"""Read the new car export without altering its bytes or exposing device IDs."""
from pathlib import Path
import collections, hashlib, json, re, zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'analysis/car-20261024'
SOURCE = ROOT.parent / 'OpenSAICLink-E2-1792809060649.zip'

def main():
    OUT.mkdir(exist_ok=True)
    dest = OUT / 'extracted'
    dest.mkdir(exist_ok=True)
    result = {'source': SOURCE.name, 'sha256': hashlib.sha256(SOURCE.read_bytes()).hexdigest(), 'sessions': [], 'captures': []}
    with zipfile.ZipFile(SOURCE) as z:
        assert z.testzip() is None
        for item in z.infolist():
            if Path(item.filename).name != item.filename:
                raise ValueError('Unexpected ZIP member path')
            data = z.read(item.filename)
            if item.filename.endswith('.h264'):
                (dest / item.filename).write_bytes(data)
                result['captures'].append({'name': item.filename, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()})
                continue
            lines = data.decode('utf-8-sig').splitlines()
            (dest / item.filename).write_text('\n'.join(lines)+'\n', encoding='utf-8-sig')
            stats, touch, methods = [], [], collections.Counter()
            for line in lines:
                if ' STATS ' in line:
                    row = dict(re.findall(r'(\w+)=([^ ]+)', line))
                    row['time'] = line[:12]
                    for key in ['encoded', 'videoWritten', 'videoPackets', 'bytesWritten', 'dropped']:
                        row[key] = int(row.get(key, 0))
                    stats.append(row)
                if ' RX_8 motionevent' in line:
                    touch.append(line.split(' RX_8 ', 1)[1])
                if ' RX_9 ' in line:
                    methods[json.loads(line.split(' RX_9 ', 1)[1])['command']['method']] += 1
            (OUT / (Path(item.filename).stem + '-touch.txt')).write_text('\n'.join(touch)+'\n', encoding='utf-8-sig')
            active = [r for r in stats if r.get('capture')=='true' and r.get('pause')=='false']
            record = {'name': item.filename, 'stats': stats, 'methods': dict(methods), 'touchCount': len(touch),
                'touchForms': dict(collections.Counter(' '.join(t.split()[3:]) for t in touch)),
                'maxEncodedMinusWrittenWhileActive': max((r['encoded']-r['videoWritten'] for r in active), default=0),
                'maxDropped': max((r['dropped'] for r in stats), default=0),
                'events': [l for l in lines if any(s in l for s in ['CAPTURE_', 'ENCODER_', 'STALLED', 'SERVICE_STOPPED', 'USB_CLOSED', 'HU_SOURCE'])]}
            result['sessions'].append(record)
            print(json.dumps({k: record[k] for k in ['name','touchCount','touchForms','maxEncodedMinusWrittenWhileActive','maxDropped','events']}))
    (OUT / 'car-results.json').write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf-8-sig')

if __name__ == '__main__':
    main()
