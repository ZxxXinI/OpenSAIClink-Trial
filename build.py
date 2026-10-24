"""Build the standalone trial using an existing Android SDK, without downloads."""
from pathlib import Path
import datetime
import hashlib
import json
import os
import shutil
import subprocess
import zipfile
import sys

ROOT = Path(__file__).resolve().parent
SDK = Path(os.environ.get('ANDROID_HOME', r'E:\Soft\Tools\AndroidSDK'))
BUILD_TOOLS = SDK / 'build-tools' / '36.0.0'
ANDROID = SDK / 'platforms' / 'android-36' / 'android.jar'
JAVA_HOME = Path(os.environ.get('JAVA_HOME', r'E:\Soft\Dev\jetbrains\jdk\jdk-17'))
JAVA = JAVA_HOME / 'bin' / 'java.exe'
BUILD = ROOT / 'build' / 'e6'
DIST = ROOT / 'dist'
VERSION_CODE = 6
VERSION_NAME = '0.6.0-e6'
APK_NAME = 'OpenSAICLink-E6-0.6.0.apk'


def run(args):
    args = [str(a) for a in args]
    executable = Path(args[0]).name.lower()
    if executable in ('javac.exe', 'keytool.exe', 'jar.exe'):
        args[1:1] = ['-J-Duser.language=en', '-J-Duser.country=US', '-J-Dfile.encoding=UTF-8']
    elif executable == 'java.exe':
        args[1:1] = ['-Duser.language=en', '-Duser.country=US', '-Dfile.encoding=UTF-8']
    result = subprocess.run(args, cwd=ROOT, capture_output=True, text=True,
                            encoding='utf-8', errors='replace')
    if result.stdout.strip():
        print(result.stdout.strip())
    if result.stderr.strip():
        print(result.stderr.strip())
    if result.returncode:
        raise RuntimeError(f'{Path(args[0]).name} failed: {result.returncode}')
    return result.stdout


