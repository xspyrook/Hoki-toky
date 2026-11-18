import express from 'express';
import bcrypt from 'bcryptjs';
import crypto from 'node:crypto';
import { AccessToken } from 'livekit-server-sdk';
import { pool } from '../db.js';
import { AuthedRequest, requireAuth, signAuthToken } from '../auth.js';
import { sendWakeNotification } from '../fcm.js';

export const apiRouter = express.Router();

function generateInviteCode(): string {
  const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let out = '';
  const bytes = crypto.randomBytes(8);
  for (let i = 0; i < 8; i++) {
    out += chars[bytes[i] % chars.length];
  }
  return out;
}

async function logActivity(
  groupId: string,
  userId: string,
  eventType: string,
  channelId: string | null = null,
  detail: string | null = null
): Promise<void> {
  await pool.query(
    `INSERT INTO activity_logs (group_id, channel_id, user_id, event_type, detail)
     VALUES ($1, $2, $3, $4, $5)`,
    [groupId, channelId, userId, eventType, detail]
  );
}

// Auth
apiRouter.post('/auth/register', async (req, res) => {
  const { username, password, nickname, avatarUrl } = req.body ?? {};
  if (!username || !password || String(password).length < 4) {
    res.status(400).json({ error: 'Username and password (min 4 chars) are required' });
    return;
  }
  const cleanUser = String(username).trim().toLowerCase();
  const cleanNick = String(nickname || cleanUser).trim();
  const hash = await bcrypt.hash(String(password), 10);

  try {
    const result = await pool.query(
      `INSERT INTO users (username, password_hash, nickname, avatar_url, is_online)
       VALUES ($1, $2, $3, $4, TRUE)
       RETURNING id, username, nickname, avatar_url AS "avatarUrl", is_online AS "isOnline"`,
      [cleanUser, hash, cleanNick, avatarUrl || null]
    );
    const user = result.rows[0];
    const token = signAuthToken({ userId: user.id, username: user.username });
    res.status(201).json({ token, user });
  } catch (err: unknown) {
    const pgErr = err as { code?: string };
    if (pgErr.code === '23505') {
      res.status(409).json({ error: 'Username is already taken' });
      return;
    }
    res.status(500).json({ error: 'Registration failed' });
  }
});

apiRouter.post('/auth/login', async (req, res) => {
  const { username, password } = req.body ?? {};
  if (!username || !password) {
    res.status(400).json({ error: 'Username and password are required' });
    return;
  }
  const cleanUser = String(username).trim().toLowerCase();
  const result = await pool.query(
    `SELECT id, username, password_hash, nickname, avatar_url AS "avatarUrl"
     FROM users WHERE username = $1`,
    [cleanUser]
  );
  if (result.rowCount === 0) {
    res.status(401).json({ error: 'Invalid username or password' });
    return;
  }
  const row = result.rows[0];
  const valid = await bcrypt.compare(String(password), row.password_hash);
  if (!valid) {
    res.status(401).json({ error: 'Invalid username or password' });
    return;
  }
  await pool.query(`UPDATE users SET is_online = TRUE, last_seen_at = NOW() WHERE id = $1`, [row.id]);
  const token = signAuthToken({ userId: row.id, username: row.username });
  res.json({
    token,
    user: {
      id: row.id,
      username: row.username,
      nickname: row.nickname,
      avatarUrl: row.avatarUrl,
      isOnline: true
    }
  });
});

apiRouter.post('/auth/logout', requireAuth, async (req: AuthedRequest, res) => {
  await pool.query(`UPDATE users SET is_online = FALSE, last_seen_at = NOW() WHERE id = $1`, [
    req.user!.userId
  ]);
  res.json({ ok: true });
});

// Profile
apiRouter.get('/users/me', requireAuth, async (req: AuthedRequest, res) => {
  const r = await pool.query(
    `SELECT id, username, nickname, avatar_url AS "avatarUrl", is_online AS "isOnline"
     FROM users WHERE id = $1`,
    [req.user!.userId]
  );
  res.json(r.rows[0]);
});

