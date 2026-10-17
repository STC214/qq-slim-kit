import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import uuid
import sys
sys.stdout.reconfigure(encoding='utf-8', errors='backslashreplace')
sys.stderr.reconfigure(encoding='utf-8', errors='backslashreplace')

root = Path(__file__).resolve().parent.parent
os.chdir(root)
p = argparse.ArgumentParser()
p.add_argument('--serial', required=True)
p.add_argument('--sdk', default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
args = p.parse_args()
if not args.serial.startswith('emulator-'):
    p.error('Select an explicit disposable emulator.')
if not args.sdk:
    p.error('Set ANDROID_HOME or pass --sdk.')
work = Path('.local/ui-tests') / uuid.uuid4().hex
source = work / 'source'
shutil.copytree('qq-slim-module/src', source / 'src')
for file in Path('tests/ui').glob('*.java'):
    shutil.copyfile(file, source / 'src/local/qqslim/module' / file.name)
shutil.copyfile('tests/ui/AndroidManifest.xml', source / 'AndroidManifest.xml')
(source / 'assets').mkdir()
(source / 'META-INF').mkdir()
records = []


def run(command, check=True):
    result = subprocess.run([str(x) for x in command], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    record = dict(argv=[str(x) for x in command], exit=result.returncode,
                  stdout=result.stdout.decode('utf-8', errors='replace'), stderr=result.stderr.decode('utf-8', errors='replace'))
    records.append(record)
    print(record['stdout'], end='')
    if result.returncode and check:
        print(record['stderr'])
        raise RuntimeError('Command failed: ' + str(command[0]))
    return record


adb = [Path(args.sdk) / 'platform-tools/adb.exe', '-s', args.serial]
try:
    run(['powershell', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', 'scripts/build.ps1', '-Sdk', args.sdk,
         '-Source', source, '-Output', work / 'ui-tests.apk'])
    run([*adb, 'install', '-r', work / 'ui-tests.apk'])
    for dark in (False, True):
        run([*adb, 'shell', 'cmd', 'uimode', 'night', 'yes' if dark else 'no'])
        result = run([*adb, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'dark', str(dark).lower(),
                      'local.qqslim.test/local.qqslim.module.UiInstrumentation'])
        assert 'failed=0' in result['stdout'] and 'INSTRUMENTATION_CODE: -1' in result['stdout'], 'UI suite failed'
finally:
    run([*adb, 'shell', 'cmd', 'uimode', 'night', 'no'])
    run([*adb, 'uninstall', 'local.qqslim.test'], check=False)
    Path('artifacts/review/ui-regression.json').write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding='utf-8')
