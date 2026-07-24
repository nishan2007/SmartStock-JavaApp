#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 <absolute-smartstock-profile-directory>" >&2
  exit 2
fi

PROFILE_DIR="$1"
if [[ "$PROFILE_DIR" != /*/profiles/development && "$PROFILE_DIR" != /*/profiles/production ]]; then
  echo "Profile directory must be an absolute SmartStock development or production profile path." >&2
  exit 2
fi

SECRET_FILE="$(mktemp "${TMPDIR:-/tmp}/smartstock-worker-secrets.XXXXXX")"
DEPLOY_OUTPUT="$(mktemp "${TMPDIR:-/tmp}/smartstock-worker-deploy.XXXXXX")"
cleanup() {
  rm -f "$SECRET_FILE" "$DEPLOY_OUTPUT"
}
trap cleanup EXIT
chmod 600 "$SECRET_FILE" "$DEPLOY_OUTPUT"

SIGNING_SECRET="$(openssl rand -hex 32)"
jq -n --arg secret "$SIGNING_SECRET" \
  '{SMARTSTOCK_UPDATE_SIGNING_SECRET: $secret}' > "$SECRET_FILE"

npx wrangler deploy --secrets-file "$SECRET_FILE" 2>&1 | tee "$DEPLOY_OUTPUT"

WORKER_URL="$(sed $'s/\033\\[[0-9;]*m//g' "$DEPLOY_OUTPUT" \
  | grep -Eo 'https://[^[:space:]]+\.workers\.dev' | tail -1)"
if [[ -z "$WORKER_URL" ]]; then
  echo "Wrangler deployed but did not return a workers.dev URL." >&2
  exit 1
fi

mkdir -p "$PROFILE_DIR"
chmod 700 "$PROFILE_DIR"
CONFIG_FILE="$PROFILE_DIR/r2-update.properties"
umask 077
{
  echo "# SmartStock private R2 updater configuration"
  echo "SMARTSTOCK_UPDATE_R2_WORKER_URL=$WORKER_URL"
  echo "SMARTSTOCK_UPDATE_R2_SIGNING_SECRET=$SIGNING_SECRET"
} > "$CONFIG_FILE"
chmod 600 "$CONFIG_FILE"

echo "Saved owner-only SmartStock server configuration: $CONFIG_FILE"
echo "Worker URL: $WORKER_URL"