apiRouter.patch('/users/me', requireAuth, async (req: AuthedRequest, res) => {
  const { nickname, avatarUrl, fcmToken } = req.body ?? {};
  const r = await pool.query(
    `UPDATE users
     SET nickname = COALESCE($2, nickname),
         avatar_url = COALESCE($3, avatar_url),
         fcm_token = COALESCE($4, fcm_token),
         last_seen_at = NOW()
     WHERE id = $1
     RETURNING id, username, nickname, avatar_url AS "avatarUrl", is_online AS "isOnline"`,
    [req.user!.userId, nickname ?? null, avatarUrl ?? null, fcmToken ?? null]
  );
  res.json(r.rows[0]);
});

// Friends
apiRouter.get('/friends', requireAuth, async (req: AuthedRequest, res) => {
  const r = await pool.query(
    `SELECT u.id, u.username, u.nickname, u.avatar_url AS "avatarUrl",
            u.is_online AS "isOnline", u.last_seen_at AS "lastSeenAt"
     FROM friendships f
     JOIN users u ON u.id = f.friend_id
     WHERE f.user_id = $1 AND f.status = 'accepted'
     ORDER BY u.is_online DESC, u.nickname ASC`,
    [req.user!.userId]
  );
  res.json(r.rows);
});

apiRouter.post('/friends/add', requireAuth, async (req: AuthedRequest, res) => {
  const targetUsername = String(req.body?.username || '').trim().toLowerCase();
  if (!targetUsername) {
    res.status(400).json({ error: 'Username is required' });
    return;
  }
  const target = await pool.query(
    `SELECT id, username, nickname, avatar_url AS "avatarUrl", is_online AS "isOnline", last_seen_at AS "lastSeenAt"
     FROM users WHERE username = $1`,
    [targetUsername]
  );
  if (target.rowCount === 0) {
    res.status(404).json({ error: 'Operator not found' });
    return;
  }
  const friend = target.rows[0];
  if (friend.id === req.user!.userId) {
    res.status(400).json({ error: 'Cannot add yourself as a friend' });
    return;
  }
  await pool.query(
    `INSERT INTO friendships (user_id, friend_id, status) VALUES ($1, $2, 'accepted'), ($2, $1, 'accepted')
     ON CONFLICT (user_id, friend_id) DO NOTHING`,
    [req.user!.userId, friend.id]
  );
  res.status(201).json(friend);
});

apiRouter.delete('/friends/:friendId', requireAuth, async (req: AuthedRequest, res) => {
  await pool.query(
    `DELETE FROM friendships WHERE (user_id = $1 AND friend_id = $2) OR (user_id = $2 AND friend_id = $1)`,
    [req.user!.userId, req.params.friendId]
  );
  res.json({ ok: true });
});

apiRouter.post('/friends/:friendId/ping', requireAuth, async (req: AuthedRequest, res) => {
  const r = await pool.query(`SELECT fcm_token, nickname FROM users WHERE id = $1`, [req.params.friendId]);
  const me = await pool.query(`SELECT nickname, username FROM users WHERE id = $1`, [req.user!.userId]);
  if (r.rowCount === 0) {
    res.status(404).json({ error: 'Friend not found' });
    return;
  }
  const token = r.rows[0].fcm_token;
  if (token) {
    await sendWakeNotification(
      [token],
      'Radio Wake Ping',
      `${me.rows[0].nickname} (@${me.rows[0].username}) is calling you on Global Walkie Talkie`,
      { type: 'DIRECT_PING', fromUserId: req.user!.userId }
    );
  }
  res.json({ ok: true });
});

// Groups
apiRouter.get('/groups', requireAuth, async (req: AuthedRequest, res) => {
  const r = await pool.query(
    `SELECT g.id, g.name, g.description, g.avatar_url AS "avatarUrl",
            g.invite_code AS "inviteCode", g.invite_revoked AS "inviteRevoked",
            gm.role AS "myRole",
            (SELECT COUNT(*)::int FROM group_members gm2 JOIN users u2 ON u2.id = gm2.user_id
             WHERE gm2.group_id = g.id AND gm2.is_banned = FALSE AND u2.is_online = TRUE) AS "onlineCount",
            (SELECT COUNT(*)::int FROM group_members gm3 WHERE gm3.group_id = g.id AND gm3.is_banned = FALSE) AS "memberCount",
            (SELECT c.id FROM channels c WHERE c.group_id = g.id ORDER BY c.is_default DESC, c.created_at ASC LIMIT 1) AS "defaultChannelId",
            (SELECT c.name FROM channels c WHERE c.group_id = g.id ORDER BY c.is_default DESC, c.created_at ASC LIMIT 1) AS "defaultChannelName"
     FROM groups g
     JOIN group_members gm ON gm.group_id = g.id
     WHERE gm.user_id = $1 AND gm.is_banned = FALSE
     ORDER BY g.created_at DESC`,
    [req.user!.userId]
  );
  res.json(r.rows);
});

