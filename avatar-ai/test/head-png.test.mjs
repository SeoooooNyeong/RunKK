import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import sharp from 'sharp';
import {HEAD_PNG_LAYER_KEYS, compressHeadPng, compressHeadLayers, headPngCompressionMode} from '../head-png.mjs';

// 피부 그라데이션·부드러운 알파 경계·투명 영역의 쓰레기 RGB를 가진 1024 RGBA 머리 레이어 대용
async function softLayer() {
  const w = 1024, h = 1024, raw = Buffer.alloc(w * h * 4);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const i = (y * w + x) * 4, d = Math.hypot(x - 512, y - 512);
    const alpha = Math.max(0, Math.min(255, Math.round((380 - d) * 4)));
    if (alpha === 0) { raw[i] = (x * 7) & 255; raw[i + 1] = (y * 13) & 255; raw[i + 2] = (x ^ y) & 255; raw[i + 3] = 0; continue; }
    raw[i] = 250 - Math.round(y / 30); raw[i + 1] = 220 - Math.round(x / 40); raw[i + 2] = 200 - Math.round(d / 20); raw[i + 3] = alpha;
  }
  return {raw, png: await sharp(raw, {raw: {width: w, height: h, channels: 4}}).png({compressionLevel: 6, adaptiveFiltering: false}).toBuffer()};
}
const PNG_SIGNATURE = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);
async function decoded(buffer) { return sharp(buffer).ensureAlpha().raw().toBuffer({resolveWithObject: true}); }
// 프리멀티플라이 RGBA PSNR. 완전 투명 픽셀의 RGB는 보이지 않으므로 차이로 치지 않는다
function premultipliedPsnr(a, b) {
  let se = 0;
  for (let i = 0; i < a.length; i += 4) {
    for (let c = 0; c < 3; c++) { const d = a[i + c] * a[i + 3] / 255 - b[i + c] * b[i + 3] / 255; se += d * d; }
    se += (a[i + 3] - b[i + 3]) ** 2;
  }
  const mse = se / a.length;
  return mse === 0 ? Infinity : 10 * Math.log10(255 * 255 / mse);
}
async function assertHeadPng(buffer) {
  assert.ok(buffer.subarray(0, 8).equals(PNG_SIGNATURE), 'PNG 서명이 아니다');
  const meta = await sharp(buffer).metadata();
  assert.equal(meta.format, 'png');
  assert.equal(meta.width, 1024);
  assert.equal(meta.height, 1024);
  assert.equal(meta.hasAlpha, true, '알파 채널이 사라졌다');
}

test('기본값은 꺼짐이고 알 수 없는 값도 꺼짐으로 둔다', () => {
  assert.equal(headPngCompressionMode({}), 'off');
  assert.equal(headPngCompressionMode({HEAD_PNG_COMPRESSION: ''}), 'off');
  assert.equal(headPngCompressionMode({HEAD_PNG_COMPRESSION: 'zip'}), 'off');
  assert.equal(headPngCompressionMode({HEAD_PNG_COMPRESSION: 'palette'}), 'palette');
  assert.equal(headPngCompressionMode({HEAD_PNG_COMPRESSION: ' LOSSLESS '}), 'lossless');
});

test('꺼짐이면 같은 버퍼를 그대로 돌려준다', async () => {
  const {png} = await softLayer();
  assert.equal(await compressHeadPng(png, {mode: 'off'}), png);
});

