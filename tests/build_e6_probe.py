"""Build instrumentation separately; no test-only entry point in the delivered app."""
from pathlib import Path
import sys, zipfile
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from build import ROOT, BUILD_TOOLS, ANDROID, JAVA_HOME, JAVA, run
OUT=ROOT/'build/e6-probe'
for name in ['classes','dex']:(OUT/name).mkdir(parents=True,exist_ok=True)
run([BUILD_TOOLS/'aapt2.exe','link','-o',OUT/'resources.ap_','-I',ANDROID,'--manifest',ROOT/'tests/device-test-e6-probe.xml'])
run([JAVA_HOME/'bin/javac.exe','-encoding','UTF-8','-source','8','-target','8','-classpath',str(ANDROID)+';'+str(ROOT/'build/e5/classes'),'-d',OUT/'classes',ROOT/'tests/E6Probe.java'])
run([JAVA_HOME/'bin/jar.exe','cf',OUT/'classes.jar','-C',OUT/'classes','.'])
run([JAVA,'-cp',BUILD_TOOLS/'lib/d8.jar','com.android.tools.r8.D8','--lib',ANDROID,'--classpath',ROOT/'build/e5/classes.jar','--min-api','34','--output',OUT/'dex',OUT/'classes.jar'])
with zipfile.ZipFile(OUT/'resources.ap_') as r,zipfile.ZipFile(OUT/'unsigned.apk','w') as z:
    for name in r.namelist():z.writestr(name,r.read(name))
    z.writestr('classes.dex',(OUT/'dex/classes.dex').read_bytes())
run([BUILD_TOOLS/'zipalign.exe','-f','4',OUT/'unsigned.apk',OUT/'aligned.apk'])
run([JAVA,'-jar',BUILD_TOOLS/'lib/apksigner.jar','sign','--ks',ROOT/'signing/local-test.p12','--ks-key-alias','trial','--ks-pass','pass:android','--v4-signing-enabled','false','--out',OUT/'device-tests.apk',OUT/'aligned.apk'])