apiRouter.post('/groups', requireAuth, async (req: AuthedRequest, res) => {
  const { name, description, avatarUrl } = req.body ?? {};
  if (!name || !String(name).trim()) {
    res.status(400).json({ error: 'Group name is required' });
    return;
  }
  const inviteCode = generateInviteCode();
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const gRes = await client.query(
      `INSERT INTO groups (name, description, avatar_url, invite_code, owner_id)
       VALUES ($1, $2, $3, $4, $5)
       RETURNING id, name, description, avatar_url AS "avatarUrl", invite_code AS "inviteCode", invite_revoked AS "inviteRevoked"`,
      [String(name).trim(), String(description || '').trim(), avatarUrl || null, inviteCode, req.user!.userId]
    );
    const group = gRes.rows[0];
    await client.query(
      `INSERT INTO group_members (group_id, user_id, role) VALUES ($1, $2, 'owner')`,
      [group.id, req.user!.userId]
    );
    const chRes = await client.query(
      `INSERT INTO channels (group_id, name, frequency_mhz, is_default, moderators_only, created_by)
       VALUES ($1, 'General', '446.006', TRUE, FALSE, $2)
       RETURNING id, name`,
      [group.id, req.user!.userId]
    );
    await client.query(
      `INSERT INTO activity_logs (group_id, channel_id, user_id, event_type, detail)
       VALUES ($1, $2, $3, 'group_created', $4)`,
      [group.id, chRes.rows[0].id, req.user!.userId, group.name]
    );
    await client.query('COMMIT');
    res.status(201).json({
      ...group,
      myRole: 'owner',
      onlineCount: 1,
      memberCount: 1,
      defaultChannelId: chRes.rows[0].id,
      defaultChannelName: chRes.rows[0].name
    });
  } catch (err) {
    await client.query('ROLLBACK');
    res.status(500).json({ error: 'Could not create group' });
  } finally {
    client.release();
  }
});

apiRouter.post('/groups/join', requireAuth, async (req: AuthedRequest, res) => {
  const code = String(req.body?.inviteCode || '').trim().toUpperCase();
  if (!code) {
    res.status(400).json({ error: 'Invite code is required' });
    return;
  }
  const gRes = await pool.query(`SELECT * FROM groups WHERE invite_code = $1`, [code]);
  if (gRes.rowCount === 0) {
    res.status(404).json({ error: 'Invalid invite code' });
    return;
  }
  const group = gRes.rows[0];
  if (group.invite_revoked) {
    res.status(403).json({ error: 'Invite code has been revoked by group admin' });
    return;
  }

  const existing = await pool.query(
    `SELECT role, is_banned FROM group_members WHERE group_id = $1 AND user_id = $2`,
    [group.id, req.user!.userId]
  );
  if ((existing.rowCount ?? 0) > 0 && existing.rows[0].is_banned) {
    res.status(403).json({ error: 'You are banned from this group' });
    return;
  }
  const countRes = await pool.query(
    `SELECT COUNT(*)::int AS cnt FROM group_members WHERE group_id = $1 AND is_banned = FALSE`,
    [group.id]
  );
  if ((existing.rowCount ?? 0) === 0 && countRes.rows[0].cnt >= group.max_members) {
    res.status(403).json({ error: 'Group member limit reached' });
    return;
  }

  if ((existing.rowCount ?? 0) === 0) {
    await pool.query(
      `INSERT INTO group_members (group_id, user_id, role) VALUES ($1, $2, 'member')`,
      [group.id, req.user!.userId]
    );
    await logActivity(group.id, req.user!.userId, 'member_joined');
  }

  const chRes = await pool.query(
    `SELECT id, name FROM channels WHERE group_id = $1 ORDER BY is_default DESC, created_at ASC LIMIT 1`,
    [group.id]
  );

  res.json({
    id: group.id,
    name: group.name,
    description: group.description,
    avatarUrl: group.avatar_url,
    inviteCode: group.invite_code,
    inviteRevoked: group.invite_revoked,
    myRole: existing.rows[0]?.role || 'member',
    onlineCount: 1,
    memberCount: countRes.rows[0].cnt + ((existing.rowCount ?? 0) === 0 ? 1 : 0),
    defaultChannelId: chRes.rows[0]?.id ?? null,
    defaultChannelName: chRes.rows[0]?.name ?? null
  });
});

