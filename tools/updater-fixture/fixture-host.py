#!/usr/bin/env python3
"""Disposable emulator host only. No upload, remote publish or user device.
Usage: fixture-host.py build|device CHECKOUT FIXTURE_DIR
"""
import hashlib,json,os,re,shutil,struct,subprocess,sys,time,zipfile,zlib
from pathlib import Path
PIN='e290e8c945cc51d744445ebffb3cdf6c483eff17';PKG='app.spool.music.dev'
root=Path(sys.argv[2]).resolve();out=Path(sys.argv[3]).resolve();out.mkdir(parents=True,exist_ok=True)
here=Path(__file__).resolve().parent
sdk=Path(os.environ['ANDROID_HOME']);bt=sdk/'build-tools/35.0.0'

def run(args,timeout=120,cwd=None,check=True):
 # Every child gets a process group. Timeout stops its descendants, preserves
 # partial output/status and aborts the current case without retry.
 import signal
 process=subprocess.Popen([str(x) for x in args],cwd=cwd or root,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,start_new_session=True)
 try:
  output,_=process.communicate(timeout=timeout)
 except subprocess.TimeoutExpired as expired:
  try:os.killpg(process.pid,signal.SIGKILL)
  except ProcessLookupError:pass
  try:output,_=process.communicate(timeout=5)
  except subprocess.TimeoutExpired:
   output=expired.output or b''
   process.stdout.close()
  stamp=str(time.time_ns());(out/('timeout-'+stamp+'.log')).write_bytes(output)
  (out/('timeout-'+stamp+'.json')).write_text(json.dumps(dict(program=str(args[0]),timeout=timeout,status=124,reaped=process.poll() is not None)))
  raise RuntimeError('bounded process-group timeout; partial output preserved; stop remaining cases')
 result=subprocess.CompletedProcess(args,process.returncode,output)
 if check and result.returncode:raise RuntimeError(f'command {args[0]} status {result.returncode}: '+result.stdout.decode(errors='replace')[-3000:])
 return result

def sha(p):
 h=hashlib.sha256()
 with open(p,'rb') as f:
  for b in iter(lambda:f.read(65536),b''):h.update(b)
 return h.hexdigest()

def metadata(p,code,name):
 badging=run([bt/'aapt','dump','badging',p]).stdout.decode()
 (out/(p.stem+'-badging.txt')).write_text(badging)
 m=re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",badging);assert m and m.groups()==(PKG,str(code),name),badging[:300]
 assert re.search(r"^sdkVersion:'26'$",badging,re.M), 'minSDK not 26'
 abi=re.search(r'^native-code: (.+)$',badging,re.M);assert abi and set(re.findall(r"'([^']+)'",abi[1]))=={'x86_64'},'not x86_64-only'
 label=re.search(r"^application-label:'([^']+)'$",badging,re.M);assert label and label[1].strip()
 signer=run([bt/'apksigner','verify','--print-certs',p]).stdout.decode();(out/(p.stem+'-signer.txt')).write_text(signer)
 certs=re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)$',signer,re.M);assert len(certs)==1 and len(certs[0])==64
 return dict(package=PKG,code=code,name=name,minSdk=26,abis=['x86_64'],label=label[1],cert=certs[0],sha256=sha(p),bytes=p.stat().st_size)

def independent_patch(base,patch,target):
 # Independent stdlib decoder, not SpoolDelta.apply or the generator.
 p=patch.read_bytes();assert len(p)>92 and p[:8]==b'SPDELTA\1'
 bs,ts=struct.unpack('>QQ',p[8:24]);assert bs==base.stat().st_size and ts==target.stat().st_size
 assert p[24:56].hex()==sha(base) and p[56:88].hex()==sha(target)
 count=struct.unpack('>I',p[88:92])[0];assert 0<count<=100000
 d=zlib.decompressobj();data=d.decompress(p[92:],1024*1024*512+1);assert d.eof and not d.unused_data and not d.unconsumed_tail
 i=0;written=0;result=out/'independent-roundtrip.apk'
 with open(base,'rb') as b,open(result,'wb') as o:
  for _ in range(count):
   op=data[i];i+=1;assert op in (1,2)
   off=0
   if op==1:off=struct.unpack('>Q',data[i:i+8])[0];i+=8
   n=struct.unpack('>Q',data[i:i+8])[0];i+=8;assert 0<n<=ts-written
   if op==1:
    assert off+n<=bs;b.seek(off);value=b.read(n)
   else:value=data[i:i+n];i+=n
   assert len(value)==n;o.write(value);written+=n
 assert i==len(data) and written==ts and sha(result)==sha(target)
 result.unlink()
 return dict(bytes=len(p),sha256=sha(patch),baseSha256=sha(base),targetSha256=sha(target),roundtrip=True)

