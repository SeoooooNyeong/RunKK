import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, rm, readFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {randomUUID} from 'node:crypto';
import {FACE_ASSETS_VERSION, FACE_IMAGE_FIELDS, faceImages, assertFaceResult, savedFaceResult} from '../face-assets.mjs';
import {requireUserId, userDirectory, saveUserHead, latestUserHead, readUserHead} from '../user-storage.mjs';

const png = await readFile(new URL('../public/assets/eyes-default.png', import.meta.url));
const layers = () => ({composite: png, right: png, face: png, eyes: png, hair: png});
const metadata = () => ({id: randomUUID(), mobile: {version: 'runkk-mobile-compose-v2'}});
const incomplete = error => error.status === 409 && error.code === 'FACE_ASSETS_INCOMPLETE';

test('얼굴 응답은 몸 없이 정면·우측·얼굴·눈·헤어 PNG 5종을 반환한다', () => {
  const result = assertFaceResult({schemaVersion: FACE_ASSETS_VERSION, ...faceImages(layers())});
  assert.deepEqual(Object.keys(result).filter(key => key.endsWith('Image')), [...FACE_IMAGE_FIELDS]);
  for (const field of FACE_IMAGE_FIELDS) assert.deepEqual(Buffer.from(result[field].split(',')[1], 'base64'), png);
});

test('우측 누락·몸 이미지·중복 이미지 응답을 거부한다', () => {
  assert.throws(() => faceImages({...layers(), right: null}), incomplete);
  const result = {schemaVersion: FACE_ASSETS_VERSION, ...faceImages(layers())};
  assert.throws(() => assertFaceResult({...result, bodyImage: result.frontImage}), incomplete);
  assert.throws(() => assertFaceResult({...result, profile: {image: result.rightImage}}), incomplete);
});

test('userId 경로 이탈을 막고 사용자별 디렉터리를 분리한다', () => {
  for (const value of ['', '../other', 'a/b', 'a\\b', null, 'x'.repeat(129)]) {
    assert.throws(() => requireUserId(value), error => error.code === 'INVALID_USER_ID');
  }
  assert.equal(requireUserId('demo-avatar_1'), 'demo-avatar_1');
  assert.notEqual(userDirectory(tmpdir(), 'alice'), userDirectory(tmpdir(), 'bob'));
});

test('사용자별 최신 결과·우측 PNG를 보존하며 다른 사용자의 결과를 읽지 않는다', async t => {
  const root = await mkdtemp(path.join(tmpdir(), 'runkk-avatar-test-'));
  t.after(() => rm(root, {recursive: true, force: true}));
  const first = metadata(), second = metadata(), bob = metadata();
  await saveUserHead(root, 'alice', layers(), first);
  await saveUserHead(root, 'bob', layers(), bob);
  await saveUserHead(root, 'alice', layers(), second);
  assert.equal((await latestUserHead(root, 'alice')).metadata.id, second.id);
  assert.equal((await latestUserHead(root, 'bob')).metadata.id, bob.id);
  assert.equal((await readUserHead(root, 'alice', first.id)).metadata.id, first.id);
  assert.deepEqual((await latestUserHead(root, 'alice')).layers.right, png);
  await assert.rejects(readUserHead(root, 'bob', first.id), {code: 'ENOENT'});
  const result = await savedFaceResult(root, 'alice', () => {throw new Error('저장된 우측 PNG 조회 시 AI 호출 불필요');});
  assert.equal(result.userId, 'alice');
  assert.equal(result.headId, second.id);
  assertFaceResult(result);
});