apiRouter.get('/groups/:groupId', requireAuth, async (req: AuthedRequest, res) => {
  const { groupId } = req.params;
  const mem = await pool.query(
    `SELECT role, is_banned FROM group_members WHERE group_id = $1 AND user_id = $2`,
    [groupId, req.user!.userId]
  );
  if (mem.rowCount === 0 || mem.rows[0].is_banned) {
    res.status(403).json({ error: 'Access denied' });
    return;
  }
  const gRes = await pool.query(
    `SELECT id, name, description, avatar_url AS "avatarUrl", invite_code AS "inviteCode", invite_revoked AS "inviteRevoked"
     FROM groups WHERE id = $1`,
    [groupId]
  );
  if (gRes.rowCount === 0) {
    res.status(404).json({ error: 'Group not found' });
    return;
  }
  const chRes = await pool.query(
    `SELECT id, group_id AS "groupId", name, frequency_mhz AS "frequencyMhz",
            is_default AS "isDefault", moderators_only AS "moderatorsOnly", 0 AS "activeListeners"
     FROM channels WHERE group_id = $1 ORDER BY is_default DESC, created_at ASC`,
    [groupId]
  );
  const membersRes = await pool.query(
    `SELECT gm.user_id AS "userId", u.username, u.nickname, u.avatar_url AS "avatarUrl",
            gm.role, u.is_online AS "isOnline", gm.is_banned AS "isBanned"
     FROM group_members gm
     JOIN users u ON u.id = gm.user_id
     WHERE gm.group_id = $1 AND gm.is_banned = FALSE
     ORDER BY CASE gm.role WHEN 'owner' THEN 0 WHEN 'moderator' THEN 1 ELSE 2 END, u.nickname ASC`,
    [groupId]
  );
  res.json({
    ...gRes.rows[0],
    myRole: mem.rows[0].role,
    channels: chRes.rows,
    members: membersRes.rows
  });
});

apiRouter.post('/groups/:groupId/invite/rotate', requireAuth, async (req: AuthedRequest, res) => {
  const { groupId } = req.params;
  const mem = await pool.query(
    `SELECT role FROM group_members WHERE group_id = $1 AND user_id = $2`,
    [groupId, req.user!.userId]
  );
  if (mem.rowCount === 0 || !['owner', 'moderator'].includes(mem.rows[0].role)) {
    res.status(403).json({ error: 'Only group admins or moderators can rotate invite codes' });
    return;
  }
  const nextCode = generateInviteCode();
  await pool.query(
    `UPDATE groups SET invite_code = $2, invite_revoked = FALSE WHERE id = $1`,
    [groupId, nextCode]
  );
  await logActivity(groupId, req.user!.userId, 'invite_rotated');
  res.json({ inviteCode: nextCode, inviteRevoked: false });
});

apiRouter.post('/groups/:groupId/invite/revoke', requireAuth, async (req: AuthedRequest, res) => {
  const { groupId } = req.params;
  const mem = await pool.query(
    `SELECT role FROM group_members WHERE group_id = $1 AND user_id = $2`,
    [groupId, req.user!.userId]
  );
  if (mem.rowCount === 0 || !['owner', 'moderator'].includes(mem.rows[0].role)) {
    res.status(403).json({ error: 'Only group admins or moderators can revoke invite codes' });
    return;
  }
  const r = await pool.query(
    `UPDATE groups SET invite_revoked = TRUE WHERE id = $1 RETURNING invite_code AS "inviteCode"`,
    [groupId]
  );
  await logActivity(groupId, req.user!.userId, 'invite_revoked');
  res.json({ inviteCode: r.rows[0].inviteCode, inviteRevoked: true });
});