if sys.argv[1]=='build':
 assert run(['git','rev-parse','HEAD']).stdout.decode().strip()==PIN
 assert not run(['git','status','--porcelain']).stdout.strip(),'checkout must be clean before fixture overlay'
 test=root/'app/src/androidTest/java/com/music/bitchord/UpdateOneTapAndroidFixtureTest.kt';shutil.copyfile(here/test.name,test)
 common=['./gradlew','--init-script',here/'fixture-version.gradle','-Pbitchord.bundleWear=false','--no-daemon']
 # Two isolated builds, preserve target outside build/ before rebuilding current.
 for code,target in [(59,True),(58,False)]:
  args=common+(['-PspoolFixtureTarget=true'] if target else [])+[':app:assembleDevDebug']+([] if target else [':app:assembleDevDebugAndroidTest'])
  result=run(args,timeout=1800,check=False);(out/f'build-{code}.log').write_bytes(result.stdout);assert result.returncode==0,f'fixture{code} build failed, see preserved build log'
  apks=list((root/'app/build/outputs/apk/dev/debug').glob('*x86_64*.apk'));assert len(apks)==1,apks
  shutil.copyfile(apks[0],out/f'fixture{code}.apk')
 current=metadata(out/'fixture58.apk',58,'2.1.1');target=metadata(out/'fixture59.apk',59,'99.0.0');assert current['cert']==target['cert'] and current['label']==target['label']
 tests=list((root/'app/build/outputs/apk/androidTest/dev/debug').glob('*.apk'));assert len(tests)==1;shutil.copyfile(tests[0],out/'fixture-test.apk')
 r=run(['python3','tools/ota/make_delta.py',out/'fixture58.apk',out/'fixture59.apk',out/'fixture.spdelta']);(out/'patch-generator.json').write_bytes(r.stdout)
 patch=independent_patch(out/'fixture58.apk',out/'fixture.spdelta',out/'fixture59.apk')
 test_badging=run([bt/'aapt','dump','badging',out/'fixture-test.apk']).stdout.decode();(out/'instrumentation-badging.txt').write_text(test_badging)
 assert "package: name='app.spool.music.dev.test'" in test_badging
 (out/'fixture-metadata.json').write_text(json.dumps(dict(pin=PIN,current=current,target=target,patch=patch,testApkSha256=sha(out/'fixture-test.apk')),indent=2))
 print('FIXTURE BUILD PREPARED, no device run or artifact upload')

