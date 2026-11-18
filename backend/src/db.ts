import { Pool } from 'pg';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const pool = new Pool({
  connectionString: process.env.DATABASE_URL
});

export async function initDatabase(): Promise<void> {
  const __dirname = path.dirname(fileURLToPath(import.meta.url));
  const sqlPath = path.resolve(__dirname, '../sql/001_init.sql');
  const sql = fs.readFileSync(sqlPath, 'utf8');
  await pool.query(sql);
}
