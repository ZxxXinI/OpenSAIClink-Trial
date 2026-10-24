"""Real consent, GPU/direct capture, launcher rotation, cleanup and exported diagnostics."""
from pathlib import Path
import json,re,time,zipfile
import emulator_smoke as common
import emulator_smoke_e3 as consent
import emulator_smoke_e4 as ui
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'evidence/e5'
common.OUT=OUT
adb=common.adb

def wait_log(word,seconds=25):
 end=time.monotonic()+seconds
 while word not in common.log():
  assert time.monotonic()<end,word
  time.sleep(.5)

def toggle(label,expected):
 ui.tap(label,'toggle')
 # Preference is verified separately after stop.

def capture(mode):
 before=adb('shell','run-as','org.opensaiclink.trial','ls','files/diagnostics').decode().splitlines()
 ui.tap('本机录屏自检（不连接车机）',mode+'-capture')
 consent.tap_resource('android:id/button1',mode+'-consent')
 wait_log('CAR_HOME_OPENED');time.sleep(2)
 tree=common.hierarchy(mode+'-car-home')
 assert tree.get('rotation') in ('1','3')
 for name in ['高德地图','百度地图','其他应用','测试画面','试验设置']:assert ui.label_node(tree,name) is not None,name
 time.sleep(2)
 (OUT/(mode+'-home.png')).write_bytes(adb('exec-out','screencap','-p'))
 ui.tap('测试画面',mode+'-pattern');time.sleep(20)
 (OUT/(mode+'-pattern.png')).write_bytes(adb('exec-out','screencap','-p'))
 after=adb('shell','run-as','org.opensaiclink.trial','ls','files/diagnostics').decode().splitlines()
 sample=next(n for n in after if n not in before and n.endswith('.h264'))
 if mode=='gpu':
  adb('shell','input','keyevent','KEYCODE_HOME');time.sleep(3)
  display=adb('shell','dumpsys','window','displays').decode()
  (OUT/'gpu-phone-launcher-display.txt').write_text(display,encoding='utf-8-sig')
  assert 'init=1080x2400' in display and 'cur=2400x1080 app=2400x1080' in display,'Emulator launcher must rotate'
  (OUT/'gpu-phone-launcher.png').write_bytes(adb('exec-out','screencap','-p'))
  time.sleep(3)
 adb('shell','am','start','-f','0x14000000','-n','org.opensaiclink.trial/.MainActivity')
 ui.tap('停止试验',mode+'-stop');time.sleep(2)
 assert 'org.opensaiclink.trial' not in adb('shell','dumpsys','media_projection').decode()
 assert 'OpenSAICLink temporary orientation' not in adb('shell','dumpsys','window','windows').decode()
 assert adb('shell','settings','get','system','user_rotation').strip()==b'0'
 assert adb('shell','settings','get','system','accelerometer_rotation').strip()==b'0'
 data=adb('exec-out','run-as','org.opensaiclink.trial','cat','files/diagnostics/'+sample)
 (OUT/(mode+'-sample.h264')).write_bytes(data)
 print(mode+' capture stopped and saved: '+str(len(data)),flush=True)
 return sample

def main():
 adb('shell','input','keyevent','KEYCODE_BACK');adb('shell','am','force-stop','org.opensaiclink.trial')
 adb('shell','cmd','statusbar','collapse')
 adb('shell','settings','put','system','accelerometer_rotation','0');adb('shell','settings','put','system','user_rotation','0')
 adb('shell','am','start','-f','0x14000000','-n','org.opensaiclink.trial/.MainActivity');time.sleep(1)
 for args in [('delete','secure','enabled_accessibility_services'),('put','secure','enabled_accessibility_services','org.opensaiclink.trial/org.opensaiclink.trial.RemoteTouchService'),('put','secure','accessibility_enabled','1')]:adb('shell','settings',*args)
 adb('logcat','-c')
 gpu=capture('gpu')
 ui.tap('横屏画面铺满车机（下次投屏生效）','disable-fill')
 prefs=adb('shell','run-as','org.opensaiclink.trial','cat','shared_prefs/trial.xml').decode()
 assert 'name="fill_display" value="false"' in prefs
 adb('logcat','-c')
 direct=capture('direct')
 ui.tap('横屏画面铺满车机（下次投屏生效）','restore-fill')
 ui.tap('导出诊断','export');consent.tap_resource('android:id/button1','save-export');time.sleep(3)
 exports=adb('shell','find','/sdcard/Download','-maxdepth','1','-name','OpenSAICLink-E5-*.zip').decode().splitlines();assert exports
 for attempt in range(15):
  adb('pull',exports[-1],str(OUT/'emulator-diagnostic-E5.zip'))
  try:
   with zipfile.ZipFile(OUT/'emulator-diagnostic-E5.zip') as ready:assert ready.testzip() is None
   break
  except zipfile.BadZipFile:time.sleep(2)
 else:raise AssertionError('Cumulative diagnostic export is incomplete')
 with zipfile.ZipFile(OUT/'emulator-diagnostic-E5.zip') as z:
  assert z.testzip() is None
  logs='\n'.join(z.read(n).decode('utf-8-sig') for n in z.namelist() if n.endswith('.txt') and b'CAPTURE_STARTED' in z.read(n) and b'E5/0.5.0' in z.read(n))
 logs=logs[logs.rfind('START E5/0.5.0'):]
 assert 'DISPLAY_RENDERER' in logs and 'fill=true' in logs
 for error in ['ENCODER_ERROR','CAPTURE_START_ERROR','DISPLAY_RENDER_ERROR','DISPLAY_FALLBACK','DISPLAY_CLOSE_TIMEOUT']:assert error not in logs,error
 (OUT/'capture-log.txt').write_text(logs,encoding='utf-8-sig')
 report={'gpuSample':gpu,'directSample':direct,'launcherRotated':True,'homeLandscape':True,'fillRestoredToTrue':True,'stopRemovesOverlayAndProjection':True,'originalRotationRestored':True,'exportedZip':True,'physicalCar':'not tested'}
 (OUT/'emulator-smoke.json').write_text(json.dumps(report,indent=2),encoding='utf-8-sig');print(json.dumps(report),flush=True)
if __name__=='__main__':main()
