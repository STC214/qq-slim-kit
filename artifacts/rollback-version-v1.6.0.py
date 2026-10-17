"""Restore the local pre-versioning files; Git commits/tags/remotes are not modified."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil

root=Path(__file__).resolve().parent.parent
p=argparse.ArgumentParser()
p.add_argument('--target',type=Path,default=root)
target=p.parse_args().target.resolve()
backup=root/'.local/version-1.6.0-baseline'
records=json.loads((backup/'modified.json').read_text(encoding='utf-8'))
def digest(path):return hashlib.sha256(path.read_bytes()).hexdigest() if path.is_file() else None
for record in records:
    dest=(target/record['path']).resolve()
    if not dest.is_relative_to(target) or digest(dest)!=record['modified']:raise SystemExit('Modified target: '+record['path'])
    if record['original'] and digest(backup/record['path'])!=record['original']:raise SystemExit('Backup mismatch')
for record in records:
    dest=target/record['path']
    if record['original']:
        dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(backup/record['path'],dest)
    elif dest.is_file():dest.unlink()
    if digest(dest)!=record['original']:raise SystemExit('Restore verification failed')
print('version_rollback=OK; versionName=1.6-reviewed; versionCode=7; git_and_devices=untouched')
