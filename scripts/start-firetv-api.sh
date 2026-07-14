#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
API_ROOT="$REPO_ROOT/services/api"

ENV_FILE="${FIRETV_ENV_FILE:-$REPO_ROOT/.env.firetv.local}"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

FIRETV_DEVICE_HOST="${FIRETV_DEVICE_HOST:-192.168.1.168}"
PORT="${PORT:-4000}"
HOST="${HOST:-0.0.0.0}"
ENABLE_DEBUG_AUTH="${ENABLE_DEBUG_AUTH:-false}"

detect_backend_host() {
  if [[ -n "${FIRETV_BACKEND_HOST:-}" ]]; then
    printf '%s\n' "$FIRETV_BACKEND_HOST"
    return
  fi

  local iface
  iface="$(route -n get "$FIRETV_DEVICE_HOST" 2>/dev/null | awk '/interface:/{print $2; exit}')"
  if [[ -n "$iface" ]]; then
    local ip
    ip="$(ipconfig getifaddr "$iface" 2>/dev/null || true)"
    if [[ -n "$ip" ]]; then
      printf '%s\n' "$ip"
      return
    fi
  fi

  printf '%s\n' "192.168.1.167"
}

BACKEND_HOST="$(detect_backend_host)"
FIRETV_BACKEND_URL="${FIRETV_BACKEND_URL:-http://$BACKEND_HOST:$PORT}"
PROVIDER_AUTH_PUBLIC_BASE_URL="${PROVIDER_AUTH_PUBLIC_BASE_URL:-$FIRETV_BACKEND_URL}"
PROVIDER_REDIRECT_URI="${PROVIDER_REDIRECT_URI:-${PROVIDER_AUTH_PUBLIC_BASE_URL%/}/v1/auth/callback}"
SESSION_STORE_PATH="${SESSION_STORE_PATH:-$API_ROOT/data/sessions.json}"
PROVIDER_TOKEN_STORE_PATH="${PROVIDER_TOKEN_STORE_PATH:-$API_ROOT/.local/provider-token-store.json}"

missing=()
placeholder=()
for name in PROVIDER_CLIENT_ID PROVIDER_CLIENT_SECRET PROVIDER_AUTH_PUBLIC_BASE_URL PROVIDER_REDIRECT_URI; do
  value="${!name:-}"
  if [[ -z "$value" ]]; then
    missing+=("$name")
  elif [[ "$value" == replace-with-* || "$value" == "<real>" ]]; then
    placeholder+=("$name")
  fi
done

if (( ${#missing[@]} > 0 )); then
  printf 'Missing required provider env: %s\n' "${missing[*]}" >&2
  printf 'Set them in your shell or in %s, then rerun this script.\n' "$ENV_FILE" >&2
  exit 1
fi

if (( ${#placeholder[@]} > 0 )); then
  printf 'Provider env still contains template placeholder values: %s\n' "${placeholder[*]}" >&2
  printf 'Replace them in %s or export real values, then rerun this script.\n' "$ENV_FILE" >&2
  exit 1
fi

if curl -fsS "http://127.0.0.1:$PORT/health" >/dev/null 2>&1; then
  printf 'SoundCloud API is already running on port %s.\n' "$PORT" >&2
  printf 'Stop that process, then rerun this launcher so the provider env is loaded into the backend.\n' >&2
  printf 'Fire TV backend URL would be: %s\n' "$FIRETV_BACKEND_URL" >&2
  exit 1
fi

export HOST
export PORT
export ENABLE_DEBUG_AUTH
export FIRETV_DEVICE_HOST
export FIRETV_BACKEND_URL
export PROVIDER_CLIENT_ID
export PROVIDER_CLIENT_SECRET
export PROVIDER_AUTH_PUBLIC_BASE_URL
export PROVIDER_REDIRECT_URI
export SESSION_STORE_PATH
export PROVIDER_TOKEN_STORE_PATH

printf 'Starting SoundCloud Fire TV API\n'
printf 'Repo: %s\n' "$REPO_ROOT"
printf 'Bind: %s:%s\n' "$HOST" "$PORT"
printf 'Fire TV backend URL: %s\n' "$FIRETV_BACKEND_URL"
printf 'Session store: %s\n' "$SESSION_STORE_PATH"
printf 'Provider token store: %s\n' "$PROVIDER_TOKEN_STORE_PATH"

cd "$REPO_ROOT"
npm --workspace @soundcloud-private/api run build
exec npm --workspace @soundcloud-private/api start
