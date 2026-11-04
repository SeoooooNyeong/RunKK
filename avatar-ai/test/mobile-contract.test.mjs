import test from 'node:test';
import assert from 'node:assert/strict';
import sharp from 'sharp';
import {mobileHead} from '../mobile-composition.mjs';
import {FACE_ASSETS_VERSION, faceImages, assertFaceResult} from '../face-assets.mjs';
import {savedFaceResult} from '../face-assets.mjs';
import {saveUserHead} from '../user-storage.mjs';
import {mkdtemp, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import {randomUUID} from 'node:crypto';

test('v1.2 renderFaceImage는 같은 1024 캔버스에서 목만 제거한 PNG다', async () => {
  const pixels = Buffer.alloc(1024 * 1024 * 4);
  for (let y = 180; y < 850; y++) {
    const [left, right] = y < 700 ? [300, 724] : [470, 554];
    for (let x = left; x < right; x++) pixels.set([245, 207, 188, 255], (y * 1024 + x) * 4);
  }
  const png = await sharp(pixels, {raw: {width: 1024, height: 1024, channels: 4}}).png().toBuffer();
  const mobile = await mobileHead(png, {pivotX: .5, pivotY: 850 / 1024});
  assert.equal(mobile.neckRemoved, true);
  assert.match(mobile.renderFaceImage, /^data:image\/png;base64,/);
  const image = Buffer.from(mobile.renderFaceImage.split(',')[1], 'base64');
  const {data, info} = await sharp(image).raw().toBuffer({resolveWithObject: true});
  assert.deepEqual([info.width, info.height], [1024, 1024]);
  assert.equal(data[(800 * 1024 + 512) * 4 + 3], 0);
  assert.equal(data[(500 * 1024 + 512) * 4 + 3], 255);
  assert.equal(pixels[(800 * 1024 + 512) * 4 + 3], 255);
  assert.ok(mobile.anchor.every(value => value >= 0 && value <= 1));
  assert.deepEqual([...mobile.skinColor], [245, 207, 188]);
  assertFaceResult({schemaVersion: FACE_ASSETS_VERSION, ...faceImages({composite: png, right: png, face: png, eyes: png, hair: png}), mobile});
});

test('목 검출에 실패해도 renderFaceImage와 연결 기준점을 제공한다', async () => {
  const png = await sharp({create: {width: 1024, height: 1024, channels: 4, background: {r: 0, g: 0, b: 0, alpha: 0}}}).png().toBuffer();
  const mobile = await mobileHead(png, {pivotX: .5, pivotY: .9});
  assert.equal(mobile.neckRemoved, false);
  assert.deepEqual(mobile.anchor, [.5, .9]);
  assert.match(mobile.renderFaceImage, /^data:image\/png;base64,/);
});

test('이전 v2 저장 결과는 AI 없이 renderFaceImage를 보완하여 조회한다', async t => {
  const root = await mkdtemp(path.join(tmpdir(), 'runkk-mobile-test-'));
  t.after(async () => {
    assert.ok(path.resolve(root).startsWith(path.join(path.resolve(tmpdir()), 'runkk-mobile-test-')));
    await rm(root, {recursive: true, force: true});
  });
  const png = await sharp({create: {width: 1024, height: 1024, channels: 4, background: {r: 0, g: 0, b: 0, alpha: 0}}}).png().toBuffer();
  await saveUserHead(root, '42', {composite: png, right: png, face: png, eyes: png, hair: png}, {
    id: randomUUID(), alignment: {pivotX: .5, pivotY: .9}, mobile: {version: 'runkk-mobile-compose-v2'},
  });
  const result = await savedFaceResult(root, '42', () => {throw new Error('저장 결과 조회 시 AI 호출 금지');});
  assert.match(result.mobile.renderFaceImage, /^data:image\/png;base64,/);
  assert.deepEqual(result.mobile.canvas, [1024, 1024]);
  assertFaceResult(result);
});
