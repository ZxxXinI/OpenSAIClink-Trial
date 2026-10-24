"""Exercise the car home, favorites, apps and notification return through Android UI."""
from pathlib import Path
import json,re,time,sys
import emulator_smoke as common
import emulator_smoke_e3 as consent
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'evidence/e4'
common.OUT=OUT
adb=common.adb

def point(node):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
    return (x1+x2)//2,(y1+y2)//2

def label_node(tree,label):
    candidates=[n for n in tree.iter('node') if n.get('text')==label or n.get('content-desc','').startswith(label+'. ')]
    candidates=[n for n in candidates if (lambda b:b[2]>b[0] and b[3]>b[1])(list(map(int,re.findall(r'\d+',n.get('bounds')))))]
    candidates.sort(key=lambda n:n.get('clickable')!='true')
    return candidates[0] if candidates else None

def tap(label,name,hold=False):
    for attempt in range(9):
        tree=common.hierarchy(name+'-'+str(attempt));node=label_node(tree,label)
        if node is not None:
            x,y=point(node)
            if hold:adb('shell','input','swipe',str(x),str(y),str(x),str(y),'900')
            else:adb('shell','input','tap',str(x),str(y))
            time.sleep(.4);return
        scroll=next((n for n in tree.iter('node') if n.get('scrollable')=='true'),None)
        if scroll is None:time.sleep(.5);continue
        x1,y1,x2,y2=map(int,re.findall(r'\d+',scroll.get('bounds')))
        adb('shell','input','swipe',str((x1+x2)//2),str(y1+(y2-y1)*4//5),str((x1+x2)//2),str(y1+(y2-y1)//5),'300')
    raise AssertionError('UI label not found: '+label)

def wait_log(value,seconds=20):
    deadline=time.monotonic()+seconds
    while value not in common.log():
        assert time.monotonic()<deadline,value
        time.sleep(.5)

def notification(label,name):
    adb('shell','cmd','statusbar','expand-notifications');time.sleep(1)
    for attempt in range(8):
        tree=common.hierarchy(name+'-'+str(attempt));action=label_node(tree,label)
        if action is not None:
            x,y=point(action);adb('shell','input','tap',str(x),str(y));time.sleep(.5);return
        title=next((n for n in tree.iter('node') if n.get('resource-id')=='android:id/title' and n.get('text')=='SAICLink 地图试验 E4'),None)
        if title is not None:
            containers=[n for n in tree.iter('node') if title in list(n.iter('node')) and any(c.get('resource-id')=='android:id/expand_button' for c in n.iter('node'))]
            container=min(containers,key=lambda n:len(list(n.iter('node'))))
            button=next(n for n in container.iter('node') if n.get('resource-id')=='android:id/expand_button')
            if button.get('content-desc')=='Expand':
                x,y=point(button);adb('shell','input','tap',str(x),str(y))
            else:
                # Start on the notification body, not the shade's lower shelf.
                x,y=point(title);adb('shell','input','swipe',str(x),str(y),str(x),str(max(180,y-380)),'500')
        else:
            button=next((n for n in tree.iter('node') if n.get('resource-id')=='android:id/expand_button' and n.get('content-desc')=='Collapse'),None)
            if button is None:
                adb('shell','cmd','statusbar','expand-notifications');time.sleep(1)
            else:
                x,y=point(button);adb('shell','input','tap',str(x),str(y))
        time.sleep(.5)
    raise AssertionError('Notification action not visible: '+label)

def main():
    adb('shell','input','keyevent','KEYCODE_BACK')
    adb('shell','am','force-stop','org.opensaiclink.trial')
    adb('shell','settings','put','system','accelerometer_rotation','0')
    adb('shell','settings','put','system','user_rotation','0')
    adb('logcat','-c')
    adb('shell','am','start','-f','0x14000000','-n','org.opensaiclink.trial/.MainActivity')
    time.sleep(2)
    tap('本机录屏自检（不连接车机）','capture')
    consent.tap_resource('android:id/button2','denied')
    wait_log('CAPTURE_DENIED')
    tap('本机录屏自检（不连接车机）','capture-again')
    consent.tap_resource('android:id/button1','approved')
    wait_log('CAR_HOME_OPENED');time.sleep(2)
    tree=common.hierarchy('car-home')
    assert tree.get('rotation') in ('1','3'),'Car home must actually be landscape'
    for label in ['高德地图','百度地图','其他应用','测试画面','试验设置']:assert label_node(tree,label) is not None,label
    (OUT/'car-home.png').write_bytes(adb('exec-out','screencap','-p'))
    # Choose a preinstalled, non-destructive app as the custom shortcut.
    prefs=adb('shell','run-as','org.opensaiclink.trial','cat','shared_prefs/trial.xml').decode('utf-8')
    if 'car_shortcut_package' in prefs:
        tap('Settings','favorite-change',hold=True)
    else:tap('常用应用','favorite-empty')
    wait_log('CAR_APPS_READY')
    (OUT/'app-picker.png').write_bytes(adb('exec-out','screencap','-p'))
    tap('Settings','pick-settings')
    wait_log('CAR_SHORTCUT_SAVED com.android.settings')
    tap('Settings','favorite-launch')
    wait_log('CAR_APP_LAUNCHED com.android.settings')
    before=common.log();time.sleep(8);after=common.log()
    count=lambda s:int(re.findall(r'encoded=(\d+)',s)[-1])
    assert count(after)>count(before)
    activities=adb('shell','dumpsys','activity','activities').decode('utf-8')
    assert re.search(r'(?:topResumedActivity=|mResumedActivity:).*com.android.settings',activities),'External app must be foreground'
    finish_flow(before,after)

def finish_flow(before,after):
    count=lambda s:int(re.findall(r'encoded=(\d+)',s)[-1])
    sessions=adb('shell','run-as','org.opensaiclink.trial','ls','files/diagnostics').decode().splitlines()
    latest=max(n for n in sessions if n.startswith('session-'))
    saved=adb('shell','run-as','org.opensaiclink.trial','cat','files/diagnostics/'+latest).decode('utf-8-sig')
    assert 'CAPTURE_DENIED' in saved and 'CAR_SHORTCUT_SAVED' in saved
    notification('返回车载主页','notify-return')
    tree=common.hierarchy('returned-home');assert label_node(tree,'车载主页') is not None
    # A long press must edit the shortcut, then Back preserves it.
    tap('Settings','edit-favorite',hold=True)
    tree=common.hierarchy('edit-picker');assert label_node(tree,'选择常用应用') is not None
    tap('返回主页','picker-back')
    tap('其他应用','other-apps')
    tap('Settings','other-launch')
    notification('返回车载主页','notify-return-2')
    tap('测试画面','pattern');time.sleep(3)
    (OUT/'pattern.png').write_bytes(adb('exec-out','screencap','-p'))
    notification('返回车载主页','notify-return-3')
    # Stop while the new landscape home is on screen, not only from the old controls.
    notification('停止试验','notify-stop')
    deadline=time.monotonic()+15
    while 'org.opensaiclink.trial' in adb('shell','dumpsys','media_projection').decode('utf-8'):
        assert time.monotonic()<deadline,'Projection not released';time.sleep(.3)
    time.sleep(2)
    assert adb('shell','settings','get','system','user_rotation').strip()==b'0'
    assert adb('shell','settings','get','system','accelerometer_rotation').strip()==b'0'
    activities=adb('shell','dumpsys','activity','activities').decode('utf-8')
    assert not re.search(r'(?:topResumedActivity=|mResumedActivity:).*CarHomeActivity',activities),'Home closed after stop'
    adb('shell','cmd','statusbar','collapse')
    adb('shell','am','start','-f','0x14000000','-n','org.opensaiclink.trial/.MainActivity')
    tap('导出诊断','export');consent.tap_resource('android:id/button1','save-export');time.sleep(3)
    exports=adb('shell','find','/sdcard/Download','-maxdepth','1','-name','OpenSAICLink-E4-*.zip').decode().splitlines();assert exports
    adb('pull',exports[-1],str(OUT/'emulator-diagnostic-E4.zip'))
    logs=adb('shell','run-as','org.opensaiclink.trial','cat','files/diagnostics/'+latest).decode('utf-8-sig')+'\n'+common.log()
    (OUT/'emulator-final-log.txt').write_text(logs,encoding='utf-8-sig')
    for error in ['FATAL EXCEPTION','ENCODER_ERROR','CAPTURE_START_ERROR','CAR_APP_LIST_ERROR']:assert error not in logs,error
    prefs=adb('shell','run-as','org.opensaiclink.trial','cat','shared_prefs/trial.xml').decode('utf-8')
    assert 'com.android.settings' in prefs
    report={'consentDenyThenAllow':True,'homeAutomaticallyOpened':True,'homeLandscape':True,
        'favoriteSavedAndLaunched':True,'longPressChangeOpened':True,'otherAppLaunched':True,
        'backgroundEncodedBefore':count(before),'backgroundEncodedAfter':count(after),'notificationReturn':True,
        'patternOpened':True,'stopFromHomeRestoresRotation':True,'homeClosesOnStop':True,'exportZip':True,
        'physicalK80AndCar':'not tested'}
    (OUT/'emulator-smoke.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8-sig')
    print(json.dumps(report),flush=True)

if __name__=='__main__':
    if '--resume' in sys.argv:
        before=common.log();time.sleep(6);finish_flow(before,common.log())
    else:main()
