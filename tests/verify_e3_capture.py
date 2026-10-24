"""Decode the final emulator export and replay its actual Java wire through A7 ARM."""
from pathlib import Path
import hashlib,json,re,struct,subprocess,sys,zipfile
import cv2
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'evidence/e3'
sys.path.insert(0,str(ROOT/'analysis/e2-protocol-review-20261023'))
from native_review import NativeAudit,wire_packets
JAVA=Path(r'E:\Soft\Dev\jetbrains\jdk\jdk-17\bin')

def main():
    with zipfile.ZipFile(OUT/'emulator-diagnostic-E3.zip') as z:
        assert z.testzip() is None
        name=max(n for n in z.namelist() if n.endswith('.h264'))
        data=z.read(name)
        (OUT/'emulator-sample.h264').write_bytes(data)
        assert any(b'E3/0.3.0' in z.read(n) for n in z.namelist() if n.endswith('.txt'))
    cap=cv2.VideoCapture(str(OUT/'emulator-sample.h264'))
    dimensions=None;count=0;first=None;last=None
    while True:
        ok,frame=cap.read()
        if not ok:break
        if first is None:first=frame.copy()
        last=frame;count+=1;dimensions=[frame.shape[1],frame.shape[0]]
    cap.release();assert count>150 and dimensions==[1280,720]
    cv2.imwrite(str(OUT/'decoded-first.png'),first);cv2.imwrite(str(OUT/'decoded-last.png'),last)
    cap=cv2.VideoCapture(str(OUT/'emulator-sample.h264'));assert cap.set(cv2.CAP_PROP_FORMAT,-1)
    packets=[]
    while True:
        ok,raw=cap.read()
        if not ok:break
        packets.append(raw.tobytes())
    cap.release()
    assert len(packets)==count and b''.join(packets)==data,'Raw demux must preserve source bytes'
    aus=OUT/'emulator-sample.aus';wire=OUT/'emulator-sample.wl'
    aus.write_bytes(b''.join(struct.pack('>I',len(p))+p for p in packets))
    subprocess.run([str(JAVA/'javac.exe'),'-encoding','UTF-8','-cp',str(ROOT/'build/e3/tests'),'-d',str(ROOT/'build/e3/tests'),str(ROOT/'tests/SequencePacketizer.java')],check=True)
    subprocess.run([str(JAVA/'java.exe'),'-cp',str(ROOT/'build/e3/tests'),'org.opensaiclink.trial.SequencePacketizer',str(aus),str(wire)],check=True)
    native=NativeAudit();wire_count=0
    for packet in wire_packets(wire):native.submit(packet);wire_count+=1
    expected=[b''.join(b'\0\0\0\1'+nal for nal in re.split(b'\x00\x00\x00\x01|\x00\x00\x01',p) if nal) for p in packets]
    assert len(native.images)==count
    assert all(image['payload']==p for image,p in zip(native.images,expected))
    report={'decodedFrames':count,'dimensions':dimensions,'sampleSha256':hashlib.sha256(data).hexdigest(),
        'actualJavaWirePackets':wire_count,'nativeImageCallbacks':len(native.images),
        'nativeKeyframes':sum(i['frameType']==1 for i in native.images),'allReconstructedBytesEqual':True,
        'limit':'Local emulator video and original ARM parser only; no real USB, A7 hardware decode or end-to-end latency measurement.'}
    (OUT/'capture-validation.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8-sig')
    print(json.dumps(report),flush=True)

if __name__=='__main__':main()
