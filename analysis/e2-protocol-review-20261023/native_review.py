"""Audit original A7 ARM parser functions; device, socket and display IO are stubbed."""
from pathlib import Path
import hashlib
import json
import struct
import sys

ROOT=Path(__file__).resolve().parents[2]
OUT=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT/'tests'))
from replay_hu_decoder import A7Parser
from unicorn.arm_const import UC_ARM_REG_R0,UC_ARM_REG_R1,UC_ARM_REG_R2,UC_ARM_REG_SP,UC_ARM_REG_LR,UC_ARM_REG_PC


class NativeAudit(A7Parser):
    CALLBACK=A7Parser.STOP+0x100

    def __init__(self):
        self.stream=b'';self.position=0;self.read_sizes=[];self.packets=[]
        self.allocations={};self.free_blocks=[]
        super().__init__()

    def hook(self,uc,address,size,data):
        if address==0x6f510:  # bounded reusable allocation, unlike the first-packet harness
            count=uc.reg_read(UC_ARM_REG_R0);assert 0<count<0x100000
            aligned=(count+15)&~15;pointer=None
            for i,(start,length) in enumerate(self.free_blocks):
                if length>=aligned:
                    pointer=start;self.free_blocks.pop(i)
                    if length>aligned:self.free_blocks.append((start+aligned,length-aligned))
                    break
            if pointer is None:
                pointer=self.heap;self.heap+=aligned
                assert self.heap<self.HEAP+0x400000
            self.allocations[pointer]=aligned
            uc.mem_write(pointer,b'\0'*aligned);self.ret(pointer)
        elif address==0x6f5c4:
            pointer=uc.reg_read(UC_ARM_REG_R0)
            if pointer:
                length=self.allocations.pop(pointer)
                self.free_blocks.append((pointer,length));self.free_blocks.sort()
                merged=[]
                for start,length in self.free_blocks:
                    if merged and merged[-1][0]+merged[-1][1]==start:
                        merged[-1]=(merged[-1][0],merged[-1][1]+length)
                    else:merged.append((start,length))
                self.free_blocks=merged
            self.ret(0)
        elif address==0x15314:  # ntohl PLT
            value=uc.reg_read(UC_ARM_REG_R0)
            self.ret(int.from_bytes(value.to_bytes(4,'little'),'big'))
        elif address in (0x68638,0x6883c,0x78ac0,0x78af0):
            self.ret(0)  # event, delay, socket cleanup
        elif address==0x7a904:  # exact-read helper: no real sockets
            destination=uc.reg_read(UC_ARM_REG_R1);count=uc.reg_read(UC_ARM_REG_R2)
            assert self.position+count<=len(self.stream)
            uc.mem_write(destination,self.stream[self.position:self.position+count])
            self.position+=count;self.read_sizes.append(count);self.ret(0)
        elif address==self.CALLBACK:
            pointer=uc.reg_read(UC_ARM_REG_R0);count=uc.reg_read(UC_ARM_REG_R1)
            self.packets.append(bytes(uc.mem_read(pointer,count)))
            uc.mem_write(self.INFO+0xb8,struct.pack('<I',0));self.ret(0)
        else:super().hook(uc,address,size,data)

    def call(self,entry,*args):
        self.uc.reg_write(UC_ARM_REG_SP,self.STACK+0x1fff0)
        self.uc.reg_write(UC_ARM_REG_LR,self.STOP|1)
        for register,value in zip((UC_ARM_REG_R0,UC_ARM_REG_R1,UC_ARM_REG_R2),args):self.uc.reg_write(register,value)
        self.uc.emu_start(entry|1,self.STOP,timeout=2000000,count=200000)
        assert self.uc.reg_read(UC_ARM_REG_PC)==self.STOP
        return self.uc.reg_read(UC_ARM_REG_R0)

    def header_valid(self,header):
        self.uc.mem_write(self.INPUT,header)
        return self.call(0x7a610,self.INPUT)

    def socket_frame(self,wire):
        self.stream=wire;self.position=0;self.read_sizes=[];self.packets=[]
        self.uc.mem_write(self.INFO+0xb8,struct.pack('<I',1))
        self.uc.mem_write(self.INFO+0x2c,struct.pack('<I',self.CALLBACK|1))
        self.call(0x7aa78,self.INFO)
        assert self.position==len(wire) and self.packets==[wire]
        return self.read_sizes


def wire_packets(path):
    raw=path.read_bytes();p=0
    while p<len(raw):
        assert raw[p:p+4]==b'WL\x06\0'
        n=struct.unpack_from('>I',raw,p+4)[0]
        assert n>0 and p+8+n<=len(raw)
        yield raw[p+8:p+8+n]
        p+=8+n
    assert p==len(raw)


def access_units(path):
    raw=path.read_bytes();p=0
    while p<len(raw):
        n=struct.unpack_from('>I',raw,p)[0];p+=4
        assert p+n<=len(raw)
        yield raw[p:p+n];p+=n


def main():
    report={'boundary':'Original ARM CPU functions only; no real USB, hardware decoder or display.'}
    native=NativeAudit()
    report['headerLimits']={}
    for length in (0,23,1536000,1536001,4194304):
        result=native.header_valid(b'WL\x06\0'+struct.pack('>I',length))
        assert result==int(length<=1536000)
        report['headerLimits'][str(length)]=bool(result)
    extended=(OUT/'extended-frame.wl').read_bytes()
    report['nativeExtendedFrameReadSizes']=native.socket_frame(extended)
    assert report['nativeExtendedFrameReadSizes']==[8,2,3]
    report['normalJsonWire']={}
    for name in ('onMuReady','responseWeLink','onHeartbeat','onResumeWelink'):
        wire=(OUT/(name+'.wl')).read_bytes()
        report['normalJsonWire'][name]=native.socket_frame(wire)
    report['fullCaptures']=[]
    inventory=json.loads((OUT/'capture-au-inventory.json').read_text(encoding='utf-8-sig'))
    for capture in inventory:
        receiver=NativeAudit();name=capture['name'];max_payload=0;count=0
        for payload in wire_packets(OUT/(name+'.wl')):
            receiver.submit(payload);max_payload=max(max_payload,len(payload));count+=1
        expected=list(access_units(OUT/(name+'.aus')))
        assert len(receiver.images)==len(expected)==capture['units']
        assert all(img['payload']==au for img,au in zip(receiver.images,expected))
        keyframes=sum(img['frameType']==1 for img in receiver.images)
        assert keyframes==capture['nalCounts']['5']
        report['fullCaptures'].append({'name':name,'accessUnits':len(expected),'wirePackets':count,
                                     'imageCallbacks':len(receiver.images),'keyframes':keyframes,
                                     'allPayloadsEqual':True,'maxPayload':max_payload})
        print(json.dumps(report['fullCaptures'][-1]),flush=True)
    prefixed=NativeAudit()
    for payload in wire_packets(OUT/'aud-sei-idr.wl'):prefixed.submit(payload)
    assert len(prefixed.images)==0
    report['audSeiIdrImageCallbacks']=len(prefixed.images)
    report['firmwareSha256']=hashlib.sha256((ROOT/'analysis/car-20261023/firmware/SaicLink').read_bytes()).hexdigest()
    (OUT/'native-results.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8-sig')
    print(json.dumps(report),flush=True)


if __name__=='__main__':main()
