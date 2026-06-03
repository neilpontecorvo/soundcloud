import cors from 'cors';
import express from 'express';
import { errorHandler } from './middleware/error-handler.js';
import { authRouter } from './routes/auth.js';
import { contentRouter } from './routes/content.js';
import { debugRouter } from './routes/debug.js';
import { healthRouter } from './routes/health.js';
import { apiEnv } from './provider/provider-runtime.js';

const env = apiEnv;
const app = express();

app.use(cors());
app.use(express.json());

app.use(healthRouter);
app.use('/v1', authRouter);
app.use('/v1', contentRouter);
app.use('/v1', debugRouter);

app.use(errorHandler);

app.listen(env.port, env.host, () => {
  console.log(`[api] listening on ${env.host}:${env.port} (${env.nodeEnv})`);
});
