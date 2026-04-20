import { randomBytes, randomUUID } from 'node:crypto';

export interface ProviderAuthPairing {
  sessionId: string;
  userCode: string;
  state: string;
  createdAtIso: string;
  expiresAtIso: string;
}

const PAIRING_TTL_MS = 10 * 60 * 1000;
const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const pairingsByCode = new Map<string, ProviderAuthPairing>();
const pairingsByState = new Map<string, ProviderAuthPairing>();

export const createProviderAuthPairing = (sessionId: string): ProviderAuthPairing => {
  purgeExpiredPairings();
  clearPairingsForSession(sessionId);

  const now = new Date();
  const pairing: ProviderAuthPairing = {
    sessionId,
    userCode: uniqueUserCode(),
    state: randomUUID(),
    createdAtIso: now.toISOString(),
    expiresAtIso: new Date(now.getTime() + PAIRING_TTL_MS).toISOString()
  };

  pairingsByCode.set(normalizeUserCode(pairing.userCode), pairing);
  pairingsByState.set(pairing.state, pairing);
  return pairing;
};

export const getProviderAuthPairingByUserCode = (
  userCode: string
): ProviderAuthPairing | undefined => {
  purgeExpiredPairings();
  return pairingsByCode.get(normalizeUserCode(userCode));
};

export const consumeProviderAuthPairingByState = (
  state: string
): ProviderAuthPairing | undefined => {
  purgeExpiredPairings();
  const pairing = pairingsByState.get(state);
  if (!pairing) return undefined;
  removePairing(pairing);
  return pairing;
};

const clearPairingsForSession = (sessionId: string): void => {
  for (const pairing of pairingsByState.values()) {
    if (pairing.sessionId === sessionId) {
      removePairing(pairing);
    }
  }
};

const purgeExpiredPairings = (): void => {
  const now = Date.now();
  for (const pairing of pairingsByState.values()) {
    if (Date.parse(pairing.expiresAtIso) <= now) {
      removePairing(pairing);
    }
  }
};

const removePairing = (pairing: ProviderAuthPairing): void => {
  pairingsByState.delete(pairing.state);
  pairingsByCode.delete(normalizeUserCode(pairing.userCode));
};

const uniqueUserCode = (): string => {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const code = generateUserCode();
    if (!pairingsByCode.has(normalizeUserCode(code))) return code;
  }
  throw new Error('Unable to allocate provider pairing code.');
};

const generateUserCode = (): string => {
  const bytes = randomBytes(8);
  let code = '';
  for (const byte of bytes) {
    code += CODE_ALPHABET[byte % CODE_ALPHABET.length];
  }
  return `${code.slice(0, 4)}-${code.slice(4)}`;
};

const normalizeUserCode = (userCode: string): string => (
  userCode.replace(/[^A-Za-z0-9]/g, '').toUpperCase()
);
