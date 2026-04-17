export interface ApiEnv {
  port: number;
  nodeEnv: string;
}

export const readEnv = (): ApiEnv => ({
  port: Number(process.env.PORT ?? 4000),
  nodeEnv: process.env.NODE_ENV ?? 'development'
});