test('팔레트 압축은 1024 PNG·알파를 지키고 용량을 줄이며 화질 차이가 작다', async () => {
  const {png} = await softLayer();
  const out = await compressHeadPng(png, {mode: 'palette'});
  await assertHeadPng(out);
  assert.ok(out.length < png.length * 0.6, `감소가 작다: ${png.length} → ${out.length}`);
  const [a, b] = await Promise.all([decoded(png), decoded(out)]);
  // 팔레트는 RGBA를 함께 양자화하므로 알파도 조금 움직인다. 투명 배경이 보이게 되거나 불투명 면이 눈에 띄게 비치면 안 된다
  let transparent = 0, shown = 0, opaque = 0, thinned = 0, minOpaque = 255;
  for (let i = 3; i < a.data.length; i += 4) {
    if (a.data[i] === 0) { transparent++; if (b.data[i] > 8) shown++; }
    if (a.data[i] === 255) { opaque++; minOpaque = Math.min(minOpaque, b.data[i]); if (b.data[i] < 250) thinned++; }
  }
  assert.ok(shown / transparent < 0.0001, `투명 픽셀이 보이게 됐다: ${shown}`);
  assert.ok(thinned / opaque < 0.01, `불투명 픽셀이 비치게 됐다: ${thinned}/${opaque}`);
  assert.ok(minOpaque >= 224, `불투명 픽셀 최소 알파 ${minOpaque}`);
  assert.ok(premultipliedPsnr(a.data, b.data) > 40);
});

test('무손실 압축은 보이는 픽셀과 알파를 바꾸지 않고 용량을 줄인다', async () => {
  const {png} = await softLayer();
  const out = await compressHeadPng(png, {mode: 'lossless'});
  await assertHeadPng(out);
  assert.ok(out.length < png.length, `감소가 없다: ${png.length} → ${out.length}`);
  const [a, b] = await Promise.all([decoded(png), decoded(out)]);
  assert.equal(premultipliedPsnr(a.data, b.data), Infinity);
  for (let i = 3; i < a.data.length; i += 4) assert.equal(b.data[i], a.data[i]);
});

test('더 작아지지 않으면 원본을 그대로 둔다', async () => {
  const {png} = await softLayer();
  const once = await compressHeadPng(png, {mode: 'palette'});
  assert.equal(await compressHeadPng(once, {mode: 'palette'}), once);
});

test('1024×1024 RGBA PNG가 아니면 손대지 않는다', async () => {
  const jpeg = await sharp({create: {width: 1024, height: 1024, channels: 3, background: '#ffffff'}}).jpeg().toBuffer();
  const small = await sharp({create: {width: 64, height: 64, channels: 4, background: {r: 0, g: 0, b: 0, alpha: 0.5}}}).png().toBuffer();
  assert.equal(await compressHeadPng(jpeg, {mode: 'palette'}), jpeg);
  assert.equal(await compressHeadPng(small, {mode: 'palette'}), small);
});

test('기본 머리 그림(저장소 픽스처)도 1024·알파를 지키며 줄어든다', async () => {
  for (const name of ['face-base-default', 'eyes-default', 'hair-default']) {
    const png = await readFile(new URL(`../public/assets/${name}.png`, import.meta.url));
    const out = await compressHeadPng(png, {mode: 'palette'});
    await assertHeadPng(out);
    assert.ok(out.length < png.length * 0.5, `${name}: ${png.length} → ${out.length}`);
  }
});

test('레이어 묶음은 보여 주는 6레이어만 바꾸고 원본·합성은 두며 꺼짐이면 그대로다', async () => {
  const {png} = await softLayer();
  const layers = () => ({master: png, composite: png, right: png, face: png, eyes: png, hair: png, rightFace: png, rightEyes: png, rightHair: png});
  assert.deepEqual(HEAD_PNG_LAYER_KEYS, ['face', 'eyes', 'hair', 'rightFace', 'rightEyes', 'rightHair']);
  const off = layers();
  await compressHeadLayers(off, {mode: 'off'});
  for (const value of Object.values(off)) assert.equal(value, png);
  const on = layers();
  await compressHeadLayers(on, {mode: 'palette'});
  for (const key of HEAD_PNG_LAYER_KEYS) assert.ok(on[key].length < png.length, key);
  for (const key of ['master', 'composite', 'right']) assert.equal(on[key], png, key);
  const partial = {face: png, eyes: null};
  await compressHeadLayers(partial, {mode: 'palette'});
  assert.equal(partial.eyes, null);
});
