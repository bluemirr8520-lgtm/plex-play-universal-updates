#!/usr/bin/env bash
set -euo pipefail

target="${1:?usage: prepare-main-overlay.sh TARGET_DIR}"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
archive="$repo_root/scripts/main-v3.19.25-overlay.json.gz.b64"

python3 - "$archive" "$target" <<'PY'
import base64
import gzip
import hashlib
import json
from pathlib import Path
import sys

archive_path = Path(sys.argv[1])
target = Path(sys.argv[2]).resolve()
encoded = archive_path.read_text(encoding="ascii").strip()
compressed = base64.b64decode(encoded, validate=True)
expected_archive_sha = "5edf3a170e0ab0aa4b3fab7e0031f482da34160f1d7427e58615327d026c857e"
actual_archive_sha = hashlib.sha256(compressed).hexdigest()
if actual_archive_sha != expected_archive_sha:
    raise SystemExit(
        f"overlay archive hash mismatch: {actual_archive_sha} != {expected_archive_sha}"
    )

payload = json.loads(gzip.decompress(compressed).decode("utf-8"))
if payload.get("format") != "plex-main-v3.19.25-overlay-v3":
    raise SystemExit(f"unexpected overlay format: {payload.get('format')!r}")
files = payload.get("files")
if not isinstance(files, list) or payload.get("fileCount") != len(files):
    raise SystemExit("overlay file count mismatch")

for item in files:
    relative = Path(item["path"])
    if relative.is_absolute() or ".." in relative.parts:
        raise SystemExit(f"unsafe overlay path: {relative}")
    data = item["text"].encode("utf-8")
    digest = hashlib.sha256(data).hexdigest()
    if digest != item["sha256"]:
        raise SystemExit(f"overlay file hash mismatch: {relative}")
    destination = (target / relative).resolve()
    if target != destination and target not in destination.parents:
        raise SystemExit(f"overlay path escaped target: {relative}")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(data)

print(f"restored and verified {len(files)} Plex Play main files")
PY

rm -f "$target/app/src/main/java/io/mirr/plexplay/ui/UniversalPlayerHost.kt"
rm -f "$target/app/src/main/java/io/mirr/plexplay/ui/UniversalSubtitleBridge.kt"
rm -f "$target/app/src/main/java/io/mirr/plexplay/ui/VlcPlayerScreen.kt"
rm -f "$target/app/src/test/java/io/mirr/plexplay/ui/SubtitleParserTest.kt"
rm -f "$target/app/src/test/java/io/mirr/plexplay/ui/UniversalSubtitleBridgeTest.kt"
rm -rf "$target/app/libs"
rm -rf "$target/app/src/main/res/drawable-xhdpi"
rm -rf "$target/app/src/main/res/mipmap-mdpi"
rm -rf "$target/app/src/main/res/mipmap-hdpi"
rm -rf "$target/app/src/main/res/mipmap-xhdpi"
rm -rf "$target/app/src/main/res/mipmap-xxhdpi"
rm -rf "$target/app/src/main/res/mipmap-xxxhdpi"

download_dir="${RUNNER_TEMP:-/tmp}/plex-main-v3.19.25-assets"
rm -rf "$download_dir"
mkdir -p "$download_dir" "$target/app/src/main/res/drawable-nodpi"
curl -fL --retry 3 --retry-all-errors   -o "$download_dir/PlexPlay.apk"   "https://github.com/bluemirr8520-lgtm/plex-play-updates/releases/download/v3.19.25/PlexPlay.apk"
curl -fL --retry 3 --retry-all-errors   -o "$download_dir/PlexPlay.apk.sha256"   "https://github.com/bluemirr8520-lgtm/plex-play-updates/releases/download/v3.19.25/PlexPlay.apk.sha256"
(
  cd "$download_dir"
  sha256sum -c PlexPlay.apk.sha256
)
unzip -p "$download_dir/PlexPlay.apk" res/X2.png > "$target/app/src/main/res/drawable-nodpi/ic_launcher_cyber.png"
unzip -p "$download_dir/PlexPlay.apk" res/EN.png > "$target/app/src/main/res/drawable-nodpi/tv_banner.png"

test "$(sha256sum "$target/app/src/main/res/drawable-nodpi/ic_launcher_cyber.png" | awk '{print $1}')" = "5b81034a4951a561ba3c11201b7972dd6e4b18c9c76ca0f7f46bd7758c280d48"
test "$(sha256sum "$target/app/src/main/res/drawable-nodpi/tv_banner.png" | awk '{print $1}')" = "f8bede13fd2884d62279a983d4d583624befcd61818d9757a0b640c7bd035fc6"

echo "Plex Play main v3.19.25 overlay restored with verified source and launcher assets"