def main():
    for folder in (BUILD/'classes', BUILD/'generated', BUILD/'dex', BUILD/'tests', DIST, ROOT/'signing'):
        folder.mkdir(parents=True, exist_ok=True)
    for file in (ANDROID, BUILD_TOOLS/'aapt2.exe', BUILD_TOOLS/'lib'/'d8.jar', JAVA):
        if not file.exists():
            raise FileNotFoundError(file)
    run([BUILD_TOOLS/'aapt2.exe', 'compile', '--dir', ROOT/'src/main/res', '-o', BUILD/'res.zip'])
    run([BUILD_TOOLS/'aapt2.exe', 'link', '-o', BUILD/'resources.ap_', '-I', ANDROID,
         '--manifest', ROOT/'src/main/AndroidManifest.xml', '--java', BUILD/'generated',
         '--min-sdk-version', '34', '--target-sdk-version', '36', '--version-code', VERSION_CODE,
         '--version-name', VERSION_NAME, BUILD/'res.zip'])
    sources = sorted((ROOT/'src/main/java').rglob('*.java')) + sorted((BUILD/'generated').rglob('*.java'))
    # Source code is ASCII; Chinese UI text resides in BOM-encoded XML resources.
    assert all(p.read_bytes().isascii() for p in sources), 'Java source must remain ASCII'
    arguments = ['-encoding', 'UTF-8', '-source', '8', '-target', '8', '-classpath', str(ANDROID),
                 '-d', str(BUILD/'classes')] + [str(p) for p in sources]
    argfile = BUILD/'javac.args'
    argfile.write_text('\n'.join('"'+s.replace('\\','/')+'"' for s in arguments), encoding='utf-8')
    run([JAVA_HOME/'bin/javac.exe', '@'+str(argfile)])
    run([JAVA_HOME/'bin/jar.exe', 'cf', BUILD/'classes.jar', '-C', BUILD/'classes', '.'])
    run([JAVA, '-cp', BUILD_TOOLS/'lib/d8.jar', 'com.android.tools.r8.D8', '--lib', ANDROID,
         '--min-api', '34', '--output', BUILD/'dex', BUILD/'classes.jar'])
    unsigned = BUILD/'unsigned.apk'
    with zipfile.ZipFile(BUILD/'resources.ap_') as resources, zipfile.ZipFile(unsigned, 'w') as out:
        for name in resources.namelist():
            compression = zipfile.ZIP_STORED if name == 'resources.arsc' else zipfile.ZIP_DEFLATED
            out.writestr(name, resources.read(name), compress_type=compression)
        for dex in sorted((BUILD/'dex').glob('*.dex')):
            out.writestr(dex.name, dex.read_bytes(), compress_type=zipfile.ZIP_STORED)
    run([BUILD_TOOLS/'zipalign.exe','-f','4',unsigned,BUILD/'aligned.apk'])
    key = ROOT/'signing/local-test.p12'
    if not key.exists():
        run([JAVA_HOME/'bin/keytool.exe','-genkeypair','-keystore',key,'-storetype','PKCS12',
             '-alias','trial','-storepass','android','-keypass','android','-keyalg','RSA',
             '-keysize','3072','-validity','3650','-dname','CN=OpenSAICLink Local Trial,OU=Development,O=Local,C=CN'])
    apk = DIST/APK_NAME
    signer = [JAVA,'-jar',BUILD_TOOLS/'lib/apksigner.jar']
    run(signer+['sign','--ks',key,'--ks-key-alias','trial','--ks-pass','pass:android',
                '--key-pass','pass:android','--v4-signing-enabled','false','--out',apk,BUILD/'aligned.apk'])
    signature = run(signer+['verify','--verbose','--print-certs',apk])
    run([BUILD_TOOLS/'zipalign.exe','-c','4',apk])
    host_sources = [ROOT/'src/main/java/org/opensaiclink/trial'/f for f in ('WlFrame.java','VideoQueue.java','AnnexB.java','WlPacketWriter.java','TouchInput.java','TouchFrames.java','DisplayGeometry.java')]
    run([JAVA_HOME/'bin/javac.exe','-encoding','UTF-8','-d',BUILD/'tests',*host_sources,
         ROOT/'tests/Stubs/CarHomeActivity.java',ROOT/'tests/ProtocolTest.java',ROOT/'tests/PacketizerFixture.java',
         ROOT/'tests/E5HostTest.java',ROOT/'tests/E6HostTest.java',ROOT/'tests/E6Tests.java'])
    # The regression corpus lives outside the source repository because its
    # session videos are large. Run each test only when the recorded input exists.
    tests = run([JAVA,'-cp',BUILD/'tests','org.opensaiclink.trial.ProtocolTest'])
    tests += run([JAVA,'-cp',BUILD/'tests','org.opensaiclink.trial.E6Tests'])
    e4_touch = ROOT/'analysis/e4-car-20261024/session-20261024-125141-touch.txt'
    e5_touch = ROOT/'analysis/e5-car-20261024/session-20261024-143311-touch.txt'
    if e4_touch.exists():
        tests += run([JAVA,'-cp',BUILD/'tests','org.opensaiclink.trial.E5HostTest',e4_touch])
    else:
        tests += '\nSKIP E5 recorded-touch regression: corpus not present'
    if e5_touch.exists():
        tests += run([JAVA,'-cp',BUILD/'tests','org.opensaiclink.trial.E6HostTest',e5_touch])
    else:
        tests += '\nSKIP E6 recorded-touch regression: corpus not present'
    car_results = ROOT/'analysis/car-20261023/car-results.json'
    if car_results.exists() and (ROOT/'tools/vendor/unicorn').exists():
        for fixture in json.loads(car_results.read_text(encoding='utf-8-sig'))['captures']:
            if fixture['car']:
                source=ROOT/fixture['firstAuPath']
                run([JAVA,'-cp',BUILD/'tests','org.opensaiclink.trial.PacketizerFixture',source,source.with_suffix('.e2.wl')])
        run([sys.executable,ROOT/'tests/replay_hu_decoder.py','--require-java'])
    with zipfile.ZipFile(apk) as z:
        assert not any(n.startswith('lib/') for n in z.namelist()), 'No native libraries should be bundled'
        assert 'classes.dex' in z.namelist()
        assert z.getinfo('resources.arsc').compress_type == zipfile.ZIP_STORED
    metadata = {'builtAt':datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).isoformat(),
                'apk':apk.name,'package':'org.opensaiclink.trial','version':VERSION_NAME,
                'minSdk':34,'targetSdk':36,'bytes':apk.stat().st_size,
                'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'nativeLibraries':0,
                'hostTests':tests.strip(),'signatureVerification':signature.strip(),
                'deviceValidation':'pending','carValidation':'pending'}
    (DIST/'build-info.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n',encoding='utf-8-sig')
    (DIST/(APK_NAME+'.build-info.json')).write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n',encoding='utf-8-sig')
    assert json.loads((DIST/'build-info.json').read_text(encoding='utf-8-sig'))==metadata
    print(json.dumps({k:metadata[k] for k in ('apk','bytes','sha256','hostTests')}))


if __name__=='__main__':
    main()
