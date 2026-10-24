"""Decode E4 real samples and E5 fit/direct samples; replay E5 wire through A7 ARM."""
from pathlib import Path
import hashlib,json,re,struct,subprocess,sys
import cv2,numpy as np
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'evidence/e5'
sys.path.insert(0,str(ROOT/'analysis/e2-protocol-review-20261023'))
from native_review import NativeAudit,wire_packets
JAVA=Path(r'E:\Soft\Dev\jetbrains\jdk\jdk-17\bin')

def decode(path,save=None):
 cap=cv2.VideoCapture(str(path));count=0;best=None;best_width=-1;bounds=None;dimensions=None
 while True:
  ok,frame=cap.read()
  if not ok:break
  count+=1;dimensions=[frame.shape[1],frame.shape[0]]
  # The dynamic test has four bright color blocks through the middle of the image.
  line=frame[int(frame.shape[0]*.60)].astype(int)
  mask=(line.max(axis=1)>140)&((line.max(axis=1)-line.min(axis=1))>90)
  xs=np.where(mask)[0]
  if len(xs)>700 and len(xs)>best_width:
   best_width=len(xs);best=frame.copy();bounds=[int(xs.min()),int(xs.max())]
 cap.release();assert count>150 and dimensions==[1280,480],(path.name,count,dimensions)
 if save and best is not None:cv2.imwrite(str(save),best)
 return {'file':path.name,'decodedFrames':count,'dimensions':dimensions,'sampleSha256':hashlib.sha256(path.read_bytes()).hexdigest(),'patternColoredWidth':best_width,'patternBounds':bounds}

def replay(path):
 cap=cv2.VideoCapture(str(path));assert cap.set(cv2.CAP_PROP_FORMAT,-1);packets=[]
 while True:
  ok,raw=cap.read()
  if not ok:break
  packets.append(raw.tobytes())
 cap.release();assert b''.join(packets)==path.read_bytes()
 aus=path.with_suffix('.aus');wire=path.with_suffix('.wl');aus.write_bytes(b''.join(struct.pack('>I',len(p))+p for p in packets))
 subprocess.run([str(JAVA/'java.exe'),'-cp',str(ROOT/'build/e5/tests'),'org.opensaiclink.trial.SequencePacketizer',str(aus),str(wire)],check=True)
 native=NativeAudit();wire_count=0
 for packet in wire_packets(wire):native.submit(packet);wire_count+=1
 expected=[b''.join(b'\0\0\0\1'+nal for nal in re.split(b'\x00\x00\x00\x01|\x00\x00\x01',p) if nal) for p in packets]
 assert len(native.images)==len(packets) and all(image['payload']==p for image,p in zip(native.images,expected))
 return {'actualJavaWirePackets':wire_count,'nativeImageCallbacks':len(native.images),'nativeKeyframes':sum(i['frameType']==1 for i in native.images),'allReconstructedBytesEqual':True}

def main():
 subprocess.run([str(JAVA/'javac.exe'),'-encoding','UTF-8','-cp',str(ROOT/'build/e5/tests'),'-d',str(ROOT/'build/e5/tests'),str(ROOT/'tests/SequencePacketizer.java')],check=True)
 results=[]
 for mode in ['gpu','direct']:
  path=OUT/(mode+'-sample.h264');row=decode(path,OUT/(mode+'-decoded-pattern.png'));row.update(replay(path));row['mode']=mode;results.append(row);print(json.dumps(row),flush=True)
 assert results[0]['patternColoredWidth']>1230
 assert 1030<results[1]['patternColoredWidth']<1100
 car=[decode(path) for path in sorted((ROOT/'analysis/e4-car-20261024/extracted').glob('*.h264'))]
 report={'e5':results,'e4RealSamples':car,'limitation':'Local decoded video and ARM parser only; no new physical USB/display/latency test.'}
 (OUT/'capture-validation.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8-sig');print(json.dumps(report),flush=True)
if __name__=='__main__':main()
