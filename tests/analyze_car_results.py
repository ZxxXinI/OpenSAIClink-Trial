"""Read the three car exports without altering originals; deduplicate snapshots."""
from pathlib import Path
import collections
import datetime
import hashlib
import json
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'analysis/car-20261023'


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8-sig')


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    exports = sorted(ROOT.parent.glob('OpenSAICLink-E1-*.zip'))
    members = {}
    snapshots = collections.defaultdict(list)
    packages = []
    for path in exports:
        with zipfile.ZipFile(path) as archive:
            assert archive.testzip() is None
            records = []
            for entry in archive.infolist():
                if entry.is_dir():
                    continue
                if Path(entry.filename).name != entry.filename:
                    raise ValueError('Unexpected member path')
                data = archive.read(entry.filename)
                record = {'export': path.name, 'name': entry.filename, 'bytes': len(data),
                          'sha256': hashlib.sha256(data).hexdigest()}
                snapshots[entry.filename].append(record)
                records.append(record)
                if entry.filename not in members or len(data) > len(members[entry.filename]):
                    members[entry.filename] = data
            packages.append({'name': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                             'entries': records})
    extracted = OUT/'deduplicated'
    extracted.mkdir(exist_ok=True)
    sessions = []
    starts = []
    for name, data in members.items():
        if not name.endswith('.txt'):
            continue
        text = data.decode('utf-8-sig')
        (extracted/name).write_text(text, encoding='utf-8-sig')
        lines = text.splitlines()
        stats = []
        methods = collections.Counter()
        for line in lines:
            if ' STATS ' in line:
                row = dict(re.findall(r'(\w+)=([^ ]+)', line))
                row['time'] = line[:12]
                for key in ('encoded', 'videoWritten', 'bytesWritten', 'dropped'):
                    row[key] = int(row.get(key, 0))
                stats.append(row)
            match = re.search(r'FIRST_ENCODED_FRAME bytes=(\d+)', line)
            if match:
                starts.append({'time': line[:12], 'bytes': int(match[1]), 'session': name,
                               'car': any('CAPTURE_STARTED 1280x480' in s for s in lines)})
            if 'RX_9 ' in line:
                try:
                    obj = json.loads(line.split('RX_9 ', 1)[1])
                    methods[obj.get('command', {}).get('method', '')] += 1
                except ValueError:
                    pass
        sessions.append({'name': name, 'lines': len(lines), 'snapshots': len(snapshots[name]),
                         'usbOpen': any(' USB_OPEN ' in s for s in lines),
                         'configReceived': any('RX_8 config ' in s for s in lines),
                         'captureStarted': any(' CAPTURE_STARTED ' in s for s in lines),
                         'methodsReceived': dict(methods), 'firstStats': stats[0] if stats else None,
                         'lastStats': stats[-1] if stats else None,
                         'stalledAfterVideo': bool(len(stats)>1 and stats[-1]['encoded']>100
                             and stats[-1]['videoWritten']>0 and stats[-1]['videoWritten']==stats[-2]['videoWritten']),
                         'errors': [s for s in lines if any(k in s for k in ('ERROR', 'USB_CLOSED', 'AUTH_RESULT'))]})
    fixtures = []
    fixture_dir = OUT/'fixtures'
    fixture_dir.mkdir(exist_ok=True)
    for name, data in members.items():
        if not name.endswith('.h264'):
            continue
        (extracted/name).write_bytes(data)
        millis = int(re.search(r'capture-(\d+)', name)[1])
        moment = datetime.datetime.fromtimestamp(millis/1000, datetime.timezone(datetime.timedelta(hours=8)))
        base_seconds = moment.hour*3600 + moment.minute*60 + moment.second + moment.microsecond/1e6
        matches = []
        for start in starts:
            h,m,s = start['time'].split(':')
            delta = int(h)*3600 + int(m)*60 + float(s) - base_seconds
            if 0 <= delta < 2:
                matches.append((delta, start))
        if not matches:
            continue
        start = min(matches, key=lambda x: x[0])[1]
        first = data[:start['bytes']]
        nals = [x for x in re.split(b'\x00\x00\x00\x01|\x00\x00\x01', first) if x]
        target = fixture_dir/(name.replace('.h264', '-first-au.bin'))
        target.write_bytes(first)
        decoded = {'name': name, 'bytes': len(data), 'car': start['car'], 'firstAuBytes': len(first),
                   'firstAuNalTypes': [x[0]&31 for x in nals],
                   'firstAuPath': str(target.relative_to(ROOT)), 'sampleSha256': hashlib.sha256(data).hexdigest()}
        fixtures.append(decoded)
    report = {'exports': packages, 'uniqueSessions': sessions, 'captures': fixtures,
              'note': 'Exports are cumulative snapshots; identical session names are not independent tests.'}
    write_json(OUT/'car-results.json', report)
    for session in sessions:
        print(json.dumps({k:session[k] for k in ('name','configReceived','captureStarted','lastStats','stalledAfterVideo')}))
    print('First access units:', json.dumps(fixtures))


if __name__ == '__main__':
    main()
