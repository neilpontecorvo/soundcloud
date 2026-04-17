import fs from 'node:fs';
import path from 'node:path';
import { DeviceSession } from '../session/session-store.js';

export interface StoredProviderTokens {
  accessToken: string;
  refreshToken?: string;
  tokenType: string;
  scope?: string;
  accessTokenExpiresAtIso?: string;
  updatedAtIso: string;
  source?: 'provider' | 'local_debug';
}

export interface StoredProviderSession {
  session: DeviceSession;
  tokens: StoredProviderTokens;
}

interface TokenStoreFile {
  version: 1;
  sessions: Record<string, StoredProviderSession>;
}

export class FileProviderTokenStore {
  private file: TokenStoreFile = { version: 1, sessions: {} };

  constructor(private readonly filePath: string) {
    this.load();
  }

  get(sessionId: string): StoredProviderSession | undefined {
    return this.file.sessions[sessionId];
  }

  list(): StoredProviderSession[] {
    return Object.values(this.file.sessions);
  }

  save(record: StoredProviderSession): void {
    this.file.sessions[record.session.sessionId] = record;
    this.flush();
  }

  delete(sessionId: string): void {
    delete this.file.sessions[sessionId];
    this.flush();
  }

  private load(): void {
    if (!fs.existsSync(this.filePath)) return;

    const raw = fs.readFileSync(this.filePath, 'utf8');
    if (!raw.trim()) return;

    const parsed = JSON.parse(raw) as TokenStoreFile;
    if (parsed.version === 1 && parsed.sessions && typeof parsed.sessions === 'object') {
      this.file = parsed;
    }
  }

  private flush(): void {
    fs.mkdirSync(path.dirname(this.filePath), { recursive: true });
    fs.writeFileSync(this.filePath, `${JSON.stringify(this.file, null, 2)}\n`, {
      encoding: 'utf8',
      mode: 0o600
    });
    fs.chmodSync(this.filePath, 0o600);
  }
}