apiRouter.post('/groups/:groupId/members/:userId/role', requireAuth, async (req: AuthedRequest, res) => {
  const { groupId, userId } = req.params;
  const role = String(req.body?.role || '');
  if (!['moderator', 'member'].includes(role)) {
    res.status(400).json({ error: 'Invalid role' });
    return;
  }
  const actor = await pool.query(
    `SELECT role FROM group_members WHERE group_id = $1 AND user_id = $2`,
    [groupId, req.user!.userId]
  );
  if (actor.rowCount === 0 || actor.rows[0].role !== 'owner') {
    res.status(403).json({ error: 'Only group owner can change roles' });
    return;
  }
  await pool.query(`UPDATE group_members SET role = $3 WHERE group_id = $1 AND user_id = $2`, [
    groupId,
    userId,
    role
  ]);
  await logActivity(groupId, req.user!.userId, 'role_changed', null, role);
  res.json({ ok: true });
});

apiRouter.post('/groups/:groupId/members/:userId/kick', requireAuth, async (req: AuthedRequest, res) => {
  const { groupId, userId } = req.params;
  const actor = await pool.query(
    `SELECT role FROM group_members WHERE group_id = $1 AND user_id = $2`,
    [groupId, req.user!.userId]
  );
  if (actor.rowCount === 0 || !['owner', 'moderator'].includes(actor.rows[0].role)) {
    res.status(403).json({ error: 'Insufficient permissions' });
    return;
  }
  await pool.query(
    `DELETE FROM group_members WHERE group_id = $1 AND user_id = $2 AND role <> 'owner'`,
    [groupId, userId]
  );
  await logActivity(groupId, req.user!.userId, 'member_kicked');
  res.json({ ok: true });
});

apiRouter.post('/groups/:groupId/members/:userId/ban', requireAuth, async (req: AuthedRequest, res) => {
  const { groupId, userId } = req.params;
  const actor = await pool.query(
    `SELECT role FROM group_members WHERE group_id = $1 AND user_id = $2`,
    [groupId, req.user!.userId]
  );
  if (actor.rowCount === 0 || !['owner', 'moderator'].includes(actor.rows[0].role)) {
    res.status(403).json({ error: 'Insufficient permissions' });
    return;
  }
  await pool.query(
    `UPDATE group_members SET is_banned = TRUE WHERE group_id = $1 AND user_id = $2 AND role <> 'owner'`,
    [groupId, userId]
  );
  await logActivity(groupId, req.user!.userId, 'member_banned');
  res.json({ ok: true });
});

// Channels
apiRouter.post('/groups/:groupId/channels', requireAuth, async (req: AuthedRequest, res) => {
  const { groupId } = req.params;
  const { name, frequencyMhz, moderatorsOnly, isDefault } = req.body ?? {};
  const actor = await pool.query(
    `SELECT role FROM group_members WHERE group_id = $1 AND user_id = $2`,
    [groupId, req.user!.userId]
  );
  if (actor.rowCount === 0 || !['owner', 'moderator'].includes(actor.rows[0].role)) {
    res.status(403).json({ error: 'Only owner or moderators can create channels' });
    return;
  }
  if (!name || !String(name).trim()) {
    res.status(400).json({ error: 'Channel name required' });
    return;
  }
  if (isDefault) {
    await pool.query(`UPDATE channels SET is_default = FALSE WHERE group_id = $1`, [groupId]);
  }
  const r = await pool.query(
    `INSERT INTO channels (group_id, name, frequency_mhz, is_default, moderators_only, created_by)
     VALUES ($1, $2, $3, $4, $5, $6)
     RETURNING id, group_id AS "groupId", name, frequency_mhz AS "frequencyMhz",
               is_default AS "isDefault", moderators_only AS "moderatorsOnly", 0 AS "activeListeners"`,
    [groupId, String(name).trim(), String(frequencyMhz || '446.012'), Boolean(isDefault), Boolean(moderatorsOnly), req.user!.userId]
  );
  res.status(201).json(r.rows[0]);
});

apiRouter.get('/groups/:groupId/activity', requireAuth, async (req: AuthedRequest, res) => {
  const { groupId } = req.params;
  const mem = await pool.query(
    `SELECT role, is_banned FROM group_members WHERE group_id = $1 AND user_id = $2`,
    [groupId, req.user!.userId]
  );
  if (mem.rowCount === 0 || mem.rows[0].is_banned) {
    res.status(403).json({ error: 'Access denied' });
    return;
  }
  const r = await pool.query(
    `SELECT a.id, a.group_id AS "groupId", a.channel_id AS "channelId", c.name AS "channelName",
            u.username, u.nickname, a.event_type AS "eventType", a.detail, a.created_at AS "createdAt"
     FROM activity_logs a
     JOIN users u ON u.id = a.user_id
     LEFT JOIN channels c ON c.id = a.channel_id
     WHERE a.group_id = $1
     ORDER BY a.created_at DESC
     LIMIT 60`,
    [groupId]
  );
  res.json(r.rows);
});

