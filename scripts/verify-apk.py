"""Validate package structure, icon pixels, signature, alignment and emulator launcher."""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import struct
import subprocess
import sys
import zipfile
import re
import xml.etree.ElementTree as ET
from PIL import Image

sys.stdout.reconfigure(encoding='utf-8', errors='backslashreplace')
root = Path(__file__).resolve().parent.parent
os.chdir(root)
p = argparse.ArgumentParser()
p.add_argument('--apk')
p.add_argument('--sdk', default=os.environ.get('ANDROID_HOME'))
p.add_argument('--serial')
args = p.parse_args()
if args.serial and not args.serial.startswith('emulator-'):
    p.error('Launcher checks only install to an explicit emulator.')
manifest=ET.parse('qq-slim-module/AndroidManifest.xml').getroot()
namespace='{http://schemas.android.com/apk/res/android}'
version_name=manifest.attrib[namespace+'versionName']
version_code=int(manifest.attrib[namespace+'versionCode'])
assert version_code>0 and re.fullmatch(r'[0-9A-Za-z][0-9A-Za-z._-]*',version_name), 'Invalid version'
apk = Path(args.apk or f'artifacts/qq-slim-kit-v{version_name}.apk')
bt = Path(args.sdk) / 'build-tools/35.0.0'
records = []


def run(argv):
    result = subprocess.run([str(x) for x in argv], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    record = dict(argv=[str(x) for x in argv], exit=result.returncode,
                  stdout=result.stdout.decode('utf-8', errors='replace'), stderr=result.stderr.decode('utf-8', errors='replace'))
    records.append(record)
    assert result.returncode == 0, record
    return record['stdout']


def dex_classes(data):
    def u32(offset): return struct.unpack_from('<I', data, offset)[0]
    def string(index):
        pos = u32(u32(60) + index * 4)
        while data[pos] & 128: pos += 1
        pos += 1
        return data[pos:data.index(b'\0', pos)].decode('utf-8', errors='replace')
    return {string(u32(u32(68) + u32(u32(100) + i * 32) * 4)) for i in range(u32(96))}


try:
    with zipfile.ZipFile(apk) as z:
        assert len(z.namelist()) == len(set(z.namelist())), 'Duplicate ZIP entries'
        assert z.testzip() is None, 'ZIP corruption'
        assert z.read('assets/xposed_init').strip() == b'local.qqslim.module.SlimEntry'
        assert z.read('META-INF/xposed/scope.list').strip() == b'com.tencent.mobileqq'
        classes = dex_classes(z.read('classes.dex'))
        assert all(c.startswith('Llocal/qqslim/module/') for c in classes), classes
        assert not any('RegressionTests' in c or 'UiInstrumentation' in c for c in classes)
        assert 'Llocal/qqslim/module/SlimEntry;' in classes
        icons = [n for n in z.namelist() if n.startswith('res/') and n.endswith('.png')]
        assert len(icons) == 10
        for name in icons:
            local = Path('qq-slim-module') / name.replace('-v4/', '/')
            with Image.open(io.BytesIO(z.read(name))) as packed, Image.open(local) as original:
                assert packed.convert('RGBA').tobytes() == original.convert('RGBA').tobytes(), name
        assert 'res/mipmap-anydpi-v26/ic_launcher.xml' in z.namelist()
    signed = run([bt / 'apksigner.bat', 'verify', '--verbose', '--print-certs', apk])
    assert 'Verified using v3 scheme (APK Signature Scheme v3): true' in signed
    assert 'e4957288c9c19ee83a4b6eb25a57072b0857c48f62f3bb2b695abacb468af51e' in signed, 'v1.5 signing identity changed'
    run([bt / 'zipalign.exe', '-c', '-v', '4', apk])
    badging = run([bt / 'aapt.exe', 'dump', 'badging', apk])
    assert f"name='local.qqslim.module' versionCode='{version_code}' versionName='{version_name}'" in badging
    assert "sdkVersion:'28'" in badging and "targetSdkVersion:'35'" in badging
    assert 'android.permission.INTERNET' not in badging
    if args.serial:
        adb = [Path(args.sdk) / 'platform-tools/adb.exe', '-s', args.serial]
        installed = run([*adb, 'shell', 'dumpsys', 'package', 'local.qqslim.module'])
        version = re.search(r'versionCode=(\d+)', installed)
        if version is None or int(version.group(1)) <= 6:
            assert 'Success' in run([*adb, 'install', '-r', '.local/review-v16-baseline/qq-slim-standalone-v1.5.apk'])
        assert 'Success' in run([*adb, 'install', '-r', apk])
        for dark in (False, True):
            run([*adb, 'shell', 'cmd', 'uimode', 'night', 'yes' if dark else 'no'])
            started = run([*adb, 'shell', 'am', 'start', '-W', '-n', 'local.qqslim.module/.MainActivity'])
            assert 'Error:' not in started
            remote = '/data/local/tmp/qqslim-release-ui.xml'
            run([*adb, 'shell', 'uiautomator', 'dump', remote])
            xml = run([*adb, 'shell', 'cat', remote])
            assert 'v'+version_name in xml and 'QQ瘦身独立模块' in xml
            run([*adb, 'shell', 'rm', remote])
        run([*adb, 'shell', 'cmd', 'uimode', 'night', 'no'])
        crash = run([*adb, 'logcat', '-b', 'crash', '-d'])
        assert 'Process: local.qqslim.module,' not in crash, 'Module crash recorded'
    summary = dict(apk=str(apk.resolve()), sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
                   version_name=version_name, version_code=version_code,
                   bytes=apk.stat().st_size, icon_png_count=len(icons), class_count=len(classes),
                   signature='v3 verified; same certificate as v1.5', launcher='light/dark passed' if args.serial else 'not run')
    print(json.dumps(summary, ensure_ascii=False, indent=2))
finally:
    Path(f'artifacts/review/package-verification-v{version_name}.json').write_text(json.dumps(records, ensure_ascii=False, indent=2), encoding='utf-8')
