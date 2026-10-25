from pathlib import Path
import subprocess,json,hashlib,shutil,re,zipfile,datetime,xml.etree.ElementTree as ET
from loguru import logger
logger.remove()
from androguard.core.apk import APK
from androguard.core.dex import DEX
root=Path(__file__).resolve().parents[1];out=root/'dist';out.mkdir(exist_ok=True)
source=root/'app/build/outputs/apk/release/app-release.apk';target=out/'OpenSAICLink-1.0.0-rc3.apk'
a=APK(str(source));assert a.get_package()=='org.opensaiclink.trial';assert a.get_androidversion_code()=='102' and a.get_androidversion_name()=='1.0.0-rc3';assert a.get_attribute_value('application','debuggable')!='true'
assert a.get_min_sdk_version()=='34' and a.get_target_sdk_version()=='36'
with zipfile.ZipFile(source) as z:
 assert z.testzip() is None;assert not any(n.startswith('lib/') for n in z.namelist())
 classes=[c.get_name() for n in z.namelist() if n.endswith('.dex') for c in DEX(z.read(n)).get_classes()]
 assert not any(any(label in c for label in ['PatternActivity','DeviceTest','PhoneTest','E6Probe','RotationOverlay']) for c in classes)
java=Path(r'E:\Soft\Dev\jetbrains\jdk\jdk-17\bin\java.exe');signer=Path(r'E:\Soft\Tools\AndroidSDK\build-tools\36.0.0\lib\apksigner.jar')
r=subprocess.run([str(java),'-jar',str(signer),'verify','--verbose','--print-certs',str(source)],check=True,capture_output=True,text=True,encoding='utf-8');cert=re.search(r'certificate SHA-256 digest: ([0-9a-f]+)',r.stdout).group(1)
assert cert=='a077a13c97e65dc45a4841f2686175acd218c70297899febdfe161c98599afa6'
old=root.parent/'OpenSAICLink-Trial'
if not old.is_dir(): old=root.parent
oldapk=APK(str(old/'dist/OpenSAICLink-E6-0.6.0.apk'));assert sorted(a.get_permissions())==sorted(oldapk.get_permissions())
assert 'org.opensaiclink.trial.RemoteTouchService' in a.get_services()
reportxml=root/'app/build/reports/lint-results-release.xml';lint=ET.parse(reportxml).getroot();errors=[i.attrib for i in lint.findall('issue') if i.get('severity') in ('Error','Fatal')];assert not errors,errors
core={}
for name in ['WlFrame','WlPacketWriter','WelinkSession','AnnexB','VideoQueue','TouchInput','TouchFrames','DisplayGeometry','DisplayRenderer','ProjectionRotation','AoaTransport']:
 rel='src/main/java/org/opensaiclink/trial/'+name+'.java';baseline=subprocess.run(['git','show','v0.6.0-e6:'+rel],cwd=old,check=True,capture_output=True).stdout
 current=(root/'app'/rel).read_bytes();assert current==baseline,name;core[name]=hashlib.sha256(current).hexdigest()
shutil.copy2(source,target)
checks=json.loads((out/'offline-checks.json').read_text(encoding='utf-8-sig'))
report={'builtAt':datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).isoformat(),'apk':target.name,'package':a.get_package(),'versionCode':102,'versionName':'1.0.0-rc3','bytes':target.stat().st_size,'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'certificateSha256':cert,'canReplaceE6SigningAndIdentity':True,'debuggable':False,'permissionsUnchanged':True,'minSdk':34,'targetSdk':36,'nativeLibraries':0,'testEntrypointsIncluded':False,'lintErrors':0,'lintWarnings':sum(i.get('severity')=='Warning' for i in lint.findall('issue')),'offlineChecks':checks['checks'],'verbatimE6Core':core,'phoneOrEmulatorTested':False,'carValidation':'Pending user acceptance'}
(out/'build-info.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8-sig')
(out/'signature-verification.txt').write_text(r.stdout,encoding='utf-8-sig')
print(json.dumps({k:report[k] for k in ['apk','bytes','sha256','lintErrors','lintWarnings','canReplaceE6SigningAndIdentity']}))
