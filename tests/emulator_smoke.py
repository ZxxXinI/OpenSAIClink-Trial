"""Exercise real Android 16 screen consent and foreground capture on our isolated AVD."""
from pathlib import Path
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
ADB=[r'E:\Soft\Tools\AndroidSDK\platform-tools\adb.exe','-s','emulator-5580']
OUT=ROOT/'evidence/e2'
OUT.mkdir(parents=True,exist_ok=True)


def adb(*args):
    r=subprocess.run(ADB+list(args),capture_output=True,timeout=30)
    if r.returncode:
        raise RuntimeError(r.stderr.decode('utf-8','replace'))
    return r.stdout


def hierarchy(name):
    adb('shell','uiautomator','dump','/sdcard/opensaiclink-window.xml')
    text=adb('shell','cat','/sdcard/opensaiclink-window.xml').decode('utf-8')
    (OUT/(name+'.xml')).write_text(text,encoding='utf-8-sig')
    return ET.fromstring(text)


def tap_text(text,name):
    for attempt in range(7):
        tree=hierarchy(name+'-'+str(attempt))
        for n in tree.iter('node'):
            if text in n.get('text','') and n.get('clickable')=='true':
                x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
                if x2<=x1 or y2<=y1:continue
                adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
                return
        bounds=next(tree.iter('node')).get('bounds')
        x1,y1,x2,y2=map(int,re.findall(r'\d+',bounds))
        adb('shell','input','swipe',str((x1+x2)//2),str(y1+(y2-y1)*3//4),
            str((x1+x2)//2),str(y1+(y2-y1)//4),'300')
    raise AssertionError('Visible button not found: '+text)


def tap_resource(resource,name):
    for n in hierarchy(name).iter('node'):
        if n.get('resource-id')==resource:
            x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')))
            adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
            return
    raise AssertionError('Resource not found: '+resource)


def log():
    return adb('logcat','-d','-s','OpenSAICLink:I','AndroidRuntime:E').decode('utf-8','replace')


def main():
    # The test service is private; shell cannot invoke its stopService path.
    adb('shell','am','force-stop','org.opensaiclink.trial')
    time.sleep(1)
    adb('shell','settings','put','system','accelerometer_rotation','0')
    adb('shell','settings','put','system','user_rotation','0')
    adb('logcat','-c')
    adb('shell','am','start','-f','0x14000000','-n','org.opensaiclink.trial/.MainActivity')
    local='\u672c\u673a\u5f55\u5c4f\u81ea\u68c0'
    tap_text(local,'final-main')
    tap_resource('android:id/button2','consent-denied')
    time.sleep(1)
    assert 'CAPTURE_DENIED' in log()
    assert 'org.opensaiclink.trial' not in adb('shell','dumpsys','media_projection').decode('utf-8')
    tap_text(local,'main-after-denial')
    tap_resource('android:id/button1','consent-approved')
    deadline=time.monotonic()+20
    while 'FIRST_ENCODED_FRAME' not in log():
        if time.monotonic()>deadline:raise AssertionError('Capture did not produce an encoded frame')
        time.sleep(1)
    time.sleep(5)
    adb('shell','settings','put','system','accelerometer_rotation','0')
    adb('shell','settings','put','system','user_rotation','1')
    time.sleep(3)
    (OUT/'emulator-final-pattern.png').write_bytes(adb('exec-out','screencap','-p'))
    before=log()
    adb('shell','input','keyevent','KEYCODE_HOME')
    adb('shell','am','start','-a','android.settings.SETTINGS')
    time.sleep(10)
    after=log()
    def count(text):
        values=re.findall(r'encoded=(\d+)',text)
        return int(values[-1]) if values else 0
    assert count(after)>count(before), 'Encoding must continue after the app is in background'
    (OUT/'emulator-background-projection.txt').write_text(adb('shell','dumpsys','media_projection').decode('utf-8'),encoding='utf-8-sig')
    # NEW_TASK | CLEAR_TOP returns to controls rather than the pattern task top.
    adb('shell','am','start','-f','0x14000000','-n','org.opensaiclink.trial/.MainActivity')
    time.sleep(1)
    tap_text('\u505c\u6b62\u8bd5\u9a8c','before-stop')
    deadline=time.monotonic()+10
    while 'org.opensaiclink.trial' in adb('shell','dumpsys','media_projection').decode('utf-8'):
        if time.monotonic()>deadline:raise AssertionError('MediaProjection was not released after stop')
        time.sleep(.3)
    final_log=log()
    assert 'FATAL EXCEPTION' not in final_log
    assert 'CAPTURE_START_ERROR' not in final_log and 'ENCODER_ERROR' not in final_log
    (OUT/'emulator-final-log.txt').write_text(final_log,encoding='utf-8-sig')
    (OUT/'emulator-final-services.txt').write_text(adb('shell','dumpsys','activity','services','org.opensaiclink.trial').decode('utf-8'),encoding='utf-8-sig')
    report={'androidApi':36,'device':'isolated x86_64 emulator','consentDenialHandled':True,
            'captureStartedAfterFreshConsent':True,'backgroundEncodedBefore':count(before),
            'backgroundEncodedAfter':count(after),'rotationHandled':'CAPTURE_RESIZE content=2400x1080' in final_log,
            'projectionReleasedOnStop':True,'fatalExceptions':0,
            'usbAccessoryAndHeadUnit':'not tested; physical car unavailable'}
    (OUT/'emulator-smoke.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8-sig')
    print(json.dumps(report),flush=True)


if __name__=='__main__':main()
