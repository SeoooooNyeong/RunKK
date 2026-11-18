import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, rm, mkdir, readFile, writeFile, readdir} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import sharp from 'sharp';
import {userDirectory} from '../user-storage.mjs';
import {RESULT_IMAGE_FIELDS, isPalettePng, parseArgs, recompressHeads} from '../scripts/recompress-heads.mjs';

// 저장된 머리 일괄 재압축(S15P31A207-300). 기본 머리 그림으로 회원 한 명의 저장 구조(heads/*.png, jobs/*/result.json)를 만든다.
const ASSETS = new URL('../public/assets/', import.meta.url);
const SCRIPT = fileURLToPath(new URL('../scripts/recompress-heads.mjs', import.meta.url));
const USER_ID = '42';
const HEAD_ID = '11111111-2222-4333-8444-555555555555';
const JOB_ID = 'aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee';
const DATA_URL = 'data:image/png;base64,';
const LAYER_SUFFIXES = ['-face', '-eyes', '-hair', '-right-face', '-right-eyes', '-right-hair'];
const KEPT_SUFFIXES = ['', '-original', '-right'];
const quiet = () => {};

async function asset(name) { return readFile(new URL(name + '.png', ASSETS)); }

async function fixture(t) {
  const root = await mkdtemp(path.join(tmpdir(), 'runkk-recompress-'));
  t.after(() => rm(root, {recursive: true, force: true}));
  const [face, eyes, hair, head] = await Promise.all(['face-base-default', 'eyes-default', 'hair-default', 'head-default'].map(asset));
  const layerSource = {'-face': face, '-eyes': eyes, '-hair': hair, '-right-face': face, '-right-eyes': eyes, '-right-hair': hair};
  const user = userDirectory(root, USER_ID), heads = path.join(user, 'heads'), job = path.join(user, 'jobs', JOB_ID);
  await mkdir(heads, {recursive: true});
  await mkdir(job, {recursive: true});
  for (const suffix of LAYER_SUFFIXES) await writeFile(path.join(heads, HEAD_ID + suffix + '.png'), layerSource[suffix]);
  for (const suffix of KEPT_SUFFIXES) await writeFile(path.join(heads, HEAD_ID + suffix + '.png'), head);
  await writeFile(path.join(heads, HEAD_ID + '.json'), JSON.stringify({id: HEAD_ID, userId: USER_ID}, null, 2));
  await writeFile(path.join(user, 'latest.json'), JSON.stringify({id: HEAD_ID}, null, 2));
  const result = {
    userId: USER_ID, schemaVersion: 'runkk-face-assets-v1', headId: HEAD_ID,
    frontImage: DATA_URL + head.toString('base64'), rightImage: DATA_URL + head.toString('base64'),
    faceImage: DATA_URL + face.toString('base64'), eyesImage: DATA_URL + eyes.toString('base64'), hairImage: DATA_URL + hair.toString('base64'),
    rightFaceImage: DATA_URL + face.toString('base64'), rightEyesImage: DATA_URL + eyes.toString('base64'), rightHairImage: DATA_URL + hair.toString('base64'),
    mobile: {version: 'runkk-mobile-compose-v2', renderFaceImage: DATA_URL + face.toString('base64'), skinColor: '#f0c8a0', pivot: [0.5, 0.083]},
    traits: {hair: 'short'}, alignment: {pivotX: 0.5, scale: 1.0000000000000002}, saved: true, warning: null, metrics: {ms: 1234},
  };
  await writeFile(path.join(job, 'result.json'), JSON.stringify(result, null, 2));
  await writeFile(path.join(job, 'job.json'), JSON.stringify({jobId: JOB_ID, userId: USER_ID, status: 'SUCCEEDED'}, null, 2));
  return {root, data: path.join(root, 'data'), heads, job, result, layerSource, head};
}

// 폴더 안 모든 파일의 상대 경로 → 내용
async function snapshot(dir) {
  const out = new Map();
  for (const entry of await readdir(dir, {recursive: true, withFileTypes: true})) {
    if (!entry.isFile()) continue;
    const file = path.join(entry.parentPath ?? entry.path, entry.name);
    out.set(path.relative(dir, file), await readFile(file));
  }
  return out;
}

