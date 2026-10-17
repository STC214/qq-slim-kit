"""Restore local pre-v1.6 files only; never touches device data or the Git index."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

root = Path(__file__).resolve().parent.parent
p = argparse.ArgumentParser()
p.add_argument('--target', type=Path, default=root, help='Workspace root (or an isolated rollback test copy).')
args = p.parse_args()
target = args.target.resolve()
metadata = json.loads((root / '.local/rollback-v1.6.json').read_text(encoding='utf-8'))
backup = root / '.local/review-v16-baseline'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None


paths = []
for record in metadata:
    relative = Path(record['path'])
    if relative.is_absolute() or '..' in relative.parts:
        raise SystemExit('Invalid rollback path')
    destination = (target / relative).resolve()
    if not destination.is_relative_to(target):
        raise SystemExit('Rollback target escaped workspace')
    if digest(destination) != record['modified']:
        raise SystemExit(f'File changed after review; rollback stopped before modifications: {relative}')
    original = backup / record['backup'] if record['backup'] else None
    if original and digest(original) != record['original']:
        raise SystemExit(f'Backup checksum mismatch: {relative}')
    paths.append((record, destination, original))

for record, destination, original in paths:
    if original:
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(original, destination)
    elif destination.is_file():
        destination.unlink()
    if digest(destination) != record['original']:
        raise SystemExit(f'Rollback verification failed: {record["path"]}')
print(f'rollback=OK; restored_files={len(paths)}; baseline=v1.5; device_and_git_index=untouched')
