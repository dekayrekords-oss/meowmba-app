from pathlib import Path
import json, hashlib, shutil, subprocess, os, re, zipfile, sys
from xml.etree import ElementTree as E

repo = Path('/home/claude/meowmba-fly-habitat')
commit = subprocess.check_output(['git','rev-parse','HEAD'], cwd=repo, text=True).strip()
assert commit.startswith(sys.argv[1]), commit
target = Path('/home/prog/research') / ('meowmba-build120-' + commit[:7])
assert not (target/'verification.json').exists(), 'Archive already finalized'
target.mkdir(exist_ok=True)
roots = [E.parse(p).getroot() for p in (repo/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
tests = {k: sum(int(x.attrib.get(k, 0)) for x in roots) for k in ('tests','failures','errors','skipped')}
names = {x.attrib['name'] for x in roots}
# Gradle filters class names, not filenames: ToolCallBrainTest.kt contains five
# differently named classes, and its old filename selector silently skipped all
# five while other matching filters kept the run green. Pin the complete set.
required_names = {'com.example.meowmba.' + name for name in (
    'AgentBrainViewModelTest',
    'AgentCancellationLifecycleTest',
    'AgentReliabilityTest',
    'AgentRouterTest',
    'AgentViewModelTest',
    'AvatarFormInventorTest',
    'AvatarFormToolTest',
    'AvatarMorphRenderTest',
    'AvatarMorphTest',
    'BrainAvatarVisibilityTest',
    'CameraPrivacyViewModelTest',
    'CommandHeuristicsTest',
    'CompanionBrainControlsTest',
    'CrossDeviceControlTest',
    'CustomAnimationTest',
    'FruitFlyAvatarTest',
    'GeneratedAvatarFormTest',
    'GeneratedAvatarIntegrationTest',
    'GeneratedAvatarRenderTest',
    'GeneratedAvatarSetupUiTest',
    'GeneratedAvatarStoreTest',
    'InventedSceneRenderTest',
    'LlmToolCallRouterTest',
    'LumenIntegrationTest',
    'LumenNaturalMotionTest',
    'OfficeHelpersRenderTest',
    'OfficeHelpersTest',
    'OrchestratorTest',
    'ProjectExecutionContextTest',
    'ToolCallParserTest',
    'ToolCallPromptTest',
    'VisionManagerLifecycleTest',
    'VisionPipelineTest',
    'neural.MaleCnsServerManagerTest',
    'neural.NeuralServerReplacementTest',
)}
required_names.update({'com.example.meowmba.core.agentic.ComputerP0Test', 'com.example.meowmba.core.agentic.Computer120Test', 'com.example.meowmba.core.agentic.ComputerSshSessionTest', 'com.example.meowmba.ui.companion.ComputerCommandFrameTest'})
missing_names = sorted(required_names - names)
assert not missing_names, {'missing_acceptance_suites': missing_names, 'observed_suites': sorted(names)}
acceptance_test_count = sum(int(x.attrib.get('tests', 0)) for x in roots if x.attrib['name'] in required_names)
assert acceptance_test_count >= 280, {'minimum_acceptance_tests': 280, 'acceptance_tests': acceptance_test_count, **tests}
assert tests['failures'] == 0 and tests['errors'] == 0 and tests['skipped'] == 0, tests
tests['suites'] = [dict(name=x.attrib['name'],tests=int(x.attrib['tests'])) for x in roots]
env = dict(os.environ, JAVA_HOME='/home/claude/.local-toolchain/jdk-21.0.12+8')
env['PATH'] = env['JAVA_HOME'] + '/bin:' + env.get('PATH','/usr/bin:/bin')
sdk = '/home/claude/Android/Sdk/build-tools/37.0.0/'
signer = 'b7850d5fb645df9f5d12525db1b10e457b0ee7f3aa50bbf4294ff31dae3379ff'
artifacts = []
for src, name in [('app/build/outputs/apk/debug/app-debug.apk','meowmba-120.apk'),('app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk','tests120.apk')]:
    p = target/name
    shutil.copy2(repo/src, p)
    sig = subprocess.check_output([sdk+'apksigner','verify','--verbose','--print-certs',str(p)], env=env, text=True)
    (target/(name+'.signature.txt')).write_text(sig)
    fingerprint = re.search(r'certificate SHA-256 digest: ([a-f0-9]+)',sig).group(1)
    assert fingerprint == signer
    badging = subprocess.check_output([sdk+'aapt2','dump','badging',str(p)],text=True).splitlines()[0]
    if name == 'meowmba-120.apk': assert "versionCode='120'" in badging
    with p.open('rb') as f: sha = hashlib.file_digest(f,'sha256').hexdigest()
    artifacts.append(dict(file=str(p),bytes=p.stat().st_size,sha256=sha,signer_sha256=fingerprint,badging=badging))

protected = json.loads((repo/'docs/characters/BUILD_116_VERIFICATION.json').read_text())['preserved_entry_sha256']
new_art = json.loads((repo/'docs/characters/artwork/build117-environments-manifest.json').read_text())
with zipfile.ZipFile(target/'meowmba-120.apk') as z:
    for name, sha in protected.items():
        with z.open(name) as f: assert hashlib.file_digest(f,'sha256').hexdigest() == sha, name
    for item in new_art:
        name = item['assetPath'].replace('app/src/main/assets/', 'assets/')
        with z.open(name) as f: assert hashlib.file_digest(f,'sha256').hexdigest() == item['sha256'], name
    assert len([n for n in z.namelist() if n.startswith('assets/backgrounds/painted/') and n.endswith('.png')]) == 19
    assert not any('malecns-full' in name for name in z.namelist())

for apk, manifest, patch in [('meowmba-120.apk','/tmp/meow-115-app-manifest.json','app120.patch.gz'),('tests120.apk','/tmp/meow-115-test-manifest.json','test120.patch.gz')]:
    subprocess.run(['python3',str(repo/'scripts/apk_delta.py'),'pack',str(target/apk),manifest,str(target/patch)],check=True)
previews = []
for folder in ['forms119','generated-avatars']:
    source = repo/'app/build/reports'/folder
    assert source.is_dir(), source
    shutil.copytree(source, target/'previews'/folder, dirs_exist_ok=True)
    previews += [str(p.relative_to(target)) for p in (target/'previews'/folder).glob('*.png')]
report = dict(build=120,source_commit=commit,server_tests=tests,artifacts=artifacts,
    preserved_runtime_count=53,preserved_painted_background_count=19,new_painted_background_count=0,
    preserved_entry_sha256=protected,preserved_build117_artwork=new_art,phone_installed=False,last_verified_phone_build=112,
    render_previews=previews,archive_host='meow-nb',delta_base_build=115, generated_forms=dict(max_saved=24,max_parts=32,requires_configured_model=True,representation='native animated vector cartoons'))
report['live_model_generation'] = dict(success=False, note='Not retested in Build 120; prior live model generation remains unverified.')
(target/'verification.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps({k:v for k,v in report.items() if k not in ('preserved_entry_sha256','preserved_build117_artwork','server_tests')},indent=2))
print(json.dumps(tests,indent=2))
