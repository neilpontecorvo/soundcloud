export interface ApiEnv {
  port: number;
  nodeEnv: string;
  enableDebugAuth: boolean;
}

export const readEnv = (): ApiEnv => ({
  port: Number(process.env.PORT ?? 4000),
  nodeEnv: process.env.NODE_ENV ?? 'development',
  enableDebugAuth: (process.env.NODE_ENV ?? 'development') !== 'production'
    && process.env.ENABLE_DEBUG_AUTH !== 'false'
});
