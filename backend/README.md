# Global Walkie Talkie — Backend, LiveKit & PostgreSQL Setup Guide

This directory contains the complete standalone backend stack for **Global Walkie Talkie**:
- **API & Signaling Server** (`src/`): Express REST API + WebSocket Floor Control & presence server
- **PostgreSQL 16** (`sql/001_init.sql`): Users, friendships, private groups, invite codes, role permissions, channels, and activity logs
- **LiveKit SFU** (`livekit.yaml`): Low-latency WebRTC audio rooms using Opus codec
- **Coturn TURN Server**: Relay traversal across restrictive 4G/5G carrier NATs and firewalls
- **Firebase Cloud Messaging (FCM)**: High-priority wake-up push alerts for dozed/background devices

---

## 1. Quick Start with Docker Compose (Recommended)

```bash
cd backend
cp .env.example .env
# Edit .env with strong JWT_SECRET, LIVEKIT_API_KEY, LIVEKIT_API_SECRET, and public wss:// URL
docker compose up -d --build
```

Verify the server is online:
```bash
curl http://localhost:8080/health
# Expected: {"status":"ok","postgres":"connected","livekitConfigured":true,"version":"1.0.0"}
```

---

## 2. Production TLS (HTTPS & WSS)

Android clients enforce `HTTPS` and `WSS` for all non-local domains (`res/xml/network_security_config.xml`).
Place a reverse proxy (Caddy or Nginx) in front of:
- `https://api.yourdomain.com` -> `http://127.0.0.1:8080` (includes `/ws` WebSocket upgrade)
- `wss://livekit.yourdomain.com` -> `http://127.0.0.1:7880`

Then enter `https://api.yourdomain.com` and `wss://livekit.yourdomain.com` in the Android app's **Server Endpoints** panel (Login screen or Settings).

---

## 3. Enabling Firebase Cloud Messaging (FCM)

1. Create a Firebase project in the Firebase Console and register Android package `com.aistudio.globalwalkie.r7k9m2`.
2. Download `google-services.json` and place it at `app/google-services.json` in the Android project.
3. Generate a Firebase Admin SDK private key JSON and save it to `backend/firebase-service-account.json` (or set `FIREBASE_SERVICE_ACCOUNT_JSON` in `.env`).

---

## 4. Floor Control & Security Guarantees

- **Zero Secrets in APK**: `LIVEKIT_API_SECRET`, `JWT_SECRET`, and DB credentials stay strictly on the server.
- **Short-Lived Room Tokens**: `/api/v1/channels/:channelId/token` validates membership, ban status, and `moderators_only` permissions before minting a LiveKit JWT.
- **Single-Speaker Floor Lock**: Only one user per channel holds `ptt:request` at any instant; any other user receives `floor:busy` with the active speaker's name. Disconnecting automatically frees the floor.
- **Zero-Recording Default**: Voice streams are never recorded unless explicitly consented.
