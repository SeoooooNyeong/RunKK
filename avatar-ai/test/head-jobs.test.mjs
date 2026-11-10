import test from 'node:test';
import assert from 'node:assert/strict';
import express from 'express';
import multer from 'multer';
import {mkdtemp, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {randomUUID} from 'node:crypto';
import {setTimeout as pause} from 'node:timers/promises';
import {registerHeadJobs} from '../head-jobs.mjs';
import {installApiContract} from '../api-contract.mjs';
import {userDirectory, atomicJson} from '../user-storage.mjs';

async function fixture(t, generate, options = {}) {
  const root = await mkdtemp(path.join(tmpdir(), 'runkk-jobs-'));
  const app = express();
  installApiContract(app);
  const server = await new Promise(resolve => {const s = app.listen(0, '127.0.0.1', () => resolve(s));});
  const port = server.address().port;
  const jobs = registerHeadJobs(app, {root, port, upload: multer({storage: multer.memoryStorage()}), normalizeSelfie: async buffer => buffer, workerIntervalMs: 25, ...options});
  app.post('/api/generate', multer({storage: multer.memoryStorage()}).single('selfie'), generate);
  app.use((error, req, res, next) => res.status(error.status || 500).json({code: error.code, message: error.message}));
  t.after(async () => {
    jobs.stop();
    server.closeAllConnections();
    await new Promise(resolve => server.close(resolve));
    await pause(50);
    const resolved = path.resolve(root);
    assert.ok(resolved.startsWith(path.join(path.resolve(tmpdir()), 'runkk-jobs-')));
    await rm(resolved, {recursive: true, force: true});
  });
  const base = `http://127.0.0.1:${port}/api/v1`;
  const submit = async (id, userId = '42', image = 'same-selfie') => {
    const form = new FormData();
    form.append('userId', userId);form.append('clientRequestId', id);
    form.append('selfie', new Blob([image], {type: 'image/jpeg'}), 'selfie.jpg');
    const response = await fetch(base + '/head-jobs', {method: 'POST', body: form});
    return {status: response.status, body: await response.json()};
  };
  const poll = async (id, expected, waitMs = 3500) => {
    const deadline = Date.now() + waitMs;
    let value;
    while (Date.now() < deadline) {
      const r = await fetch(base + `/head-jobs/${id}?userId=42`);
      assert.equal(r.status, 200);value = await r.json();
      if (value.status === expected) return value;
      await pause(25);
    }
    assert.fail(`Expected ${expected}, got ${value?.status}`);
  };
  return {base, submit, poll, root};
}

test('동시·재접수는 202와 같은 작업을 반환하고 생성 완료를 기다리지 않는다', async t => {
  let calls = 0;
  const f = await fixture(t, (req, res) => {calls++;res.json({saved: true, headId: 'head-1'});});
  const id = randomUUID(), start = Date.now();
  const replies = await Promise.all([f.submit(id), f.submit(id), f.submit(id)]);
  assert.ok(Date.now() - start < 12000);
  for (const r of replies) {assert.equal(r.status, 202);assert.equal(r.body.jobId, id);}
  await f.poll(id, 'SUCCEEDED');
  assert.equal((await f.submit(id)).body.jobId, id);
  assert.equal(calls, 1);
  assert.equal((await f.submit(id, '42', 'different-selfie')).status, 409);
  assert.equal((await fetch(f.base + `/head-jobs/${id}?userId=43`)).status, 404);
});

test('실패 상태는 HTTP 200 + FAILED + error.code이고 콜백 미설정은 조용히 건너뛴다', async t => {
  const f = await fixture(t, (req, res) => res.status(422).json({code: 'SELFIE_QUALITY_REJECTED', message: '얼굴이 어두워요.'}));
  const id = randomUUID();assert.equal((await f.submit(id)).status, 202);
  const job = await f.poll(id, 'FAILED');
  assert.equal(job.error.code, 'SELFIE_QUALITY_REJECTED');
  assert.equal(job.notification.status, 'NOT_CONFIGURED');
  assert.equal(job.notification.attempts, 0);
  const result = await fetch(f.base + `/head-jobs/${id}/result?userId=42`);
  assert.equal(result.status, 409);assert.equal((await result.json()).code, 'JOB_NOT_READY');
});

test('생성기가 끝나지 않아도 접수 202를 먼저 반환한다', {timeout: 5000}, async t => {
  let release;
  const gate = new Promise(resolve => {release = resolve;});
  t.after(() => release());
  const f = await fixture(t, async (req, res) => {await gate;res.json({saved: true, headId: 'head-gated'});});
  const id = randomUUID();
  const accepted = await f.submit(id);
  assert.equal(accepted.status, 202);
  await f.poll(id, 'RUNNING');
  release();
  await f.poll(id, 'SUCCEEDED');
});

test('영구적인 409는 재생성하지 않고 실패로 종료한다', async t => {
  let calls = 0;
  const f = await fixture(t, (req, res) => {calls++;res.status(409).json({code: 'FACE_ASSETS_INCOMPLETE', message: '결과 불완전'});});
  const id = randomUUID();await f.submit(id);
  const job = await f.poll(id, 'FAILED');
  assert.equal(job.error.code, 'FACE_ASSETS_INCOMPLETE');assert.equal(calls, 1);
});

test('생성 전 busy 응답만 다시 대기하고 성공 결과를 저장한다', async t => {
  let calls = 0;
  const f = await fixture(t, (req, res) => ++calls === 1 ? res.status(409).json({code: 'GENERATION_BUSY'}) : res.json({saved: true, headId: 'head-2'}));
  const id = randomUUID();await f.submit(id);await f.poll(id, 'SUCCEEDED');
  const response = await fetch(f.base + `/head-jobs/${id}/result?userId=42`);
  assert.equal(response.status, 200);assert.equal((await response.json()).headId, 'head-2');assert.equal(calls, 2);
});

test('생성 응답 시간 초과는 UPSTREAM_TIMEOUT으로 종료하고 자동 재호출하지 않는다', async t => {
  let calls = 0;
  const f = await fixture(t, () => {calls++;}, {generationTimeoutMs: 40});
  const id = randomUUID();await f.submit(id);
  const job = await f.poll(id, 'FAILED');
  assert.equal(job.error.code, 'UPSTREAM_TIMEOUT');assert.equal(calls, 1);
});

// DV-성-9: 같은 프로세스의 /generate 호출이 전역 fetch(undici)의 응답 머리 대기 기본 300초에 끊겨 15분 생성 제한보다 먼저
// GENERATION_OUTCOME_UNKNOWN이 됐다. 300초를 기다릴 수 없어 전역 dispatcher의 머리 대기 제한을 200ms로 줄여 같은 상황을 만든다.
test('생성 호출은 전역 fetch의 응답 머리 대기 제한에 끊기지 않고 generationTimeoutMs만 따른다', {timeout: 10000}, async t => {
  const key = Symbol.for('undici.globalDispatcher.1');
  await fetch('data:,warm');
  const original = globalThis[key];
  assert.ok(original, '전역 dispatcher가 있어야 한다');
  globalThis[key] = new original.constructor({headersTimeout: 200});
  t.after(() => {globalThis[key] = original;});
  const f = await fixture(t, async (req, res) => {await pause(1500);res.json({saved: true, headId: 'head-slow'});});
  const id = randomUUID();await f.submit(id);
  const job = await f.poll(id, 'SUCCEEDED', 6000);
  assert.equal(job.headId, 'head-slow');
});

async function saveHeadMeta(root, userId, createdAt = new Date().toISOString()) {
  const id = randomUUID(), dir = userDirectory(root, userId);
  await atomicJson(path.join(dir, 'heads', id + '.json'), {id, createdAt, userId});
  await atomicJson(path.join(dir, 'latest.json'), {id});
  return id;
}

test('시간 초과로 끝난 작업은 그 뒤 저장된 머리를 상태 조회 때 회수하고 다시 생성하지 않는다', {timeout: 10000}, async t => {
  let calls = 0, saved;
  const holder = {};
  const f = await fixture(t, async (req, res) => {calls++;await pause(250);saved = await saveHeadMeta(holder.root, '42');res.json({saved: true, headId: saved});}, {generationTimeoutMs: 60});
  holder.root = f.root;
  const id = randomUUID();await f.submit(id);
  const failed = await f.poll(id, 'FAILED');
  assert.equal(failed.error.code, 'UPSTREAM_TIMEOUT');
  const recovered = await f.poll(id, 'SUCCEEDED');
  assert.equal(recovered.headId, saved);assert.equal(recovered.error, null);
  const result = await fetch(f.base + `/head-jobs/${id}/result?userId=42`);
  assert.equal(result.status, 200);assert.equal((await result.json()).headId, saved);
  assert.equal(calls, 1);
});

test('작업 시작 전에 저장된 머리나 다른 작업이 가진 머리는 회수하지 않는다', {timeout: 10000}, async t => {
  const holder = {};
  let calls = 0;
  const f = await fixture(t, async (req, res) => {
    calls++;
    if (calls === 1) return;// 응답하지 않아 시간 초과
    const headId = await saveHeadMeta(holder.root, '42');res.json({saved: true, headId});
  }, {generationTimeoutMs: 60});
  holder.root = f.root;
  await saveHeadMeta(f.root, '42', '2000-01-01T00:00:00.000Z');
  const stale = randomUUID();await f.submit(stale);
  assert.equal((await f.poll(stale, 'FAILED')).error.code, 'UPSTREAM_TIMEOUT');
  await pause(100);
  assert.equal((await f.poll(stale, 'FAILED')).headId, null);
  const next = randomUUID();await f.submit(next, '42', 'another-selfie');
  const done = await f.poll(next, 'SUCCEEDED');
  await pause(100);
  const still = await f.poll(stale, 'FAILED');
  assert.equal(still.headId, null);assert.notEqual(done.headId, null);
});
