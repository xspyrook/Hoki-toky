import 'dotenv/config';
import express from 'express';
import http from 'node:http';
import cors from 'cors';
import helmet from 'helmet';
import { initDatabase, pool } from './db.js';
import { initFcm } from './fcm.js';
import { apiRouter } from './routes/api.js';
import { attachSignalingServer } from './ws/signaling.js';

const PORT = Number(process.env.PORT || 8080);

async function bootstrap() {
  await initDatabase();
  initFcm();

  const app = express();
  app.use(helmet());
  app.use(cors());
  app.use(express.json({ limit: '2mb' }));

  app.get('/health', async (_req, res) => {
    try {
      await pool.query('SELECT 1');
      res.json({
        status: 'ok',
        postgres: 'connected',
        livekitConfigured: Boolean(process.env.LIVEKIT_API_KEY && process.env.LIVEKIT_API_SECRET),
        version: '1.0.0'
      });
    } catch {
      res.status(500).json({
        status: 'error',
        postgres: 'disconnected',
        livekitConfigured: false,
        version: '1.0.0'
      });
    }
  });

  app.use('/api/v1', apiRouter);

  const server = http.createServer(app);
  attachSignalingServer(server);

  server.listen(PORT, () => {
    console.log(`Global Walkie Talkie backend listening on :${PORT}`);
  });
}

bootstrap().catch((err) => {
  console.error('Fatal startup error:', err);
  process.exit(1);
});
