"""Audit cumulative E3 car exports and retain the two new captures separately."""
from pathlib import Path
import collections,hashlib,json,re,zipfile

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'analysis/e3-car-20261024'

def sha(data):return hashlib.sha256(data).hexdigest()
def main():
    OUT.mkdir(exist_ok=True)
    dest=OUT/'extracted';dest.mkdir(exist_ok=True)
    selected={};sources=[]
    old=json.loads((ROOT/'analysis/car-20261024/car-results.json').read_text(encoding='utf-8-sig'))
    old_names={v['name'] for v in old['captures']}
    for p in sorted(ROOT.parent.glob('OpenSAICLink-E3-*.zip')):
        with zipfile.ZipFile(p) as z:
            assert z.testzip() is None
            entries=[]
            for info in z.infolist():
                assert Path(info.filename).name==info.filename
                data=z.read(info.filename)
                entries.append({'name':info.filename,'bytes':len(data),'sha256':sha(data)})
                if info.filename in selected:
                    previous=selected[info.filename]
                    assert data.startswith(previous) or previous.startswith(data),'Unexpected cumulative export divergence'
                if len(data)>=len(selected.get(info.filename,b'')):selected[info.filename]=data
            sources.append({'name':p.name,'bytes':p.stat().st_size,'sha256':sha(p.read_bytes()),'members':entries})
    report={'exports':sources,'sessions':[],'captures':[]}
    for name,data in selected.items():
        if name.endswith('.h264'):
            if name in old_names:continue
            (dest/name).write_bytes(data)
            report['captures'].append({'name':name,'bytes':len(data),'sha256':sha(data)})
            continue
        text=data.decode('utf-8-sig')
        if 'START E3/0.3.0' not in text:continue
        (dest/name).write_text(text,encoding='utf-8-sig')
        lines=text.splitlines();stats=[];touch=collections.Counter();other=collections.Counter()
        for line in lines:
            if ' STATS ' in line:
                record=dict(re.findall(r'(\w+)=([^ ]+)',line));record['time']=line[:12]
                for k,v in list(record.items()):
                    if re.fullmatch(r'-?\d+',v):record[k]=int(v)
                stats.append(record)
            if ' RX_8 motionevent' in line:touch[line.split()[-1]]+=1
            if ' RX_9 ' in line:
                o=json.loads(line.split(' RX_9 ',1)[1]);other[o['command']['method']]+=1
        active=[s for s in stats if s.get('capture')=='true' and s.get('pause')=='false']
        fields=['encoded','videoWritten','videoPackets','dropped','touchRx','touchDone','maxQueueMs','maxWriteMs','maxEncodeAgeMs']
        row={'name':name,'lines':len(lines),'stats':stats,'activeStatsCount':len(active),
            'firstStats':stats[0] if stats else None,'lastStats':stats[-1] if stats else None,
            'maxima':{k:max((s.get(k,0) for s in stats),default=0) for k in fields},
            'maxEncodedMinusWritten':max((s['encoded']-s['videoWritten'] for s in active),default=0),
            'touchWireActions':dict(touch),'rxMethods':dict(other),
            'events':[l for l in lines if any(t in l for t in ['ENCODER_','CAPTURE_','ROTATION_','TOUCH_SERVICE','TOUCH_CANCELLED','TOUCH_REJECT','TOUCH_UNSUPPORTED','TOUCH_ERROR','STALLED','SLOW_VIDEO_WRITE','USB_CLOSED','SERVICE_STOPPED'])]}
        report['sessions'].append(row)
        print(json.dumps({k:row[k] for k in ['name','activeStatsCount','maxima','maxEncodedMinusWritten','touchWireActions','events']}),flush=True)
    (OUT/'car-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8-sig')

if __name__=='__main__':main()