async function assertSameAlpha(original, compressed, label) {
  const meta = await sharp(compressed).metadata();
  assert.equal(meta.format, 'png', label);
  assert.equal(meta.width, 1024, label);
  assert.equal(meta.height, 1024, label);
  assert.equal(meta.hasAlpha, true, label + ': 알파가 사라졌다');
  const [a, b] = await Promise.all([original, compressed].map(buffer => sharp(buffer).ensureAlpha().extractChannel(3).raw().toBuffer()));
  let shown = 0, transparent = 0, diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff += Math.abs(a[i] - b[i]);
    if (a[i] === 0) { transparent++; if (b[i] > 8) shown++; }
  }
  assert.ok(shown <= transparent * 0.0001, `${label}: 투명 픽셀 ${shown}개가 보이게 됐다`);
  assert.ok(diff / a.length < 2, `${label}: 평균 알파 차이 ${diff / a.length}`);
}

test('인자: --data·--dry-run·--limit을 읽고 잘못된 값은 거절한다', () => {
  assert.deepEqual(parseArgs(['--data', '/app/data', '--dry-run', '--limit', '20']), {data: path.resolve('/app/data'), dryRun: true, limit: 20, help: false});
  assert.equal(parseArgs([]).dryRun, false);
  assert.equal(parseArgs([]).limit, Infinity);
  assert.equal(parseArgs(['--data=/x']).data, path.resolve('/x'));
  for (const bad of [['--limit', '0'], ['--limit', 'abc'], ['--limit'], ['--data'], ['--force']]) assert.throws(() => parseArgs(bad), /인자/, bad.join(' '));
});

test('팔레트 PNG를 IHDR 색 형식으로 알아본다', async () => {
  const face = await asset('face-base-default');
  assert.equal(isPalettePng(face), false);
  assert.equal(isPalettePng(await sharp(face).png({palette: true}).toBuffer()), true);
  assert.equal(isPalettePng(Buffer.from('not a png')), false);
});

test('미리 보기는 아무 파일도 바꾸지 않고 대상 수와 예상 감소량만 알린다', async t => {
  const {root, data} = await fixture(t);
  const before = await snapshot(root);
  const lines = [];
  const summary = await recompressHeads({data, dryRun: true, log: line => lines.push(line)});
  assert.deepEqual(await snapshot(root), before, '미리 보기에서 파일이 바뀌었다');
  assert.equal(summary.files, 7);
  assert.equal(summary.layers, 12);
  assert.equal(summary.shrunk, 12);
  assert.equal(summary.changedFiles, 0);
  assert.equal(summary.plannedFiles, 7);
  assert.ok(summary.layerBytesAfter < summary.layerBytesBefore * 0.6, `${summary.layerBytesBefore} → ${summary.layerBytesAfter}`);
  assert.ok(summary.fileBytesAfter < summary.fileBytesBefore);
  assert.ok(lines.some(line => line.includes('미리 보기')));
});

