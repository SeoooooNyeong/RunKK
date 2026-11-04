// docker run --rm --network none --mount type=bind,source=<test-dir>,target=/app/test,readonly --entrypoint node <image> test/container-smoke.mjs
// 로컬 가짜 분석 응답만 사용한다. 외부 AI 연결·크레딧 사용 없음.
import assert from 'node:assert/strict';
import http from 'node:http';
import {readFile} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import {setTimeout as pause} from 'node:timers/promises';
import sharp from 'sharp';
import {saveUserHead, userDirectory, atomicJson} from '../user-storage.mjs';
import path from 'node:path';

let calls = 0;
const provider = http.createServer(async (req, res) => {
  for await (const chunk of req) { /* 요청 본문을 로그로 남기지 않는다. */ }
  calls++;
  assert.equal(req.url, '/v1/chat/completions');
  const traits = Object.fromEntries(['hair', 'eyes', 'eyebrows', 'nose', 'mouth', 'face', 'skin_tone', 'accessories'].map(k => [k, '확인 어려움']));
  const content = JSON.stringify({traits: {...traits, hair_length: 'unknown'}, quality: {faceCount: 0, faceBox: null, visibility: {eyes: 'unknown', nose: 'unknown', mouth: 'unknown'}, lighting: 'adequate', sharpness: 'unknown', pose: 'unknown', clipped: false}});
  res.setHeader('Content-Type', 'application/json');res.end(JSON.stringify({choices: [{message: {content}}]}));
});
await new Promise(resolve => provider.listen(0, '127.0.0.1', resolve));
process.env.HOST = '0.0.0.0';process.env.PORT = '3000';
process.env.GMS_KEY = 'local-container-smoke-placeholder';
process.env.GMS_BASE_URL = `http://127.0.0.1:${provider.address().port}/v1`;
assert.equal(process.env.AI_CALLBACK_URL, undefined);
await import('../server.mjs');
await pause(50);
const base = 'http://127.0.0.1:3000/api/v1';
const health = await fetch('http://127.0.0.1:3000/api/health');assert.equal(health.status, 200);
const selfie = await readFile(new URL('../public/assets/head-default.png', import.meta.url));
const jobId = randomUUID();
async function submit() {
  const form = new FormData();form.append('userId', '42');form.append('clientRequestId', jobId);
  form.append('selfie', new Blob([selfie], {type: 'image/png'}), 'selfie.png');
  const r = await fetch(base + '/head-jobs', {method: 'POST', body: form, signal: AbortSignal.timeout(12000)});
  assert.equal(r.status, 202);const job = await r.json();assert.equal(job.jobId, jobId);return job;
}
const start = performance.now();
await Promise.all([submit(), submit(), submit()]);
const acceptanceMs = Math.round(performance.now() - start);
assert.ok(acceptanceMs < 12000);
let job;
const deadline = Date.now() + 5000;
while (Date.now() < deadline) {
  const r = await fetch(base + `/head-jobs/${jobId}?userId=42`);assert.equal(r.status, 200);job = await r.json();
  if (job.status === 'FAILED') break;
  await pause(50);
}
assert.equal(job.status, 'FAILED');assert.equal(job.error.code, 'SELFIE_QUALITY_REJECTED');
assert.equal(job.notification.status, 'NOT_CONFIGURED');assert.equal(job.notification.attempts, 0);
await submit();assert.equal(calls, 1);
for (const [route, status, code] of [
  [`/head-jobs/${jobId}/result?userId=42`, 409, 'JOB_NOT_READY'],
  [`/head-jobs/${jobId}?userId=43`, 404, 'JOB_NOT_FOUND'],
  ['/heads/latest?userId=43', 404, 'HEAD_NOT_FOUND'],
  ['/bodies/unknown', 404, 'BODY_NOT_FOUND'],
]) {const r = await fetch(base + route);assert.equal(r.status, status);assert.equal((await r.json()).code, code);}
const bodyResponse = await fetch(base + '/bodies/hoodie');assert.equal(bodyResponse.status, 200);
const {pack} = await bodyResponse.json();assert.equal(pack.mobile.neck.length, 2);assert.equal(pack.mobile.canvas.length, 2);
const [face, eyes, hair] = await Promise.all(['face-base-default', 'eyes-default', 'hair-default'].map(name => readFile(new URL(`../public/assets/${name}.png`, import.meta.url))));
await saveUserHead('/app', '42', {composite: selfie, right: selfie, face, eyes, hair}, {id: randomUUID(), alignment: {pivotX: .493, pivotY: .701}, mobile: {version: 'runkk-mobile-compose-v2'}});
const latest = await fetch(base + '/heads/latest?userId=42');assert.equal(latest.status, 200);
const result = await latest.json();
for (const key of ['faceImage', 'eyesImage', 'hairImage']) {
  const info = await sharp(Buffer.from(result[key].split(',')[1], 'base64')).metadata();assert.deepEqual([info.width, info.height], [1024, 1024]);
}
assert.match(result.mobile.renderFaceImage, /^data:image\/png;base64,/);assert.equal(result.mobile.anchor.length, 2);
// 저장된 성공 작업 fixture로 실제 result 라우트의 정규화와 응답도 검사한다.
const directory = path.join(userDirectory('/app', '42'), 'jobs', jobId);
await atomicJson(path.join(directory, 'result.json'), result);
await atomicJson(path.join(directory, 'job.json'), {...job, status: 'SUCCEEDED', headId: result.headId});
const success = await fetch(base + `/head-jobs/${jobId}/result?userId=42`);assert.equal(success.status, 200);
assert.equal((await success.json()).mobile.renderFaceImage, result.mobile.renderFaceImage);
assert.equal(calls, 1);
console.log(JSON.stringify({health: 200, acceptanceMs, concurrentDuplicates: 3, mockedAnalysisCalls: calls, paidImageCalls: 0, failedStatus: job.status, callback: job.notification.status, resultAndLatest: true, v12Mobile: true, bodyMobile: true}));
process.exit(0);
