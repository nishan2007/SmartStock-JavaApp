#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
WORKER_DIR="$ROOT_DIR/cloudflare/smartstock-update-download"
R2_BUCKET="smartstock-updates"
R2_BUCKET_REFERENCE="r2:$R2_BUCKET"

usage() {
  echo "Usage: $0 <artifact.zip> <version> <build-number> <mac|windows|linux> [release-notes-file]" >&2
}

if [[ $# -lt 4 || $# -gt 5 ]]; then
  usage
  exit 2
fi

ARTIFACT_PATH="$1"
VERSION="$2"
BUILD_NUMBER="$3"
PLATFORM="$4"
NOTES_FILE="${5:-}"

if [[ ! -f "$ARTIFACT_PATH" ]]; then
  echo "Artifact not found: $ARTIFACT_PATH" >&2
  exit 1
fi
ARTIFACT_PATH="$(cd "$(dirname "$ARTIFACT_PATH")" && pwd)/$(basename "$ARTIFACT_PATH")"
case "$PLATFORM" in
  mac|windows|linux) ;;
  *)
    echo "Platform must be mac, windows, or linux." >&2
    exit 1
    ;;
esac
if [[ ! "$VERSION" =~ ^[0-9]+(\.[0-9]+){1,3}([._-][A-Za-z0-9]+)?$ ]]; then
  echo "Version is invalid: $VERSION" >&2
  exit 1
fi
if [[ ! "$BUILD_NUMBER" =~ ^[0-9]+$ ]]; then
  echo "Build number must be a positive integer." >&2
  exit 1
fi
if [[ -n "$NOTES_FILE" && ! -f "$NOTES_FILE" ]]; then
  echo "Release notes file not found: $NOTES_FILE" >&2
  exit 1
fi
if [[ -n "$NOTES_FILE" ]]; then
  NOTES_FILE="$(cd "$(dirname "$NOTES_FILE")" && pwd)/$(basename "$NOTES_FILE")"
fi
if [[ -z "${SUPABASE_URL:-}" || -z "${SUPABASE_SECRET_KEY:-${SUPABASE_SERVICE_ROLE_KEY:-}}" ]]; then
  echo "Set SUPABASE_URL and SUPABASE_SECRET_KEY (or legacy SUPABASE_SERVICE_ROLE_KEY)." >&2
  exit 1
fi
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required to publish release metadata." >&2
  exit 1
fi

SUPABASE_SERVER_KEY="${SUPABASE_SECRET_KEY:-${SUPABASE_SERVICE_ROLE_KEY}}"
ARTIFACT_NAME="$(basename "$ARTIFACT_PATH")"
OBJECT_KEY="$PLATFORM/$VERSION/$ARTIFACT_NAME"
OBJECT_PATH="$R2_BUCKET/$OBJECT_KEY"
LOCAL_SHA256="$(shasum -a 256 "$ARTIFACT_PATH" | awk '{print $1}')"
LOCAL_SIZE="$(stat -f '%z' "$ARTIFACT_PATH")"
VERIFY_FILE="$(mktemp "${TMPDIR:-/tmp}/smartstock-r2-verify.XXXXXX")"
trap 'rm -f "$VERIFY_FILE"' EXIT

echo "Uploading $ARTIFACT_NAME to R2 as $OBJECT_KEY..."
(
  cd "$WORKER_DIR"
  npx wrangler r2 object put "$OBJECT_PATH" \
    --remote \
    --file "$ARTIFACT_PATH" \
    --content-type "application/zip" \
    --content-disposition "attachment; filename=\"$ARTIFACT_NAME\"" \
    --force
)

echo "Downloading the stored object for byte-for-byte verification..."
(
  cd "$WORKER_DIR"
  npx wrangler r2 object get "$OBJECT_PATH" --remote --file "$VERIFY_FILE"
)
REMOTE_SHA256="$(shasum -a 256 "$VERIFY_FILE" | awk '{print $1}')"
REMOTE_SIZE="$(stat -f '%z' "$VERIFY_FILE")"
if [[ "$REMOTE_SHA256" != "$LOCAL_SHA256" || "$REMOTE_SIZE" != "$LOCAL_SIZE" ]]; then
  echo "R2 verification failed. Supabase release metadata was not published." >&2
  echo "Expected size/SHA-256: $LOCAL_SIZE $LOCAL_SHA256" >&2
  echo "Stored size/SHA-256:   $REMOTE_SIZE $REMOTE_SHA256" >&2
  exit 1
fi

RELEASE_NOTES=""
if [[ -n "$NOTES_FILE" ]]; then
  RELEASE_NOTES="$(<"$NOTES_FILE")"
fi
PUBLISHED_AT="$(date -u '+%Y-%m-%dT%H:%M:%SZ')"
PAYLOAD="$(jq -n \
  --arg version "$VERSION" \
  --argjson build_number "$BUILD_NUMBER" \
  --arg platform "$PLATFORM" \
  --arg artifact_bucket "$R2_BUCKET_REFERENCE" \
  --arg artifact_path "$OBJECT_KEY" \
  --arg sha256 "$LOCAL_SHA256" \
  --argjson file_size_bytes "$LOCAL_SIZE" \
  --arg release_notes "$RELEASE_NOTES" \
  --arg published_at "$PUBLISHED_AT" \
  '{
    version: $version,
    build_number: $build_number,
    platform: $platform,
    artifact_bucket: $artifact_bucket,
    artifact_path: $artifact_path,
    sha256: $sha256,
    file_size_bytes: $file_size_bytes,
    release_notes: $release_notes,
    required: false,
    published: true,
    published_at: $published_at
  }')"

CURL_HEADERS=(
  -H "apikey: $SUPABASE_SERVER_KEY"
  -H "Content-Type: application/json"
  -H "Accept: application/json"
  -H "Prefer: return=representation"
)
if [[ "$SUPABASE_SERVER_KEY" != sb_secret_* ]]; then
  CURL_HEADERS+=(-H "Authorization: Bearer $SUPABASE_SERVER_KEY")
fi

echo "Publishing the verified release in Supabase..."
RESPONSE="$(curl -fsS -X POST \
  "${CURL_HEADERS[@]}" \
  --data "$PAYLOAD" \
  "${SUPABASE_URL%/}/rest/v1/app_releases")"

PUBLISHED_PATH="$(jq -r 'if type == "array" and length == 1 then .[0].artifact_path else empty end' <<<"$RESPONSE")"
PUBLISHED_SHA256="$(jq -r 'if type == "array" and length == 1 then .[0].sha256 else empty end' <<<"$RESPONSE")"
if [[ "$PUBLISHED_PATH" != "$OBJECT_KEY" || "$PUBLISHED_SHA256" != "$LOCAL_SHA256" ]]; then
  echo "Supabase did not return the expected release row." >&2
  exit 1
fi

echo "Published SmartStock $VERSION build $BUILD_NUMBER for $PLATFORM."
echo "R2 object: $OBJECT_KEY"
echo "Size: $LOCAL_SIZE bytes"
echo "SHA-256: $LOCAL_SHA256"
