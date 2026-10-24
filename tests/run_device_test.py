"""Reconnect the emulator accessibility service inside the instrumentation process."""
from pathlib import Path
import subprocess,time
ROOT=Path(__file__).resolve().parents[1]
ADB=[r'E:\Soft\Tools\AndroidSDK\platform-tools\adb.exe','-s','emulator-5580']
subprocess.run(ADB+['shell','am','force-stop','org.opensaiclink.trial'],check=True,capture_output=True)
subprocess.run(ADB+['shell','input','keyevent','KEYCODE_HOME'],check=True,capture_output=True)
subprocess.run(ADB+['logcat','-c'],check=True,capture_output=True)
p=subprocess.Popen(ADB+['shell','am','instrument','-w','org.opensaiclink.trial.tests/org.opensaiclink.trial.E3DeviceTest'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
time.sleep(2)
for args in [['shell','settings','delete','secure','enabled_accessibility_services'],
             ['shell','settings','put','secure','enabled_accessibility_services','org.opensaiclink.trial/org.opensaiclink.trial.RemoteTouchService'],
             ['shell','settings','put','secure','accessibility_enabled','1']]:
    subprocess.run(ADB+args,check=True,capture_output=True,timeout=10)
out=p.communicate(timeout=40)[0].decode('utf-8')
(ROOT/'evidence/e3/device-test-result.txt').write_text(out,encoding='utf-8-sig')
log=subprocess.run(ADB+['logcat','-d','-s','OpenSAICLink:I','AndroidRuntime:E'],check=True,capture_output=True).stdout.decode('utf-8')
(ROOT/'evidence/e3/device-test-log.txt').write_text(log,encoding='utf-8-sig')
print(out)
assert 'failure=' not in out and 'PASS ' in out
