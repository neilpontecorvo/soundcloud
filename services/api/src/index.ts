import cors from 'cors';
import express from 'express';
import { readEnv } from './config/env.js';
import { errorHandler } from './middleware/error-handler.js';
import { authRouter } from './routes/auth.js';
import { contentRouter } from './routes/content.js';
import { healthRouter } from './routes/health.js';

const env = readEnv();
const app = express();

app.use(cors());
app.use(express.json());

app.use(healthRouter);
app.use('/v1', authRouter);
app.use('/v1', contentRouter);

app.use(errorHandler);

app.listen(env.port, () => {
  console.log(`[api] listening on ${env.port} (${env.nodeEnv})`);
});