elif sys.argv[1]=='device':
 serial=os.environ.get('ANDROID_SERIAL','emulator-5554');assert re.fullmatch(r'emulator-\d+',serial),'disposable emulator serial required'
 adb=['adb','-s',serial]
 assert run(adb+['shell','getprop','ro.kernel.qemu']).stdout.strip()==b'1'
 assert run(adb+['shell','getprop','ro.product.cpu.abi']).stdout.strip()==b'x86_64'
 manifest=json.loads((out/'fixture-metadata.json').read_text());assert manifest['pin']==PIN
 assert metadata(out/'fixture58.apk',58,'2.1.1')==manifest['current']
 assert metadata(out/'fixture59.apk',59,'99.0.0')==manifest['target']
 independent_patch(out/'fixture58.apk',out/'fixture.spdelta',out/'fixture59.apk')
 cls='com.music.bitchord.UpdateOneTapAndroidFixtureTest';runner=PKG+'.test/androidx.test.runner.AndroidJUnitRunner'
 def adbtext(args,timeout=120):return run(adb+args,timeout).stdout.decode(errors='replace')
 def package_present(package):
  assert package in (PKG,PKG+'.test'),'unexpected package probe'
  assert adbtext(['get-state'],10).strip()=='device','emulator not online before package probe'
  result=run(adb+['shell','pm','path',package],30,check=False)
  output=result.stdout.decode(errors='strict')
  assert adbtext(['get-state'],10).strip()=='device','emulator lost during package probe'
  paths=output.splitlines()
  present=result.returncode==0 and bool(paths) and all(re.fullmatch(r'package:/[^\r\n]+',line) for line in paths)
  absent=result.returncode==1 and output==''
  (out/('package-probe-'+str(time.time_ns())+'.json')).write_text(json.dumps(dict(serial=serial,package=package,status=result.returncode,output=output,present=present,absent=absent)))
  assert present or absent,'unexpected package probe status/output'
  return present
 def install_current():
  if package_present(PKG):adbtext(['uninstall',PKG],30)
  adbtext(['install','-r',out/'fixture58.apk'],120);adbtext(['install','-r',out/'fixture-test.apk'],120)
  adbtext(['shell','mkdir','-p',f'/sdcard/Android/data/{PKG}/files'],30)
  for file in ['fixture59.apk','fixture.spdelta']:adbtext(['push',out/file,f'/sdcard/Android/data/{PKG}/files/{file}'],120)
  adbtext(['shell','appops','set',PKG,'REQUEST_INSTALL_PACKAGES','deny'],30)
  actual=adbtext(['shell','appops','get',PKG,'REQUEST_INSTALL_PACKAGES'],30);assert 'deny' in actual,actual
 def evidence(case):
  dest=out/case;dest.mkdir(exist_ok=True)
  for name,args in [('logcat.txt',['logcat','-d']),('package.txt',['shell','dumpsys','package',PKG]),('windows.txt',['shell','dumpsys','window'])]:
   try:(dest/name).write_bytes(run(adb+args,30).stdout)
   except Exception as e:(dest/(name+'.error')).write_text(type(e).__name__)
  # Pull completed PNGs before evaluating the instrumentation result.
  run(adb+['pull',f'/sdcard/Android/data/{PKG}/files',dest/'device-files'],60,check=False)
  # Never publish local fixture APKs or patches, including pulled copies.
  for f in dest.rglob('*'):
   if f.suffix.lower() in ('.apk','.spdelta'):f.unlink()
 for method in ['fullCompletionPermissionDenialNeverReopens','fullCompletionPermissionGrantThenRealCancelDoesNotLoop','deltaCompletionPermissionGrantThenRealInstall']:
  install_current();adbtext(['logcat','-c'],30)
  args=adb+['shell','am','instrument','-w','-r','-e','fixtureLabel',manifest['current']['label'],'-e','class',cls+'#'+method,runner]
  result=None
  try:
   result=run(args,240,check=False);(out/(method+'.instrumentation.txt')).write_bytes(result.stdout)
  finally:evidence(method)
  if method.startswith('delta'):
   # Click/process death/status are deliberately ignored as success signals.
   deadline=time.monotonic()+45
   while time.monotonic()<deadline:
    if re.search(r'\bversionCode=59\b',adbtext(['shell','dumpsys','package',PKG],30)):break
    time.sleep(1)
   else:raise AssertionError('target59 not installed')
   paths=adbtext(['shell','pm','path',PKG],30).splitlines();assert len(paths)==1 and paths[0].startswith('package:')
   installed=out/'installed59.apk';run(adb+['pull',paths[0][8:],installed],60)
   observed=metadata(installed,59,'99.0.0');assert observed['cert']==manifest['target']['cert'];assert observed['sha256']==manifest['target']['sha256']
   (out/'installed59-before-runner-recovery.json').write_text(json.dumps(observed,indent=2));installed.unlink()
   # Self-update can detach instrumentation registration. Recovery reinstalls
   # only the exact already-built test APK, never current58 or app data.
   assert sha(out/'fixture-test.apk')==manifest['testApkSha256']
   test_present=package_present(PKG+'.test')
   runners=adbtext(['shell','pm','list','instrumentation'],30)
   if not test_present or runner not in runners:
    recovery=adbtext(['install','-r',out/'fixture-test.apk'],120);(out/'test-runner-recovery.txt').write_text(recovery)
   assert package_present(PKG+'.test')
   assert runner in adbtext(['shell','pm','list','instrumentation'],30)
   after=adbtext(['shell','dumpsys','package',PKG],30);assert re.search(r'\bversionCode=59\b',after)
   (out/'package59-after-runner-check.txt').write_text(after)
   launch=adbtext(['shell','am','start','-W','-n',PKG+'/com.music.bitchord.MainActivity'],30);assert 'Status: ok' in launch
   verify=run(adb+['shell','am','instrument','-w','-r','-e','class',cls+'#verifyInstalled59AndSentinel',runner],120,check=False)
   (out/'replacement-verifier.txt').write_bytes(verify.stdout);evidence('replacement59')
   assert verify.returncode==0 and b'OK (1 test)' in verify.stdout and b'FIXTURE-INSTALLED59-VERIFIED' in (out/'replacement59/logcat.txt').read_bytes(),verify.stdout[-2000:]
  else:assert result.returncode==0 and b'OK (1 test)' in result.stdout,result.stdout[-2000:]
 print('FIXTURE CASES COMPLETE, inspect retained system pixels before clearance')
else:raise SystemExit('mode must be build or device')
