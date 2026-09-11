#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
target_dir="${1:?Usage: prepare-main-overlay.sh TARGET_PROJECT_ROOT}"

python3 - "$script_dir" "$target_dir" <<'PY'
import base64
import gzip
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import sys
import zipfile

scripts = Path(sys.argv[1]).resolve(strict=True)
root = Path(sys.argv[2]).resolve(strict=True)
if not root.is_dir() or root == Path(root.anchor) or not (root / "app/build.gradle.kts").is_file():
    raise ValueError("Target must be an existing Plex Play Android project directory")

def safe_path(relative):
    if not isinstance(relative, str):
        raise ValueError("Overlay path must be a string")
    path = PurePosixPath(relative)
    if path.is_absolute() or not path.parts or any(part in (".", "..") for part in path.parts):
        raise ValueError(f"Unsafe overlay path: {relative!r}")
    if "\\" in relative or ":" in relative or str(path) != relative:
        raise ValueError(f"Non-portable overlay path: {relative!r}")
    target = root
    for part in path.parts:
        target = target / part
        if target.is_symlink():
            raise ValueError(f"Overlay cannot traverse a symlink: {relative!r}")
    resolved = target.resolve()
    if resolved == root or not resolved.is_relative_to(root):
        raise ValueError(f"Overlay path escapes project: {relative!r}")
    return resolved

compressed = base64.b64decode(
    b"".join((scripts / "main-v3.19.25-overlay.json.gz.b64").read_bytes().split()), validate=True
)
if hashlib.sha256(compressed).hexdigest() != "5edf3a170e0ab0aa4b3fab7e0031f482da34160f1d7427e58615327d026c857e":
    raise ValueError("Main source overlay checksum mismatch")
overlay = json.loads(gzip.decompress(compressed).decode("utf-8"))
if overlay.get("format") != "plex-main-v3.19.25-overlay-v3" or overlay.get("fileCount") != 22:
    raise ValueError("Unexpected main source overlay format/count")
files = overlay.get("files")
if not isinstance(files, list) or len(files) != 22:
    raise ValueError("Main source overlay must contain exactly 22 files")
seen = set()
writes = []
for entry in files:
    if not isinstance(entry, dict) or set(entry) != {"path", "text", "sha256"}:
        raise ValueError("Invalid source overlay file record")
    relative = entry["path"]
    if relative in seen:
        raise ValueError(f"Duplicate source overlay path: {relative}")
    seen.add(relative)
    target = safe_path(relative)
    data = entry["text"].encode("utf-8")
    if hashlib.sha256(data).hexdigest() != entry["sha256"]:
        raise ValueError(f"Main source file checksum mismatch: {relative}")
    writes.append((target, data))

remove_files = (
    "app/src/main/java/io/mirr/plexplay/ui/UniversalPlayerHost.kt",
    "app/src/main/java/io/mirr/plexplay/ui/UniversalSubtitleBridge.kt",
    "app/src/main/java/io/mirr/plexplay/ui/VlcPlayerScreen.kt",
    "app/src/test/java/io/mirr/plexplay/ui/SubtitleParserTest.kt",
    "app/src/test/java/io/mirr/plexplay/ui/UniversalSubtitleBridgeTest.kt",
)
remove_dirs = (
    "app/libs", "app/src/main/res/drawable-xhdpi",
    "app/src/main/res/mipmap-mdpi", "app/src/main/res/mipmap-hdpi",
    "app/src/main/res/mipmap-xhdpi", "app/src/main/res/mipmap-xxhdpi",
    "app/src/main/res/mipmap-xxxhdpi",
)
file_targets = [safe_path(path) for path in remove_files]
dir_targets = [safe_path(path) for path in remove_dirs]
for target in file_targets:
    if target.exists() and not target.is_file():
        raise ValueError(f"Expected removable file: {target}")
for target in dir_targets:
    if target.exists() and not target.is_dir():
        raise ValueError(f"Expected removable directory: {target}")

asset_hashes = {
    "ic_launcher_cyber.png": "5b81034a4951a561ba3c11201b7972dd6e4b18c9c76ca0f7f46bd7758c280d48",
    "tv_banner.png": "f8bede13fd2884d62279a983d4d583624befcd61818d9757a0b640c7bd035fc6",
}
with zipfile.ZipFile(scripts / "main-launcher-assets.zip") as archive:
    if len(archive.namelist()) != 2 or set(archive.namelist()) != set(asset_hashes):
        raise ValueError("Launcher archive must contain exactly the two expected PNG assets")
    for name, expected in asset_hashes.items():
        data = archive.read(name)
        if hashlib.sha256(data).hexdigest() != expected:
            raise ValueError(f"Launcher asset checksum mismatch: {name}")
        writes.append((safe_path("app/src/main/res/drawable-nodpi/" + name), data))

# All source/archive/target checks above finish before changing the target.
for target in file_targets:
    target.unlink(missing_ok=True)
for target in dir_targets:
    if target.exists():
        shutil.rmtree(target)
for target, data in writes:
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.is_file() or target.read_bytes() != data:
        target.write_bytes(data)
print(f"Prepared verified main overlay: {len(files)} source files and 2 original launcher PNGs → {root}")
PY
