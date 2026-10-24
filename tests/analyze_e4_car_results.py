"""Audit the returned E4 exports, sessions, stream counters and pointer lifetimes."""
from pathlib import Path
import collections,datetime,hashlib,json,re,zipfile
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'analysis/e4-car-20261024'

def seconds(value):
    h,m,s=value.split(':');return int(h)*3600+int(m)*60+float(s)

def main():
    OUT.mkdir(exist_ok=True);dest=OUT/'extracted';dest.mkdir(exist_ok=True)
    selected={};sources=[];capture_times=[]
    for path in sorted(ROOT.parent.glob('OpenSAICLink-E4-*.zip')):
        with zipfile.ZipFile(path) as z:
            assert z.testzip() is None;entries=[]
            for info in z.infolist():
                assert Path(info.filename).name==info.filename
                data=z.read(info.filename);prior=selected.get(info.filename,b'')
                assert data.startswith(prior) or prior.startswith(data),'Export history diverged'
                if len(data)>=len(prior):selected[info.filename]=data
                entries.append({'name':info.filename,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
            sources.append({'name':path.name,'bytes':path.stat().st_size,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'entries':entries})
    report={'exports':sources,'sessions':[],'captures':[]}
    for name,data in selected.items():
        if not name.endswith('.txt'):continue
        text=data.decode('utf-8-sig')
        if 'START E4/0.4.0' not in text:continue
        (dest/name).write_text(text,encoding='utf-8-sig')
        stats=[];segments=[];segment=None;touch=[];active=set();overlap=[];ids=collections.Counter();maximum=0;orphans=0
        for line in text.splitlines():
            if ' CAPTURE_STARTED ' in line:
                segment={'start':line[:12],'stats':[],'events':[]};segments.append(segment);active.clear()
                capture_times.append(seconds(line[:12]))
            if ' CAPTURE_STOPPED ' in line and segment is not None:segment['end']=line[:12]
            if ' STATS ' in line:
                row=dict(re.findall(r'(\w+)=([^ ]+)',line));row['time']=line[:12]
                for key,value in list(row.items()):
                    if re.fullmatch(r'-?\d+',value):row[key]=int(value)
                stats.append(row)
                if segment is not None and 'end' not in segment:segment['stats'].append(row)
            if ' RX_8 motionevent3 ' in line:
                command=line.split(' RX_8 ',1)[1];touch.append(line[:12]+' '+command);words=command.split()
                if len(words)!=6:continue
                slot=int(words[3]);action=words[5]
                if action=='comm':continue
                ids[slot]+=1
                if action=='down':
                    if active and slot not in active:overlap.append({'time':line[:12],'newSlot':slot,'existingSlots':sorted(active)})
                    active.add(slot);maximum=max(maximum,len(active))
                elif action=='up':active.discard(slot)
                elif slot not in active:orphans+=1
            if segment is not None and any(tag in line for tag in ['ENCODER_FORMAT','CAPTURE_RESIZE','ROTATION_','USB_CLOSED','STALLED','ERROR','CAR_']):segment['events'].append(line)
        for s in segments:
            s['durationSeconds']=round(seconds(s.get('end',s['stats'][-1]['time'] if s['stats'] else s['start']))-seconds(s['start']),3)
            keys=['encoded','videoWritten','dropped','touchRx','touchDone','maxQueueMs','maxWriteMs','maxEncodeAgeMs']
            s['maxima']={k:max((r.get(k,0) for r in s['stats']),default=0) for k in keys}
        (OUT/(Path(name).stem+'-touch.txt')).write_text('\n'.join(touch)+'\n',encoding='utf-8-sig')
        row={'name':name,'segments':segments,'pointerIds':dict(ids),'maxSimultaneousPointers':maximum,'overlapStarts':overlap,'orphanMoves':orphans,
             'errors':[l for l in text.splitlines() if any(t in l for t in ['ERROR','STALLED','TOUCH_CANCELLED'])]}
        report['sessions'].append(row)
        print(json.dumps({'name':name,'segments':[{k:s[k] for k in ['start','durationSeconds','maxima']} for s in segments],
            'pointerIds':dict(ids),'maxSimultaneousPointers':maximum,'overlapStarts':len(overlap),'orphanMoves':orphans,'errors':row['errors']}),flush=True)
    for name,data in selected.items():
        if not name.endswith('.h264'):continue
        stamp=int(name.split('-')[1].split('.')[0]);dt=datetime.datetime.fromtimestamp(stamp/1000,datetime.timezone(datetime.timedelta(hours=8)))
        moment=dt.hour*3600+dt.minute*60+dt.second+dt.microsecond/1e6
        if not any(0<=start-moment<3 for start in capture_times):continue
        (dest/name).write_bytes(data);report['captures'].append({'name':name,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()})
    photos=[{'file':'IMG_20261024_131035.jpg','phoneMs':15252,'carMs':15136},
            {'file':'IMG_20261024_131907.jpg','phoneMs':16260,'carMs':16085},
            {'file':'IMG_20261024_132328.jpg','phoneMs':128448,'carMs':128398}]
    for p in photos:p['displayedDifferenceMs']=p['phoneMs']-p['carMs'];p['sha256']=hashlib.sha256((ROOT.parent/p['file']).read_bytes()).hexdigest()
    report['photoReadings']=photos
    (OUT/'car-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8-sig')

if __name__=='__main__':main()
