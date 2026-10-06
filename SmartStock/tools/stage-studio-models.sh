#!/usr/bin/env bash
set -euo pipefail
destination="${1:?Supply the catalog-studio destination directory}"
script_dir="$(cd "$(dirname "$0")" && pwd)"
cache_dir="${TMPDIR:-/tmp}"
mkdir -p "$destination"
stage_model() {
  local name="$1" hash="$2" url="$3" cached="$cache_dir/smartstock-$1"
  if [[ ! -f "$cached" ]] || [[ "$(shasum -a 256 "$cached" | awk '{print $1}')" != "$hash" ]]; then
    curl --fail --location --retry 2 "$url" -o "$cached.partial"
    [[ "$(shasum -a 256 "$cached.partial" | awk '{print $1}')" == "$hash" ]] || { echo "Photo model integrity check failed: $name" >&2; exit 1; }
    mv "$cached.partial" "$cached"
  fi
  cp "$cached" "$destination/$name"
}
if [[ "${2:-}" != "--notices-only" ]]; then
  stage_model isnet-general-use.onnx 60920e99c45464f2ba57bee2ad08c919a52bbf852739e96947fbb4358c0d964a https://huggingface.co/skillsafe-ai/isnet-general-use/resolve/main/isnet-general-use.onnx
  stage_model birefnet-general.onnx 58f621f00f5d756097615970a88a791584600dcf7c45b18a0a6267535a1ebd3c https://github.com/danielgatis/rembg/releases/download/v0.0.0/BiRefNet-general-epoch_244.onnx
fi
cp "$script_dir/catalog-studio-model-NOTICE.txt" "$script_dir/birefnet-LICENSE.txt" "$destination/"
