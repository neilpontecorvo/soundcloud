import { ProviderCredentialsService } from './credentials-service.js';
import { ProviderOAuthService } from './oauth-service.js';
import { readProviderConfig } from './provider-config.js';
import { FileProviderTokenStore } from './token-store.js';
import { restoreDeviceSession } from '../session/session-store.js';

export const providerConfig = readProviderConfig();
export const providerTokenStore = new FileProviderTokenStore(providerConfig.tokenStorePath);
export const providerOAuthService = new ProviderOAuthService(providerConfig);
export const providerCredentialsService = new ProviderCredentialsService(
  providerOAuthService,
  providerTokenStore
);

for (const record of providerTokenStore.list()) {
  restoreDeviceSession(record.session);
}
