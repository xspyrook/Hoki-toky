import admin from 'firebase-admin';
import fs from 'node:fs';

let fcmReady = false;

export function initFcm(): void {
  const credPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH;
  const credJson = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;

  try {
    if (credJson) {
      const parsed = JSON.parse(credJson);
      admin.initializeApp({ credential: admin.credential.cert(parsed) });
      fcmReady = true;
    } else if (credPath && fs.existsSync(credPath)) {
      const parsed = JSON.parse(fs.readFileSync(credPath, 'utf8'));
      admin.initializeApp({ credential: admin.credential.cert(parsed) });
      fcmReady = true;
    }
  } catch (err) {
    console.warn('FCM disabled (invalid or missing service account):', err);
  }
}

export async function sendWakeNotification(
  tokens: string[],
  title: string,
  body: string,
  data: Record<string, string>
): Promise<number> {
  if (!fcmReady || tokens.length === 0) return 0;

  const validTokens = tokens.filter((t) => Boolean(t && t.trim()));
  if (validTokens.length === 0) return 0;

  const response = await admin.messaging().sendEachForMulticast({
    tokens: validTokens,
    android: {
      priority: 'high'
    },
    notification: {
      title,
      body
    },
    data
  });
  return response.successCount;
}
