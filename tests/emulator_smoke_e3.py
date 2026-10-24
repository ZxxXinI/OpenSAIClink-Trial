"""Verify capture consent, background capture, orientation restoration and export UI."""
from pathlib import Path
import json,re,time
import emulator_smoke as common
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'evidence/e3'
common.OUT=OUT
adb=common.adb

def tap_resource(resource,name):
    for attempt in range(5):
        tree=common.hierarchy(name+'-'+str(attempt))
        for node in tree.iter('node'):
            if node.get('resource-id')==resource:
                x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
                if x2>x1 and y2>y1:
                    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));return
        nodes=[n for n in tree.iter('node') if n.get('scrollable')=='true' and n.get('class')=='android.widget.ScrollView']
        if not nodes:break
        x1,y1,x2,y2=map(int,re.findall(r'\d+',nodes[0].get('bounds')))
        adb('shell','input','swipe',str((x1+x2)//2),str(y1+(y2-y1)*4//5),str((x1+x2)//2),str(y1+(y2-y1)//5),'300')
    raise AssertionError('Resource not found: '+resource)

def main():
    # A previously interrupted test can leave SystemUI's consent dialog above the app.
    adb('shell','input','keyevent','KEYCODE_BACK')
    adb('shell','am','force-stop','org.opensaiclink.trial')
    adb('shell','settings','put','system','accelerometer_rotation','0')
    adb('shell','settings','put','system','user_rotation','0')
    adb('logcat','-c')
    adb('shell','am','start','-f','0x14000000','-n','org.opensaiclink.trial/.MainActivity')
    time.sleep(2)
    local='\u672c\u673a\u5f55\u5c4f\u81ea\u68c0'
    common.tap_text(local,'main-before-consent')
    tap_resource('android:id/button2','consent-denied')
    time.sleep(1)
    assert 'CAPTURE_DENIED' in common.log()
    common.tap_text(local,'main-after-denial')
    tap_resource('android:id/button1','consent-approved')
    deadline=time.monotonic()+30
    while 'FIRST_ENCODED_FRAME' not in common.log():
        assert time.monotonic()<deadline,'No encoded output'
        time.sleep(.5)
    assert adb('shell','settings','get','system','user_rotation').strip()==b'1'
    time.sleep(5)
    (OUT/'emulator-pattern.png').write_bytes(adb('exec-out','screencap','-p'))
    before=common.log()
    adb('shell','input','keyevent','KEYCODE_HOME')
    adb('shell','am','start','-a','android.settings.SETTINGS')
    time.sleep(12)
    after=common.log()
    count=lambda s:int(re.findall(r'encoded=(\d+)',s)[-1])
    assert count(after)>count(before),'Capture stopped in background'
    assert 'org.opensaiclink.trial' in adb('shell','dumpsys','media_projection').decode('utf-8')
    adb('shell','am','start','-f','0x14000000','-n','org.opensaiclink.trial/.MainActivity')
    common.tap_text('\u505c\u6b62\u8bd5\u9a8c','stop')
    deadline=time.monotonic()+12
    while 'org.opensaiclink.trial' in adb('shell','dumpsys','media_projection').decode('utf-8'):
        assert time.monotonic()<deadline,'Projection not released'
        time.sleep(.3)
    time.sleep(1)
    assert adb('shell','settings','get','system','user_rotation').strip()==b'0'
    assert adb('shell','settings','get','system','accelerometer_rotation').strip()==b'0'
    logs=common.log()
    for error in ['FATAL EXCEPTION','ENCODER_ERROR','CAPTURE_START_ERROR']:assert error not in logs,error
    assert 'fps=15' in logs and 'ROTATION_RESTORED' in logs
    (OUT/'emulator-final-log.txt').write_text(logs,encoding='utf-8-sig')
    (OUT/'emulator-main.png').write_bytes(adb('exec-out','screencap','-p'))
    common.tap_text('\u5bfc\u51fa\u8bca\u65ad','export')
    tap_resource('android:id/button1','export-save')
    time.sleep(2)
    exports=adb('shell','find','/sdcard/Download','-maxdepth','1','-name','OpenSAICLink-E3-*.zip').decode().splitlines()
    assert exports,'No diagnostic ZIP exported'
    adb('pull',exports[-1],str(OUT/'emulator-diagnostic-E3.zip'))
    report={'captureConsentDeniedThenGranted':True,'backgroundBefore':count(before),'backgroundAfter':count(after),
        'landscapeAppliedAndRestored':True,'projectionReleasedOnStop':True,'fps15':True,'zipExported':True,
        'physicalUsbHeadUnitAndLatency':'not tested'}
    (OUT/'emulator-smoke.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8-sig')
    print(json.dumps(report),flush=True)

if __name__=='__main__':main()
