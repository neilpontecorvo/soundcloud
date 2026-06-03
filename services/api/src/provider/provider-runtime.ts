import { ProviderCredentialsService } from './credentials-service.js';
import { ProviderOAuthService } from './oauth-service.js';
import { readProviderConfig } from './provider-config.js';
import { FileProviderTokenStore } from './token-store.js';
import { restoreDeviceSession } from '../session/session-store.js';
import { readEnv } from '../config/env.js';

export const apiEnv = readEnv();
export const providerConfig = readProviderConfig();
export const providerTokenStore = new FileProviderTokenStore(providerConfig.tokenStorePath);
export const providerOAuthService = new ProviderOAuthService(providerConfig);
export const providerCredentialsService = new ProviderCredentialsService(
  providerOAuthService,
  providerTokenStore,
  apiEnv.enableDebugAuth
);

for (const record of providerTokenStore.list()) {
  if (record.tokens.source === 'local_debug' && !apiEnv.enableDebugAuth) {
    continue;
  }
  restoreDeviceSession(providerCredentialsService.restoreStoredSession(record));
}
