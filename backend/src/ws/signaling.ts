import type { Server as HttpServer } from 'node:http';
import { WebSocketServer, WebSocket } from 'ws';
import { verifyAuthToken } from '../auth.js';
import { pool } from '../db.js';

interface ClientSocket {
  ws: WebSocket;
  userId: string;
  username: string;
  nickname: string;
  avatarUrl: string | null;
  channelId: string | null;
  groupId: string | null;
  role: string;
}

interface FloorHolder {
  userId: string;
  speakerName: string;
  timeout: NodeJS.Timeout;
}

const MAX_TX_DURATION_MS = 90_000;

export function attachSignalingServer(server: HttpServer): void {
  const wss = new WebSocketServer({ server, path: '/ws' });
  const clients = new Set<ClientSocket>();
  const channelMembers = new Map<string, Set<ClientSocket>>();
  const channelFloors = new Map<string, FloorHolder>();
  const lastSpeakers = new Map<string, string>();

  function broadcastToChannel(channelId: string, payload: unknown): void {
    const members = channelMembers.get(channelId);
    if (!members) return;
    const raw = JSON.stringify(payload);
    for (const c of members) {
      if (c.ws.readyState === WebSocket.OPEN) {
        c.ws.send(raw);
      }
    }
  }

  function serializeMembers(channelId: string) {
    const members = channelMembers.get(channelId);
    if (!members) return [];
    return Array.from(members).map((c) => ({
      userId: c.userId,
      username: c.username,
      nickname: c.nickname,
      avatarUrl: c.avatarUrl,
      role: c.role,
      isOnline: true,
      isBanned: false
    }));
  }

  async function releaseFloor(channelId: string, userId?: string): Promise<void> {
    const floor = channelFloors.get(channelId);
    if (!floor) return;
    if (userId && floor.userId !== userId) return;

    clearTimeout(floor.timeout);
    channelFloors.delete(channelId);
    lastSpeakers.set(channelId, floor.speakerName);

    broadcastToChannel(channelId, {
      type: 'floor:released',
      channelId,
      lastSpeakerName: floor.speakerName
    });
  }

  async function leaveCurrentChannel(client: ClientSocket): Promise<void> {
    const chId = client.channelId;
    if (!chId) return;

    await releaseFloor(chId, client.userId);

    const members = channelMembers.get(chId);
    if (members) {
      members.delete(client);
      if (members.size === 0) {
        channelMembers.delete(chId);
      } else {
        broadcastToChannel(chId, {
          type: 'channel:presence',
          channelId: chId,
          listenersCount: members.size,
          members: serializeMembers(chId)
        });
      }
    }

    if (client.groupId) {
      await pool.query(
        `INSERT INTO activity_logs (group_id, channel_id, user_id, event_type) VALUES ($1, $2, $3, 'channel_leave')`,
        [client.groupId, chId, client.userId]
      ).catch(() => {});
    }
    client.channelId = null;
    client.groupId = null;
  }

  wss.on('connection', async (ws, req) => {
    const reqUrl = new URL(req.url || '/', 'http://localhost');
    const token = reqUrl.searchParams.get('token');
    if (!token) {
      ws.close(4001, 'Missing token');
      return;
    }

    let userRow: { id: string; username: string; nickname: string; avatar_url: string | null };
    try {
      const payload = verifyAuthToken(token);
      const res = await pool.query(
        `SELECT id, username, nickname, avatar_url FROM users WHERE id = $1`,
        [payload.userId]
      );
      if (res.rowCount === 0) {
        ws.close(4001, 'Unknown user');
        return;
      }
      userRow = res.rows[0];
    } catch {
      ws.close(4001, 'Invalid token');
      return;
    }

    const client: ClientSocket = {
      ws,
      userId: userRow.id,
      username: userRow.username,
      nickname: userRow.nickname,
      avatarUrl: userRow.avatar_url,
      channelId: null,
      groupId: null,
      role: 'member'
    };
    clients.add(client);

    await pool.query(`UPDATE users SET is_online = TRUE, last_seen_at = NOW() WHERE id = $1`, [client.userId]);
    ws.send(JSON.stringify({ type: 'authenticated', userId: client.userId }));

    ws.on('message', async (raw) => {
      let msg: Record<string, unknown>;
      try {
        msg = JSON.parse(raw.toString());
      } catch {
        return;
      }

      switch (msg.type) {
        case 'ping': {
          ws.send(JSON.stringify({ type: 'pong', ts: msg.ts }));
          break;
        }
        case 'channel:join': {
          const channelId = String(msg.channelId || '');
          if (!channelId) return;

          const chRes = await pool.query(
            `SELECT c.id, c.group_id, c.moderators_only, gm.role, gm.is_banned
             FROM channels c
             JOIN group_members gm ON gm.group_id = c.group_id AND gm.user_id = $2
             WHERE c.id = $1`,
            [channelId, client.userId]
          );
          if (chRes.rowCount === 0 || chRes.rows[0].is_banned) {
            ws.send(JSON.stringify({ type: 'error', message: 'Unauthorized for this channel' }));
            return;
          }

          await leaveCurrentChannel(client);
          client.channelId = channelId;
          client.groupId = chRes.rows[0].group_id;
          client.role = chRes.rows[0].role;

          let set = channelMembers.get(channelId);
          if (!set) {
            set = new Set();
            channelMembers.set(channelId, set);
          }
          set.add(client);

          await pool.query(
            `INSERT INTO activity_logs (group_id, channel_id, user_id, event_type) VALUES ($1, $2, $3, 'channel_join')`,
            [client.groupId, channelId, client.userId]
          ).catch(() => {});

          const floor = channelFloors.get(channelId);
          const membersPayload = serializeMembers(channelId);

          ws.send(
            JSON.stringify({
              type: 'channel:joined',
              channelId,
              listenersCount: set.size,
              currentSpeakerId: floor?.userId || null,
              currentSpeakerName: floor?.speakerName || null,
              lastSpeakerName: lastSpeakers.get(channelId) || null,
              members: membersPayload
            })
          );

          broadcastToChannel(channelId, {
            type: 'channel:presence',
            channelId,
            listenersCount: set.size,
            members: membersPayload
          });
          break;
        }
        case 'channel:leave': {
          await leaveCurrentChannel(client);
          break;
        }
        case 'ptt:request': {
          const channelId = String(msg.channelId || '');
          if (!channelId || client.channelId !== channelId) return;

          const chRes = await pool.query(
            `SELECT moderators_only FROM channels WHERE id = $1`,
            [channelId]
          );
          const modOnly = chRes.rows[0]?.moderators_only ?? false;
          if (modOnly && !['owner', 'moderator'].includes(client.role)) {
            ws.send(
              JSON.stringify({
                type: 'floor:denied',
                channelId,
                reason: 'Moderators only channel'
              })
            );
            return;
          }

          const currentFloor = channelFloors.get(channelId);
          if (currentFloor && currentFloor.userId !== client.userId) {
            ws.send(
              JSON.stringify({
                type: 'floor:busy',
                channelId,
                holderId: currentFloor.userId,
                holderName: currentFloor.speakerName
              })
            );
            return;
          }

          if (!currentFloor) {
            const timeout = setTimeout(() => {
              void releaseFloor(channelId, client.userId);
            }, MAX_TX_DURATION_MS);

            channelFloors.set(channelId, {
              userId: client.userId,
              speakerName: client.nickname,
              timeout
            });

            if (client.groupId) {
              await pool.query(
                `INSERT INTO activity_logs (group_id, channel_id, user_id, event_type) VALUES ($1, $2, $3, 'ptt_speak')`,
                [client.groupId, channelId, client.userId]
              ).catch(() => {});
            }
          }

          broadcastToChannel(channelId, {
            type: 'floor:granted',
            channelId,
            speakerId: client.userId,
            speakerName: client.nickname
          });
          break;
        }
        case 'ptt:release': {
          const channelId = String(msg.channelId || '');
          if (channelId) {
            await releaseFloor(channelId, client.userId);
          }
          break;
        }
      }
    });

    ws.on('close', async () => {
      clients.delete(client);
      await leaveCurrentChannel(client);
      const stillOnline = Array.from(clients).some((c) => c.userId === client.userId);
      if (!stillOnline) {
        await pool.query(`UPDATE users SET is_online = FALSE, last_seen_at = NOW() WHERE id = $1`, [client.userId]).catch(() => {});
      }
    });
  });
}
