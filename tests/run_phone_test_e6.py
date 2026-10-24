from pathlib import Path
import subprocess,time,sys
adb=[r'E:\Soft\Tools\AndroidSDK\platform-tools\adb.exe','-s','192.168.0.8:5555'];out=Path('evidence/e6')
def run(*a):return subprocess.run(adb+list(a),check=True,capture_output=True,timeout=20).stdout
original=run('shell','settings','get','secure','enabled_accessibility_services').decode().strip();component='org.opensaiclink.trial/org.opensaiclink.trial.RemoteTouchService'
if component not in original:
 saved=(out/'phone-enabled-services-before.txt').read_text(encoding='utf-8-sig').strip()
 assert component in saved and set(original.split(':'))==set(v for v in saved.split(':') if v!=component),'Service configuration changed outside this test'
 original=original+':'+component
 run('shell','settings','put','secure','enabled_accessibility_services',original)

p=subprocess.Popen(adb+['shell','am','instrument','-w','-e','phase',sys.argv[1] if len(sys.argv)>1 else 'all','org.opensaiclink.trial.tests/org.opensaiclink.trial.E6PhoneTest'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT);time.sleep(2)
run('shell','settings','put','secure','enabled_accessibility_services',':'.join(v for v in original.split(':') if v!=component));time.sleep(.3);run('shell','settings','put','secure','enabled_accessibility_services',original)
try:result=p.communicate(timeout=150)[0].decode('utf-8')
except subprocess.TimeoutExpired:
 run('shell','am','force-stop','org.opensaiclink.trial');result=p.communicate(timeout=10)[0].decode('utf-8')+'\nTEST_TIMEOUT\n'
(out/'phone-e6-result.txt').write_text(result,encoding='utf-8-sig');print(result)
log=run('logcat','-d','-s','OpenSAICLink:I','AndroidRuntime:E').decode('utf-8');(out/'phone-e6-log.txt').write_text(log,encoding='utf-8-sig')
print('\n'.join(l for l in log.splitlines() if any(x in l for x in ['E6_CHECK','E6_TEST_FAILURE','E6_CONSENT'])))
