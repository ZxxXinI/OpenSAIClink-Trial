"""Build the isolated UI instrumentation APK for the production candidate."""
from pathlib import Path
import sys
import zipfile

APP=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(APP))
from build import ROOT,BUILD_TOOLS,ANDROID,JAVA_HOME,JAVA,run

OUT=ROOT/'build/0.1.0-ui-test'
for folder in ('classes','dex'):
    (OUT/folder).mkdir(parents=True,exist_ok=True)
run([BUILD_TOOLS/'aapt2.exe','link','-o',OUT/'resources.ap_','-I',ANDROID,
     '--manifest',ROOT/'tests/ui-gate-manifest.xml'])
run([JAVA_HOME/'bin/javac.exe','-encoding','UTF-8','-source','8','-target','8',
     '-classpath',str(ANDROID)+';'+str(ROOT/'build/0.1.0/classes'),
     '-d',OUT/'classes',ROOT/'tests/UiGate.java'])
run([JAVA_HOME/'bin/jar.exe','cf',OUT/'classes.jar','-C',OUT/'classes','.'])
run([JAVA,'-cp',BUILD_TOOLS/'lib/d8.jar','com.android.tools.r8.D8','--lib',ANDROID,
     '--classpath',ROOT/'build/0.1.0/classes.jar','--min-api','34',
     '--output',OUT/'dex',OUT/'classes.jar'])
unsigned=OUT/'unsigned.apk'
with zipfile.ZipFile(OUT/'resources.ap_') as resources,zipfile.ZipFile(unsigned,'w') as output:
    for name in resources.namelist():
        output.writestr(name,resources.read(name))
    output.writestr('classes.dex',(OUT/'dex/classes.dex').read_bytes())
run([BUILD_TOOLS/'zipalign.exe','-f','4',unsigned,OUT/'aligned.apk'])
run([JAVA,'-jar',BUILD_TOOLS/'lib/apksigner.jar','sign',
     '--ks',ROOT.parents[1]/'signing/local-test.p12','--ks-key-alias','trial',
     '--ks-pass','pass:android','--v4-signing-enabled','false',
     '--out',OUT/'ui-gate.apk',OUT/'aligned.apk'])
print(OUT/'ui-gate.apk')