// LiveKit Secure Room Access Token
apiRouter.post('/channels/:channelId/token', requireAuth, async (req: AuthedRequest, res) => {
  const { channelId } = req.params;
  const chRes = await pool.query(
    `SELECT c.*, g.name AS group_name
     FROM channels c
     JOIN groups g ON g.id = c.group_id
     WHERE c.id = $1`,
    [channelId]
  );
  if (chRes.rowCount === 0) {
    res.status(404).json({ error: 'Channel not found' });
    return;
  }
  const channel = chRes.rows[0];
  const memRes = await pool.query(
    `SELECT gm.role, gm.is_banned, u.username, u.nickname
     FROM group_members gm
     JOIN users u ON u.id = gm.user_id
     WHERE gm.group_id = $1 AND gm.user_id = $2`,
    [channel.group_id, req.user!.userId]
  );
  if (memRes.rowCount === 0 || memRes.rows[0].is_banned) {
    res.status(403).json({ error: 'Unauthorized: not an active member of this group' });
    return;
  }
  const member = memRes.rows[0];
  const canPublish = !channel.moderators_only || ['owner', 'moderator'].includes(member.role);

  const apiKey = process.env.LIVEKIT_API_KEY;
  const apiSecret = process.env.LIVEKIT_API_SECRET;
  const livekitUrl = process.env.LIVEKIT_PUBLIC_URL || 'ws://10.0.2.2:7880';
  if (!apiKey || !apiSecret) {
    res.status(503).json({ error: 'LiveKit credentials are not configured on server' });
    return;
  }

  const roomName = `gw_${channel.group_id}_${channel.id}`;
  const at = new AccessToken(apiKey, apiSecret, {
    identity: req.user!.userId,
    name: member.nickname,
    ttl: '4h'
  });
  at.addGrant({
    roomJoin: true,
    room: roomName,
    canPublish,
    canSubscribe: true
  });

  const jwtToken = await at.toJwt();
  res.json({
    livekitUrl,
    roomName,
    token: jwtToken,
    canPublish,
    channelName: channel.name,
    groupName: channel.group_name
  });
});

// Wake offline/dozed group members via FCM
apiRouter.post('/channels/:channelId/ping-wake', requireAuth, async (req: AuthedRequest, res) => {
  const { channelId } = req.params;
  const chRes = await pool.query(
    `SELECT c.id, c.name, c.group_id, g.name AS group_name
     FROM channels c JOIN groups g ON g.id = c.group_id WHERE c.id = $1`,
    [channelId]
  );
  if (chRes.rowCount === 0) {
    res.status(404).json({ error: 'Channel not found' });
    return;
  }
  const channel = chRes.rows[0];
  const mem = await pool.query(
    `SELECT role, is_banned FROM group_members WHERE group_id = $1 AND user_id = $2`,
    [channel.group_id, req.user!.userId]
  );
  if (mem.rowCount === 0 || mem.rows[0].is_banned) {
    res.status(403).json({ error: 'Access denied' });
    return;
  }
  const tokensRes = await pool.query(
    `SELECT u.fcm_token
     FROM group_members gm
     JOIN users u ON u.id = gm.user_id
     WHERE gm.group_id = $1 AND gm.is_banned = FALSE AND gm.user_id <> $2 AND u.fcm_token IS NOT NULL`,
    [channel.group_id, req.user!.userId]
  );
  const tokens = tokensRes.rows.map((r) => r.fcm_token).filter(Boolean);
  const sent = await sendWakeNotification(
    tokens,
    `${channel.group_name} • #${channel.name}`,
    `${req.user!.username} is hailing on the radio channel`,
    {
      type: 'CHANNEL_WAKE',
      groupId: channel.group_id,
      channelId: channel.id
    }
  );
  res.json({ ok: true, message: `Dispatched ${sent} wake alert(s)` });
});
