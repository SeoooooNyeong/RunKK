import test from 'node:test';
import assert from 'node:assert/strict';
import express from 'express';
import multer from 'multer';
import {mkdtemp, rm, mkdir, writeFile, access} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {randomUUID} from 'node:crypto';
import {setTimeout as pause} from 'node:timers/promises';
import {registerHeadJobs} from '../head-jobs.mjs';
import {installApiContract} from '../api-contract.mjs';
import {userDirectory, deleteUserData} from '../user-storage.mjs';

// 탈퇴 파기(S15P31A207-257): 사용자 폴더(셀카 작업·머리·옆모습·latest) 전체를 지운다.
async function fixture(t, generate) {
  const root = await mkdtemp(path.join(tmpdir(), 'runkk-delete-'));
  const app = express();
  installApiContract(app);
  const server = await new Promise(resolve => {const s = app.listen(0, '127.0.0.1', () => resolve(s));});
  const port = server.address().port;
  const jobs = registerHeadJobs(app, {root, port, upload: multer({storage: multer.memoryStorage()}), normalizeSelfie: async b => b, workerIntervalMs: 25});
  app.post('/api/generate', multer({storage: multer.memoryStorage()}).single('selfie'), generate);
  app.use((error, req, res, next) => res.status(error.status || 500).json({code: error.code, message: error.message}));
  t.after(async () => {
    jobs.stop();
    server.closeAllConnections();
    await new Promise(resolve => server.close(resolve));
    await pause(50);
    await rm(root, {recursive: true, force: true});
  });
  const base = `http://127.0.0.1:${port}/api/v1`;
  const submit = async (id, userId = '42') => {
    const form = new FormData();
    form.append('userId', userId);form.append('clientRequestId', id);
    form.append('selfie', new Blob(['selfie-bytes'], {type: 'image/jpeg'}), 'selfie.jpg');
    return fetch(base + '/head-jobs', {method: 'POST', body: form});
  };
  const remove = async userId => {const r = await fetch(base + '/users/' + userId, {method: 'DELETE'});return {status: r.status, body: await r.json()};};
  const status = async (id, userId = '42') => (await (await fetch(base + `/head-jobs/${id}?userId=${userId}`)).json()).status;
  return {root, base, submit, remove, status};
}

const exists = file => access(file).then(() => true, () => false);

test('deleteUserData는 그 사용자 폴더만 지우고 다른 사용자는 남긴다', async t => {
  const root = await mkdtemp(path.join(tmpdir(), 'runkk-delete-'));
  t.after(() => rm(root, {recursive: true, force: true}));
  for (const user of ['42', '43']) {
    const dir = userDirectory(root, user);
    await mkdir(path.join(dir, 'heads'), {recursive: true});
    await writeFile(path.join(dir, 'heads', 'x.png'), 'png');
    await writeFile(path.join(dir, 'latest.json'), '{}');
  }

  assert.equal(await deleteUserData(root, '42'), true);
  assert.equal(await exists(userDirectory(root, '42')), false);
  assert.equal(await exists(path.join(userDirectory(root, '43'), 'latest.json')), true);
  assert.equal(await deleteUserData(root, '42'), false);
});

test('DELETE /users/{userId}는 완료된 작업·결과를 지우고 다시 불러도 200이다', async t => {
  const f = await fixture(t, (req, res) => res.json({saved: true, headId: 'head-1'}));
  const id = randomUUID();
  assert.equal((await f.submit(id)).status, 202);
  for (let i = 0; i < 100 && await f.status(id) !== 'SUCCEEDED'; i++) await pause(25);

  const first = await f.remove('42');
  const again = await f.remove('42');

  assert.deepEqual(first, {status: 200, body: {userId: '42', deleted: true}});
  assert.deepEqual(again, {status: 200, body: {userId: '42', deleted: false}});
  assert.equal(await exists(userDirectory(f.root, '42')), false);
  const lookup = await fetch(f.base + `/head-jobs/${id}?userId=42`);
  assert.equal(lookup.status, 404);
});

test('생성 중인 사용자는 409 USER_BUSY로 거절하고 아무것도 지우지 않는다', {timeout: 5000}, async t => {
  let release;
  const gate = new Promise(resolve => {release = resolve;});
  t.after(() => release());
  const f = await fixture(t, async (req, res) => {await gate;res.json({saved: true, headId: 'head-busy'});});
  const id = randomUUID();
  await f.submit(id);
  for (let i = 0; i < 100 && await f.status(id) !== 'RUNNING'; i++) await pause(25);

  const busy = await f.remove('42');

  assert.equal(busy.status, 409);
  assert.equal(busy.body.code, 'USER_BUSY');
  assert.equal(await exists(userDirectory(f.root, '42')), true);
  release();
  for (let i = 0; i < 100 && await f.status(id) !== 'SUCCEEDED'; i++) await pause(25);
  assert.equal((await f.remove('42')).status, 200);
});

test('대기 중인 작업은 큐에서 빼고 셀카와 함께 지운다(생성되지 않는다)', {timeout: 5000}, async t => {
  let release, calls = 0;
  const gate = new Promise(resolve => {release = resolve;});
  t.after(() => release());
  const f = await fixture(t, async (req, res) => {calls++;await gate;res.json({saved: true, headId: 'h'});});
  const running = randomUUID(), queued = randomUUID();
  await f.submit(running, '41');
  for (let i = 0; i < 100 && await f.status(running, '41') !== 'RUNNING'; i++) await pause(25);
  await f.submit(queued, '42');

  assert.equal((await f.remove('42')).status, 200);
  release();
  await pause(300);

  assert.equal(calls, 1);
  assert.equal(await exists(userDirectory(f.root, '42')), false);
});

test('userId 형식이 틀리면 400 INVALID_USER_ID', async t => {
  const f = await fixture(t, (req, res) => res.json({}));
  const r = await f.remove('a:b');
  assert.equal(r.status, 400);
  assert.equal(r.body.code, 'INVALID_USER_ID');
});
