#!/usr/bin/env python3
"""Generate SPD v1 after signing. No APK repack/resign. Python standard library only.
Usage: python3 tools/ota/make_delta.py BASE.apk TARGET.apk OUTPUT.spdelta
Round-trip and signature verification are required before release publication.
"""
import hashlib, json, os, struct, sys, tempfile, zipfile, zlib
from pathlib import Path
MAX=512*1024*1024

def sha(path):
    h=hashlib.sha256()
    with open(path,'rb') as f:
        for b in iter(lambda:f.read(65536),b''):h.update(b)
    return h.hexdigest()

def span(f,entry):
    f.seek(entry.header_offset+26);n,e=struct.unpack('<HH',f.read(4))
    return entry.header_offset+30+n+e,entry.compress_size

def digest_range(f,offset,length):
    h=hashlib.sha256();f.seek(offset)
    while length:
        b=f.read(min(length,65536))
        if not b:raise ValueError('Truncated APK range')
        h.update(b);length-=len(b)
    return h.digest()

def produce(base,target,output):
    bs,ts=os.path.getsize(base),os.path.getsize(target)
    if not 0<bs<=MAX or not 0<ts<=MAX:raise ValueError('APK size out of bounds')
    if Path(output).resolve() in (Path(base).resolve(),Path(target).resolve()):raise ValueError('Output overwrites input')
    bh,th=sha(base),sha(target);ops=[];pos=0
    with zipfile.ZipFile(base) as za,zipfile.ZipFile(target) as zb,open(base,'rb') as a,open(target,'rb') as b:
        old={i.filename:i for i in za.infolist()}
        for i in sorted(zb.infolist(),key=lambda x:x.header_offset):
            start,n=span(b,i)
            if start<pos or start+n>ts:raise ValueError('Overlapping or invalid target ZIP')
            if start>pos:ops.append((2,pos,start-pos))
            j=old.get(i.filename);copied=False
            if j:
                off,on=span(a,j)
                if n and on==n and off+on<=bs and digest_range(a,off,on)==digest_range(b,start,n):
                    ops.append((1,off,n));copied=True
            if n and not copied:ops.append((2,start,n))
            pos=start+n
        if pos<ts:ops.append((2,pos,ts-pos))
        if not 0<len(ops)<=100000:raise ValueError('Operation count out of bounds')
        tmp=str(output)+'.tmp'
        try:
            with open(tmp,'wb') as out:
                out.write(b'SPDELTA\x01'+struct.pack('>QQ',bs,ts)+bytes.fromhex(bh)+bytes.fromhex(th)+struct.pack('>I',len(ops)))
                compressor=zlib.compressobj(9)
                for op,off,n in ops:
                    out.write(compressor.compress(bytes([op])+(struct.pack('>Q',off) if op==1 else b'')+struct.pack('>Q',n)))
                    if op==2:
                        b.seek(off);left=n
                        while left:
                            part=b.read(min(left,65536))
                            if not part:raise ValueError('Truncated target')
                            out.write(compressor.compress(part));left-=len(part)
                out.write(compressor.flush());out.flush();os.fsync(out.fileno())
            os.replace(tmp,output)
        finally:
            if os.path.exists(tmp):os.unlink(tmp)
    return {'format':'spdelta-1','base_size':bs,'base_sha256':bh,'target_size':ts,'target_sha256':th,'patch_size':os.path.getsize(output),'patch_sha256':sha(output),'operations':len(ops)}

if __name__=='__main__':
    if len(sys.argv)!=4:raise SystemExit(__doc__)
    print(json.dumps(produce(*sys.argv[1:]),indent=2))
