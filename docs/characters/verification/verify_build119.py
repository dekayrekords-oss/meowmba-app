from pathlib import Path
import json, hashlib, shutil, subprocess, os, re, zipfile, sys
from xml.etree import ElementTree as E

repo = Path('/home/claude/meowmba-fly-habitat')
commit = subprocess.check_output(['git','rev-parse','HEAD'], cwd=repo, text=True).strip()
assert commit.startswith(sys.argv[1]), commit
target = Path('/home/prog/research') / ('meowmba-build119-' + commit[:7])
assert not (target/'verification.json').exists(), 'Archive already finalized'
target.mkdir(exist_ok=True)
roots = [E.parse(p).getroot() for p in (repo/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
tests = {k: sum(int(x.attrib.get(k, 0)) for x in roots) for k in ('tests','failures','errors','skipped')}
assert tests['tests'] >= 100 and tests['failures'] == 0 and tests['errors'] == 0 and tests['skipped'] == 0, tests
names = {x.attrib['name'] for x in roots}
assert {'com.example.meowmba.GeneratedAvatarIntegrationTest','com.example.meowmba.GeneratedAvatarRenderTest','com.example.meowmba.GeneratedAvatarSetupUiTest','com.example.meowmba.GeneratedAvatarFormTest','com.example.meowmba.GeneratedAvatarStoreTest','com.example.meowmba.AgentCancellationLifecycleTest','com.example.meowmba.OrchestratorTest','com.example.meowmba.AgentReliabilityTest','com.example.meowmba.CameraPrivacyViewModelTest','com.example.meowmba.VisionManagerLifecycleTest'} <= names
tests['suites'] = [dict(name=x.attrib['name'],tests=int(x.attrib['tests'])) for x in roots]
env = dict(os.environ, JAVA_HOME='/home/claude/.local-toolchain/jdk-21.0.12+8')
env['PATH'] = env['JAVA_HOME'] + '/bin:' + env.get('PATH','/usr/bin:/bin')
sdk = '/home/claude/Android/Sdk/build-tools/37.0.0/'
signer = 'b7850d5fb645df9f5d12525db1b10e457b0ee7f3aa50bbf4294ff31dae3379ff'
artifacts = []
for src, name in [('app/build/outputs/apk/debug/app-debug.apk','meowmba-119.apk'),('app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk','tests119.apk')]:
    p = target/name
    shutil.copy2(repo/src, p)
    sig = subprocess.check_output([sdk+'apksigner','verify','--verbose','--print-certs',str(p)], env=env, text=True)
    (target/(name+'.signature.txt')).write_text(sig)
    fingerprint = re.search(r'certificate SHA-256 digest: ([a-f0-9]+)',sig).group(1)
    assert fingerprint == signer
    badging = subprocess.check_output([sdk+'aapt2','dump','badging',str(p)],text=True).splitlines()[0]
    if name == 'meowmba-119.apk': assert "versionCode='119'" in badging
    with p.open('rb') as f: sha = hashlib.file_digest(f,'sha256').hexdigest()
    artifacts.append(dict(file=str(p),bytes=p.stat().st_size,sha256=sha,signer_sha256=fingerprint,badging=badging))

protected = json.loads((repo/'docs/characters/BUILD_116_VERIFICATION.json').read_text())['preserved_entry_sha256']
new_art = json.loads((repo/'docs/characters/artwork/build117-environments-manifest.json').read_text())
with zipfile.ZipFile(target/'meowmba-119.apk') as z:
    for name, sha in protected.items():
        with z.open(name) as f: assert hashlib.file_digest(f,'sha256').hexdigest() == sha, name
    for item in new_art:
        name = item['assetPath'].replace('app/src/main/assets/', 'assets/')
        with z.open(name) as f: assert hashlib.file_digest(f,'sha256').hexdigest() == item['sha256'], name
    assert len([n for n in z.namelist() if n.startswith('assets/backgrounds/painted/') and n.endswith('.png')]) == 19
    assert not any('malecns-full' in name for name in z.namelist())

for apk, manifest, patch in [('meowmba-119.apk','/tmp/meow-115-app-manifest.json','app119.patch.gz'),('tests119.apk','/tmp/meow-115-test-manifest.json','test119.patch.gz')]:
    subprocess.run(['python3',str(repo/'scripts/apk_delta.py'),'pack',str(target/apk),manifest,str(target/patch)],check=True)
previews = []
for folder in ['forms119','generated-avatars']:
    source = repo/'app/build/reports'/folder
    assert source.is_dir(), source
    shutil.copytree(source, target/'previews'/folder, dirs_exist_ok=True)
    previews += [str(p.relative_to(target)) for p in (target/'previews'/folder).glob('*.png')]
report = dict(build=119,source_commit=commit,server_tests=tests,artifacts=artifacts,
    preserved_runtime_count=53,preserved_painted_background_count=19,new_painted_background_count=0,
    preserved_entry_sha256=protected,preserved_build117_artwork=new_art,phone_installed=False,last_verified_phone_build=112,
    render_previews=previews,archive_host='meow-nb',delta_base_build=115, generated_forms=dict(max_saved=24,max_parts=32,requires_configured_model=True,representation='native animated vector cartoons'))
live_source = Path('/home/prog/research/meowmba-build119-live-forms')
assert live_source.is_dir()
shutil.copytree(live_source, target/'live-inference', dirs_exist_ok=True)
report['live_model_generation'] = dict(success=False, model='Qwen3.8-Flash-Next', regular_attempts=3, regular_deadline_seconds=80, streaming_deadline_seconds=90, returned_content_characters=0, note='Existing Meow model service returned no design. Deterministic model fixtures validate the application path; successful live generation remains unverified.')
(target/'verification.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items() if k not in ('preserved_entry_sha256','preserved_build117_artwork','server_tests')},indent=2))
print(json.dumps(tests,indent=2))
