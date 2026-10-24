"""Replay only A7 H.264 parser instructions with all external calls stubbed.

This does not boot firmware or emulate its hardware decoder, display, or USB.
"""
from pathlib import Path
import hashlib
import json
import re
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT/'tools/vendor'))
import unicorn
from unicorn import Uc, UC_ARCH_ARM, UC_MODE_THUMB, UC_HOOK_CODE
from unicorn.arm_const import UC_ARM_REG_R0, UC_ARM_REG_R1, UC_ARM_REG_R2, UC_ARM_REG_LR, UC_ARM_REG_PC, UC_ARM_REG_SP

OUT = ROOT/'analysis/car-20261023'
ELF = OUT/'firmware/SaicLink'
ENTRY = 0x73241
STUBS = {'info':0x69ee0, 'log':0x68a34, 'errno':0x683b4, 'malloc':0x6f510,
         'free':0x6f5c4, 'memcpy':0x15644, 'imageCallback':0x731b8}
EXPECTED_ELF_SHA256='835f2d3a6485c52111c5360c34ef665bcf2b1a26969cd7cea8747fc8f2275ced'


class A7Parser:
    STACK=0x4000000
    INPUT=0x5000000
    HEAP=0x6000000
    INFO=0x7000000
    STOP=0x3000000

    def __init__(self):
        self.uc = Uc(UC_ARCH_ARM, UC_MODE_THUMB)
        raw = ELF.read_bytes()
        assert hashlib.sha256(raw).hexdigest()==EXPECTED_ELF_SHA256, 'Addresses belong to one audited firmware only'
        header = struct.unpack_from('<16sHHIIIIIHHHHHH', raw)
        assert header[0][:6] == b'\x7fELF\x01\x01' and header[2] == 40
        segments = []
        for i in range(header[10]):
            row=struct.unpack_from('<IIIIIIII',raw,header[5]+i*header[9])
            if row[0]==1:segments.append(row)
        lo=min(s[2] for s in segments)&~4095
        hi=(max(s[2]+s[5] for s in segments)+4095)&~4095
        self.uc.mem_map(lo,hi-lo)
        for _,offset,addr,_,size,_,_,_ in segments:
            self.uc.mem_write(addr,raw[offset:offset+size])
        for address,size in ((self.STACK,0x20000),(self.INPUT,0x400000),
                             (self.HEAP,0x400000),(self.INFO,0x2000),(self.STOP,0x1000)):
            self.uc.mem_map(address,size)
        self.uc.mem_write(self.INFO+0x858,struct.pack('<II',1280,480))
        self.heap=self.HEAP
        self.images=[]
        self.calls=[]
        self.uc.hook_add(UC_HOOK_CODE,self.hook)

    def ret(self,value=0):
        self.uc.reg_write(UC_ARM_REG_R0,value)
        self.uc.reg_write(UC_ARM_REG_PC,self.uc.reg_read(UC_ARM_REG_LR))

    def hook(self,uc,address,size,data):
        if address==self.STOP:
            uc.emu_stop();return
        if address==STUBS['info']:
            self.ret(self.INFO)
        elif address in (STUBS['log'],STUBS['errno'],STUBS['free']):
            self.ret(0)
        elif address==STUBS['malloc']:
            count=uc.reg_read(UC_ARM_REG_R0)
            assert 0<count<0x100000
            pointer=self.heap;self.heap+=(count+15)&~15
            assert self.heap<self.HEAP+0x400000
            self.ret(pointer)
        elif address==STUBS['memcpy']:
            destination=uc.reg_read(UC_ARM_REG_R0);source=uc.reg_read(UC_ARM_REG_R1)
            count=uc.reg_read(UC_ARM_REG_R2);assert count<0x100000
            uc.mem_write(destination,bytes(uc.mem_read(source,count)));self.ret(destination)
        elif address==STUBS['imageCallback']:
            info=uc.reg_read(UC_ARM_REG_R0)
            image_type,offset,width,height,count,pointer,frame_type=struct.unpack('<7I',uc.mem_read(info,28))
            self.images.append({'frameType':frame_type,'width':width,'height':height,
                                'payload':bytes(uc.mem_read(pointer,count))})
            self.ret(0)

    def submit(self,payload):
        assert payload and len(payload)<0x100000
        self.uc.mem_write(self.INPUT,payload)
        self.uc.reg_write(UC_ARM_REG_SP,self.STACK+0x1fff0)
        self.uc.reg_write(UC_ARM_REG_LR,self.STOP|1)
        self.uc.reg_write(UC_ARM_REG_R0,self.INPUT)
        self.uc.reg_write(UC_ARM_REG_R1,len(payload))
        self.uc.emu_start(ENTRY,self.STOP,timeout=2000000,count=200000)
        assert self.uc.reg_read(UC_ARM_REG_PC)==self.STOP


def reference_packets(access_unit):
    units=[x for x in re.split(b'\x00\x00\x00\x01|\x00\x00\x01',access_unit) if x]
    params=[b'\x00\x00\x00\x01'+x for x in units if x[0]&31 in (7,8)]
    picture=b''.join(b'\x00\x00\x00\x01'+x for x in units if x[0]&31 not in (7,8))
    return params+([picture] if picture else [])


def main():
    facts=json.loads((OUT/'car-results.json').read_text(encoding='utf-8-sig'))
    results=[]
    for capture in facts['captures']:
        if not capture['car']:continue
        first=(ROOT/capture['firstAuPath']).read_bytes()
        old=A7Parser();old.submit(first)
        fixed=A7Parser()
        java_wire=(ROOT/capture['firstAuPath']).with_suffix('.e2.wl')
        if '--require-java' in sys.argv and not java_wire.exists():
            raise AssertionError('Generate fixture with actual Java packetizer first')
        packets=[]
        if java_wire.exists():
            wire=java_wire.read_bytes();offset=0
            while offset<len(wire):
                assert wire[offset:offset+4]==b'WL\x06\x00'
                count=struct.unpack_from('>I',wire,offset+4)[0]
                assert offset+8+count<=len(wire)
                packets.append(wire[offset+8:offset+8+count]);offset+=8+count
        else:packets=reference_packets(first)
        for payload in packets:fixed.submit(payload)
        assert len(old.images)==0, 'Recorded E1 packet should reproduce missing IDR callback'
        assert len(fixed.images)==1 and fixed.images[0]['frameType']==1
        assert fixed.images[0]['payload']==first, 'Receiver must reconstruct the same original AVC bytes'
        results.append({'capture':capture['name'],'e1FirstPacketNalTypes':capture['firstAuNalTypes'],
                        'e1ImageCallbacks':len(old.images),'splitPacketSizes':[len(x) for x in packets],
                        'splitImageCallbacks':len(fixed.images),'splitImageType':fixed.images[0]['frameType'],
                        'reconstructedBytesEqualOriginal':True,'actualJavaWire':java_wire.exists()})
    assert results
    report={'unicornVersion':unicorn.__version__,'firmwareSha256':hashlib.sha256(ELF.read_bytes()).hexdigest(),
            'entryAddress':hex(ENTRY),'stubbedCalls':{k:hex(v) for k,v in STUBS.items()},
            'results':results,'limitation':'CPU parser replay only; no USB or physical hardware decoding/display tested.'}
    (OUT/'hu-parser-replay.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8-sig')
    print(json.dumps(report))


if __name__=='__main__':main()
