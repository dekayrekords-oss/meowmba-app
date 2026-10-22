#!/usr/bin/env python3
"""Lossless APK ZIP-payload reuse over slow links. SHA-256 verifies every reuse and result.
manifest BASEAPK MANIFEST; pack NEWAPK MANIFEST PATCH; apply BASEAPK PATCH OUTPUT.
The patch is a gzip JSON envelope and literals; APK signing bytes are preserved exactly.
"""
import sys,json,zipfile,struct,hashlib,gzip,base64,pathlib,tempfile,os

def digest(b): return hashlib.sha256(b).hexdigest()
def entries(path):
 with open(path,'rb') as f,zipfile.ZipFile(path) as z:
  for e in z.infolist():
   f.seek(e.header_offset);h=f.read(30)
   if h[:4]!=b'PK\x03\x04':raise ValueError('bad local header')
   n,x=struct.unpack_from('<HH',h,26);offset=e.header_offset+30+n+x
   f.seek(offset);data=f.read(e.compress_size)
   yield e.filename,{'offset':offset,'size':len(data),'sha256':digest(data)}

def main(a):
 mode=a[0]
 if mode=='manifest':
  p=pathlib.Path(a[1]);b=p.read_bytes();pathlib.Path(a[2]).write_text(json.dumps({'sha256':digest(b),'size':len(b),'entries':dict(entries(p))}));return
 if mode=='pack':
  b=pathlib.Path(a[1]).read_bytes();m=json.loads(pathlib.Path(a[2]).read_text());parts=[];pos=0;reused=0
  def literal(data):
   if data:parts.append({'data':base64.b64encode(data).decode()})
  for name,e in sorted(entries(a[1]),key=lambda v:v[1]['offset']):
   start=e['offset'];end=start+e['size'];literal(b[pos:start]);old=m['entries'].get(name)
   if old and old['size']==e['size'] and old['sha256']==e['sha256']:
    parts.append({'copy':old['offset'],'size':old['size'],'sha256':old['sha256']});reused+=old['size']
   else:literal(b[start:end])
   pos=end
  literal(b[pos:]);patch={'base':m['sha256'],'target':digest(b),'size':len(b),'parts':parts}
  with gzip.open(a[3],'wb',compresslevel=9) as f:f.write(json.dumps(patch,separators=(',',':')).encode())
  print(json.dumps({'target':patch['target'],'reused':reused,'literal':len(b)-reused,'patchBytes':pathlib.Path(a[3]).stat().st_size}));return
 if mode=='apply':
  with gzip.open(a[2],'rb') as f:p=json.load(f)
  output=pathlib.Path(a[3]);temporary=None
  try:
   with open(a[1],'rb') as source:
    if hashlib.file_digest(source,'sha256').hexdigest()!=p['base']:raise ValueError('wrong base APK')
    source.seek(0,2);base_size=source.tell()
    # Stream payload reuse: holding two ~450MB APKs in RAM forced swap on the
    # build Mac. A sibling temporary file also prevents a failed check leaving
    # an incomplete package at the requested output path.
    with tempfile.NamedTemporaryFile(dir=output.parent,prefix=output.name+'.',suffix='.partial',delete=False) as target:
     temporary=pathlib.Path(target.name);whole=hashlib.sha256();written=0
     for part in p['parts']:
      if 'copy' in part:
       off=part['copy'];size=part['size']
       if off<0 or size<0 or off+size>base_size:raise ValueError('invalid copy range')
       source.seek(off);remaining=size;copied=hashlib.sha256()
       while remaining:
        data=source.read(min(1024*1024,remaining))
        if not data:raise ValueError('incomplete base APK')
        target.write(data);whole.update(data);copied.update(data);written+=len(data);remaining-=len(data)
       if copied.hexdigest()!=part['sha256']:raise ValueError('copy checksum mismatch')
      else:
       data=base64.b64decode(part['data'],validate=True)
       target.write(data);whole.update(data);written+=len(data)
     if written!=p['size'] or whole.hexdigest()!=p['target']:raise ValueError('result checksum mismatch')
   os.replace(temporary,output);temporary=None;print(p['target']);return
  finally:
   if temporary is not None:temporary.unlink(missing_ok=True)
 raise ValueError('expected manifest, pack or apply')
if __name__=='__main__':main(sys.argv[1:])