test('실제 실행은 보여 주는 6레이어만 줄이고 1024·알파·JSON 다른 필드를 지키며 다시 돌려도 바꾸지 않는다', async t => {
  const {root, data, heads, job, result, layerSource, head} = await fixture(t);
  const lines = [];
  const summary = await recompressHeads({data, log: line => lines.push(line)});
  assert.equal(summary.failed, 0);
  assert.equal(summary.changedFiles, 7);
  assert.ok(summary.layerBytesAfter < summary.layerBytesBefore * 0.6);
  assert.ok(summary.fileBytesAfter < summary.fileBytesBefore);

  for (const suffix of LAYER_SUFFIXES) {
    const out = await readFile(path.join(heads, HEAD_ID + suffix + '.png'));
    assert.ok(out.length < layerSource[suffix].length * 0.6, `${suffix}: ${layerSource[suffix].length} → ${out.length}`);
    assert.ok(isPalettePng(out), suffix);
    await assertSameAlpha(layerSource[suffix], out, suffix);
  }
  for (const suffix of KEPT_SUFFIXES) assert.ok((await readFile(path.join(heads, HEAD_ID + suffix + '.png'))).equals(head), `${suffix || '합성'}은 그대로여야 한다`);

  const saved = JSON.parse(await readFile(path.join(job, 'result.json'), 'utf8'));
  assert.deepEqual(Object.keys(saved), Object.keys(result), '키 순서가 바뀌었다');
  for (const key of Object.keys(result)) {
    if (RESULT_IMAGE_FIELDS.includes(key)) continue;
    assert.deepEqual(saved[key], result[key], key);
  }
  for (const field of RESULT_IMAGE_FIELDS) {
    assert.ok(saved[field].startsWith(DATA_URL), field);
    const original = Buffer.from(result[field].slice(DATA_URL.length), 'base64');
    const out = Buffer.from(saved[field].slice(DATA_URL.length), 'base64');
    assert.ok(out.length < original.length * 0.6, field);
    assert.ok(isPalettePng(out), field);
    await assertSameAlpha(original, out, field);
  }
  assert.ok((await readdir(heads)).every(name => !name.endsWith('.tmp')), '임시 파일이 남았다');
  assert.ok((await readdir(job)).every(name => !name.endsWith('.tmp')), '임시 파일이 남았다');

  // 진행 기록에 회원 식별값(사용자 ID·폴더 해시·머리 ID·작업 ID)이 나오지 않는다
  const userHash = path.basename(userDirectory(root, USER_ID));
  for (const line of lines) for (const secret of [userHash, HEAD_ID, JOB_ID, root]) assert.ok(!line.includes(secret), line);

  // 멱등: 이미 팔레트라 다시 돌려도 바이트가 그대로다
  const once = await snapshot(root);
  const again = await recompressHeads({data, log: quiet});
  assert.equal(again.changedFiles, 0);
  assert.equal(again.palette, 12);
  assert.deepEqual(await snapshot(root), once);
});

test('압축 결과가 원본보다 작지 않으면 파일을 그대로 둔다', async t => {
  const {root, data} = await fixture(t);
  const before = await snapshot(root);
  const bigger = async buffer => Buffer.concat([buffer, Buffer.alloc(16)]);
  const same = async buffer => buffer;
  for (const compress of [bigger, same]) {
    const summary = await recompressHeads({data, compress, log: quiet});
    assert.equal(summary.changedFiles, 0);
    assert.equal(summary.kept, 12);
  }
  assert.deepEqual(await snapshot(root), before);
});

test('--limit은 앞에서 N개 파일만 본다', async t => {
  const {data} = await fixture(t);
  const summary = await recompressHeads({data, limit: 2, log: quiet});
  assert.equal(summary.files, 2);
  assert.equal(summary.changedFiles, 2);
});

test('1024 알파 PNG가 아니거나 깨진 레이어는 건드리지 않고 실패로 세지 않는다', async t => {
  const {data, heads} = await fixture(t);
  const small = await sharp({create: {width: 64, height: 64, channels: 4, background: {r: 0, g: 0, b: 0, alpha: 0.5}}}).png().toBuffer();
  await writeFile(path.join(heads, HEAD_ID + '-face.png'), small);
  await writeFile(path.join(heads, HEAD_ID + '-eyes.png'), Buffer.from('broken'));
  const summary = await recompressHeads({data, log: quiet});
  assert.equal(summary.failed, 0);
  assert.ok((await readFile(path.join(heads, HEAD_ID + '-face.png'))).equals(small));
  assert.ok((await readFile(path.join(heads, HEAD_ID + '-eyes.png'))).equals(Buffer.from('broken')));
});

test('데이터 폴더에 회원이 없으면 0개로 끝난다', async t => {
  const root = await mkdtemp(path.join(tmpdir(), 'runkk-recompress-empty-'));
  t.after(() => rm(root, {recursive: true, force: true}));
  const summary = await recompressHeads({data: root, log: quiet});
  assert.equal(summary.files, 0);
  assert.equal(summary.changedFiles, 0);
});

test('명령줄: node scripts/recompress-heads.mjs --data <dir> --dry-run 은 0으로 끝나고 파일을 바꾸지 않는다', async t => {
  const {root, data} = await fixture(t);
  const before = await snapshot(root);
  const run = spawnSync(process.execPath, [SCRIPT, '--data', data, '--dry-run'], {encoding: 'utf8'});
  assert.equal(run.status, 0, run.stderr);
  assert.match(run.stdout, /미리 보기/);
  assert.deepEqual(await snapshot(root), before);
  const bad = spawnSync(process.execPath, [SCRIPT, '--limit', '-1'], {encoding: 'utf8'});
  assert.equal(bad.status, 2);
});
