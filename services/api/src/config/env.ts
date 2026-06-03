export interface ApiEnv {
  host: string;
  port: number;
  nodeEnv: string;
  enableDebugAuth: boolean;
}

export const readEnv = (): ApiEnv => ({
  host: process.env.HOST ?? '127.0.0.1',
  port: Number(process.env.PORT ?? 4000),
  nodeEnv: process.env.NODE_ENV ?? 'development',
  enableDebugAuth: (process.env.NODE_ENV ?? 'development') !== 'production'
    && process.env.ENABLE_DEBUG_AUTH !== 'false'
});
