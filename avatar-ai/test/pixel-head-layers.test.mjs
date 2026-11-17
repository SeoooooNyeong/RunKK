// sharp 어댑터 검증. 실제로 PNG 를 인코딩하고 그 바이트를 다시 읽어서
// 재합성 오차를 측정한다. 코어 테스트(head-layer-split.test.mjs)가 알고리즘을
// 검증하고, 여기서는 인코딩 경계를 검증한다.
//
// sharp 는 네이티브 모듈이라 설치되지 않은 환경에서는 건너뛴다.
import test from 'node:test';
import assert from 'node:assert/strict';
import {face, LANDMARKS, W} from './head-fixture.mjs';

let sharp = null;
try { sharp = (await import('sharp')).default; } catch { /* 미설치 */ }

const skip = sharp ? false : 'sharp 가 설치되지 않았어요';

async function masterPng() {
  const src = face();
  return sharp(Buffer.from(src.data.buffer, src.data.byteOffset, src.data.byteLength),
    {raw: {width: src.width, height: src.height, channels: 4}}).png().toBuffer();
}

test('PNG 왕복 후에도 재합성이 원본과 일치한다', {skip}, async () => {
  const {splitHeadPixels} = await import('../pixel-head-layers.mjs');
  const master = await masterPng();

  const {face: f, eyes, hair, report} = await splitHeadPixels(master, {
    landmarks: LANDMARKS, glassesPresent: true
  });

  for (const [name, buf] of Object.entries({face: f, eyes, hair})) {
    assert.ok(Buffer.isBuffer(buf) && buf.length > 0, `${name} 버퍼가 비었다`);
    const meta = await sharp(buf).metadata();
    assert.equal(meta.width, 1024, `${name} 폭`);
    assert.equal(meta.height, 1024, `${name} 높이`);
    assert.equal(meta.hasAlpha, true, `${name} 알파 채널`);
  }

  assert.ok(report.verified.maxAbsOnWhite <= 2,
    `PNG 왕복 후 오차 ${report.verified.maxAbsOnWhite}/255`);
  assert.equal(report.exact, true);
  assert.equal(report.warning, null);
  console.log('      PNG 왕복 오차: %s/255, PSNR %s dB, %dms',
    report.verified.maxAbsOnWhite, report.verified.psnrOnWhite, report.elapsedMs);
});

test('sharp 로 세 레이어를 겹친 결과도 원본과 일치한다', {skip}, async () => {
  // head-layers.splitHead 가 composite 를 만드는 방식 그대로 검증한다.
  // sharp 의 composite 가 프리멀티플라이를 끼얹지 않는지 확인하는 의미가 있다.
  const {splitHeadPixels} = await import('../pixel-head-layers.mjs');
  const master = await masterPng();
  const {face: f, eyes, hair} = await splitHeadPixels(master, {
    landmarks: LANDMARKS, glassesPresent: true
  });

  const composed = await sharp(f).composite([{input: eyes}, {input: hair}]).png().toBuffer();
  const a = await sharp(composed).ensureAlpha().raw().toBuffer();
  const b = await sharp(master).ensureAlpha().raw().toBuffer();

  let worst = 0, worstAt = 0;
  for (let i = 0; i < b.length; i += 4) {
    const alphaA = a[i + 3] / 255, alphaB = b[i + 3] / 255;
    for (let c = 0; c < 3; c++) {
      // 흰 배경에 올린 값으로 비교한다
      const got = (a[i + c] / 255) * alphaA + (1 - alphaA);
      const want = (b[i + c] / 255) * alphaB + (1 - alphaB);
      const d = Math.abs(got - want) * 255;
      if (d > worst) { worst = d; worstAt = i / 4; }
    }
  }
  assert.ok(worst <= 2,
    `sharp 합성 오차 ${worst.toFixed(2)}/255 @ (${worstAt % W},${Math.floor(worstAt / W)})`);
});

test('캔버스가 1024가 아니면 거절한다', {skip}, async () => {
  const {splitHeadPixels} = await import('../pixel-head-layers.mjs');
  const small = await sharp({create: {width: 512, height: 512, channels: 4,
    background: {r: 0, g: 0, b: 0, alpha: 0}}}).png().toBuffer();
  await assert.rejects(() => splitHeadPixels(small), /1024x1024/);
});

test('verifyHeadPixels 가 어긋난 레이어를 잡아낸다', {skip}, async () => {
  const {splitHeadPixels, verifyHeadPixels} = await import('../pixel-head-layers.mjs');
  const master = await masterPng();
  const {face: f, eyes, hair} = await splitHeadPixels(master, {
    landmarks: LANDMARKS, glassesPresent: true
  });

  const ok = await verifyHeadPixels(master, {face: f, eyes, hair});
  assert.ok(ok.maxAbsOnWhite <= 2, `정상 레이어인데 오차 ${ok.maxAbsOnWhite}`);

  // hair 를 3px 옮긴다. 기존 파이프라인이 겪던 드리프트와 같은 상황이다.
  const shifted = await sharp({create: {width: 1024, height: 1024, channels: 4,
    background: {r: 0, g: 0, b: 0, alpha: 0}}})
    .composite([{input: hair, left: 3, top: 0}]).png().toBuffer();
  const bad = await verifyHeadPixels(master, {face: f, eyes, hair: shifted});
  assert.ok(bad.maxAbsOnWhite > 20, `드리프트를 못 잡았다 (${bad.maxAbsOnWhite}/255)`);
});

test('안경이 없다고 알려주면 안경을 만들어내지 않는다', {skip}, async () => {
  const {splitHeadPixels} = await import('../pixel-head-layers.mjs');
  const master = await masterPng();
  const {report} = await splitHeadPixels(master, {
    landmarks: LANDMARKS, glassesPresent: false
  });
  assert.equal(report.parts.glasses, 0);
  assert.ok(report.verified.maxAbsOnWhite <= 2);
});
