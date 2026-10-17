"""Compile the real sources plus test-only host fixtures, then run on an explicit emulator."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import uuid

ROOT = Path(__file__).resolve().parent.parent
os.chdir(ROOT)
p = argparse.ArgumentParser()
p.add_argument('--serial', required=True)
p.add_argument('--sdk', default=os.getenv('ANDROID_HOME') or os.getenv('ANDROID_SDK_ROOT'))
p.add_argument('--source', default='qq-slim-module')
p.add_argument('--label', default='regression')
p.add_argument('--expect-fail', action='store_true')
args = p.parse_args()
if not args.serial.startswith('emulator-'):
    p.error('Tests require an explicitly selected emulator, never a physical phone.')
if not args.sdk:
    p.error('Set ANDROID_HOME or pass --sdk.')
sdk = Path(args.sdk)
bt = sdk / 'build-tools/35.0.0'
android = sdk / 'platforms/android-35/android.jar'
api = ROOT / '.local/xposed-api-82.jar'
assert hashlib.sha256(api.read_bytes()).hexdigest() == 'f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25'
work = ROOT / '.local/tests' / uuid.uuid4().hex
(work / 'classes').mkdir(parents=True)
(work / 'dex').mkdir()
records = []
env = os.environ.copy()
env['JAVA_TOOL_OPTIONS'] = env.get('JAVA_TOOL_OPTIONS', '') + ' -Duser.language=en -Duser.country=US -Dfile.encoding=UTF-8'


def run(command, check=True):
    result = subprocess.run([str(v) for v in command], stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env)
    record = {'argv': [str(v) for v in command], 'exit': result.returncode,
              'stdout': result.stdout.decode('utf-8', errors='replace'),
              'stderr': result.stderr.decode('utf-8', errors='replace')}
    records.append(record)
    print(record['stdout'], end='')
    if result.returncode:
        print(record['stderr'], end='', file=sys.stderr)
        if check:
            raise RuntimeError(f'Command exit={result.returncode}: {command[0]}')
    return record


try:
    sources = sorted(Path(args.source).joinpath('src').rglob('*.java')) + sorted(Path('tests').rglob('*.java'))
    warnings = [] if args.expect_fail else ['-Werror']
    run(['javac', '-J-Duser.language=en', '-encoding', 'UTF-8', '--release', '8', '-Xlint:all,-options', *warnings,
         '-cp', f'{android}{os.pathsep}{api}', '-d', work / 'classes', *sources])
    run(['jar', 'cf', work / 'classes.jar', '-C', work / 'classes', '.'])
    # Xposed API stubs and host fixtures exist only in this disposable test DEX, never the APK.
    run([bt / 'd8.bat', '--min-api', '28', '--lib', android, '--output', work / 'dex', work / 'classes.jar', api])
    adb = [sdk / 'platform-tools/adb.exe', '-s', args.serial]
    remote = '/data/local/tmp/qqslim-tests-' + work.name
    run([*adb, 'shell', 'mkdir', remote])
    try:
        run([*adb, 'push', work / 'dex/classes.dex', remote + '/tests.dex'])
        result = run([*adb, 'shell', f'CLASSPATH={remote}/tests.dex app_process /system/bin local.qqslim.module.RegressionTests {remote}'], check=False)
        if args.expect_fail:
            assert result['exit'] != 0 and 'FAIL ' in result['stdout'] and 'RESULT passed=' in result['stdout'], 'Baseline did not reproduce a complete regression run'
        else:
            assert result['exit'] == 0 and 'failed=0' in result['stdout'], 'Regression suite failed'
    finally:
        # Exact randomly generated fixture path, not an app or user directory.
        run([*adb, 'shell', f'rm -rf {remote}'])
finally:
    Path('artifacts/review').mkdir(parents=True, exist_ok=True)
    Path(f'artifacts/review/{args.label}.json').write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding='utf-8')
