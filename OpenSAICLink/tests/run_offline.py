from pathlib import Path
import subprocess,json,hashlib
root=Path(__file__).resolve().parents[1];src=root/'app/src/main/java/org/opensaiclink/trial';out=root/'build/host-tests';out.mkdir(parents=True,exist_ok=True)
java=Path(r'E:\Soft\Dev\jetbrains\jdk\jdk-17\bin')
classes=['WlFrame','VideoQueue','AnnexB','WlPacketWriter','TouchInput','TouchFrames','DisplayGeometry','HomeLayout','SessionState','ConfigurationLogic']
subprocess.run([str(java/'javac.exe'),'-encoding','UTF-8','-d',str(out),*[str(src/(c+'.java')) for c in classes],*[str(root/'tests'/n) for n in ['ProtocolTest.java','E5HostTest.java','E6HostTest.java','FormalTest.java']]],check=True)
results=[]
for name,args in [('ProtocolTest',[]),('E5HostTest',[root/'tests/fixtures/session-20261024-125141-touch.txt']),('E6HostTest',[root/'tests/fixtures/session-20261024-143311-touch.txt']),('FormalTest',[])]:
 p=subprocess.run([str(java/'java.exe'),'-cp',str(out),'org.opensaiclink.trial.'+name,*map(str,args)],check=True,capture_output=True,text=True,encoding='utf-8');results.append(p.stdout.strip());print(p.stdout.strip())
(root/'dist/offline-checks.json').write_text(json.dumps({'checks':results,'phoneOrEmulatorUsed':False},indent=2),encoding='utf-8-sig')
