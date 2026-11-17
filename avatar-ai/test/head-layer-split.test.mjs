// 결정론적 레이어 분리 검증. sharp 도 네트워크도 쓰지 않는다.
//
// 합성 셀 셰이딩 얼굴(test/head-fixture.mjs)을 세 레이어로 쪼갠 뒤 다시 합성해
// 원본과 코드값 단위로 비교한다.
import test from 'node:test';
import assert from 'node:assert/strict';
import {splitHeadLayers, verifyRecomposition, LAYER_NAMES} from '../head-layer-split.mjs';
import {
  W, H, face, alphaAt, rgbAt,
  EYE, RIM, IRIS_R, CREASE_R, LANDMARKS,
  SKIN, IRIS
} from './head-fixture.mjs';

// ──────────────────────────────────────────────────────────────── 테스트

test('세 레이어를 다시 합성하면 원본과 코드값 단위로 같다', async () => {
  const src = face();
  const {layers, report} = await splitHeadLayers(src, {landmarks: LANDMARKS, glassesPresent: true});

  assert.equal(layers.length, 3);
  assert.deepEqual(layers.map(l => l.name), [...LAYER_NAMES]);

  assert.ok(report.maxAbsOnWhite <= 2,
    `흰 배경 최대 오차 ${report.maxAbsOnWhite}/255 (임계값 2). 최악 픽셀 ${JSON.stringify(report.worstPixel)}`);
  assert.ok(report.maxAbsAlpha <= 1,
    `알파 최대 오차 ${report.maxAbsAlpha}/255`);
  assert.ok(report.psnrOnWhite > 50, `PSNR ${report.psnrOnWhite}dB`);
  assert.equal(report.pixelsOver8, 0, '8코드값 넘는 픽셀이 있으면 분할이 틀렸다는 뜻이다');

  console.log('      재합성 오차: max %s/255, alpha %s/255, PSNR %s dB, >1LSB %d px',
    report.maxAbsOnWhite, report.maxAbsAlpha, report.psnrOnWhite, report.pixelsOver1);
});

test('모든 불투명 픽셀이 정확히 한 파트에 속한다', async () => {
  const {report} = await splitHeadLayers(face(), {landmarks: LANDMARKS, glassesPresent: true});
  assert.equal(report.partitionedPixels, report.opaquePixels);
  const sum = Object.values(report.parts).reduce((a, b) => a + b, 0);
  assert.equal(sum, report.opaquePixels);
});

test('안경이 눈 레이어로 넘어가지 않고, 눈 윤곽이 안경 레이어로 가지 않는다', async () => {
  const {layers, report} = await splitHeadLayers(face(), {landmarks: LANDMARKS, glassesPresent: true});
  const [faceL, eyesL, hairL] = layers;

  assert.ok(report.lensInteriorPixels > 20000,
    `렌즈 내부를 못 찾았다 (${report.lensInteriorPixels}px). 안경 분리가 불가능하다`);
  assert.ok(report.parts.glasses > 4000, `안경 파트가 너무 작다 (${report.parts.glasses}px)`);
  assert.ok(report.parts.eyes > 20000, `눈 파트가 너무 작다 (${report.parts.eyes}px)`);

  // 테 왼쪽 링 한가운데는 hair 레이어 소유
  const rimX = EYE[0].x - (RIM.rx - 3), rimY = EYE[0].y;
  assert.equal(alphaAt(hairL, rimX, rimY), 255, '안경테가 hair 레이어에 없다');
  assert.ok(alphaAt(eyesL, rimX, rimY) < 255, '안경테가 eyes 레이어로 넘어갔다');

  // 홍채 중심은 eyes 레이어 소유
  assert.equal(alphaAt(eyesL, EYE[0].x, EYE[0].y), 255, '홍채가 eyes 레이어에 없다');
  const iris = rgbAt(eyesL.data, EYE[0].x, EYE[0].y);
  assert.ok(Math.abs(iris[0] - IRIS[0]) <= 3 && Math.abs(iris[2] - IRIS[2]) <= 3,
    `eyes 레이어 홍채 색이 변했다: ${iris}`);

  // 눈 중심은 face 레이어에서 살색으로 메워져 있어야 한다 (구멍이 아니라)
  assert.equal(alphaAt(faceL, EYE[0].x, EYE[0].y), 255, 'face 레이어에 눈 모양 구멍이 남았다');
  const filled = rgbAt(faceL.data, EYE[0].x, EYE[0].y);
  assert.ok(Math.abs(filled[0] - SKIN[0]) <= 12 && Math.abs(filled[1] - SKIN[1]) <= 16,
    `눈 자리가 살색으로 메워지지 않았다: ${filled}`);
});

test('쌍꺼풀 선은 face 레이어에 남고 눈 레이어로 가지 않는다', async () => {
  const src = face();
  const {layers} = await splitHeadLayers(src, {landmarks: LANDMARKS, glassesPresent: true});
  const [faceL, eyesL] = layers;
  // 쌍꺼풀 호 한가운데. ring(e.x, e.y-6, 76, 80, 5) 이므로 x=e.x 에서 y 514..519
  const x = EYE[0].x, y = EYE[0].y - 83;
  assert.ok(src.data[((y * W + x) * 4)] < 120, '테스트 좌표가 쌍꺼풀 선 위가 아니다');
  assert.equal(alphaAt(faceL, x, y), 255, '쌍꺼풀 선이 face 레이어에 없다');
  assert.ok(alphaAt(eyesL, x, y) < 255, '쌍꺼풀 선이 eyes 레이어로 넘어갔다');
});

test('이마가 살색으로 복원되어 헤어 아래에 받침이 있다', async () => {
  const src = face();
  const {layers} = await splitHeadLayers(src, {landmarks: LANDMARKS, glassesPresent: true});
  const [faceL, , hairL] = layers;
  // 실제로 헤어가 덮고 있는 이마. 원본은 여기가 머리색이다.
  const x = 512, y = 380;
  assert.ok(src.data[((y * W + x) * 4)] < 80, '테스트 좌표가 헤어 아래가 아니다');
  assert.equal(alphaAt(hairL, x, y), 255, '헤어가 이 좌표를 덮고 있지 않다');
  assert.equal(alphaAt(faceL, x, y), 255, '헤어 아래 face 받침이 투명하다. 경계에 실금이 생긴다');
  const c = rgbAt(faceL.data, x, y);
  assert.ok(c[0] > 180 && c[1] > 140, `이마가 살색으로 복원되지 않았다: ${c}`);
});

test('안경이 없다고 알려주면 안경을 만들어내지 않는다', async () => {
  const {report} = await splitHeadLayers(face(), {landmarks: LANDMARKS, glassesPresent: false});
  assert.equal(report.parts.glasses, 0);
  assert.equal(report.lensInteriorPixels, 0);
  // 그래도 재합성은 정확해야 한다. 테 픽셀은 hair 또는 face 로 떨어진다.
  assert.ok(report.maxAbsOnWhite <= 2, `오차 ${report.maxAbsOnWhite}/255`);
});

test('랜드마크가 없어도 동작하고 재합성 정확성을 유지한다', async () => {
  const {report} = await splitHeadLayers(face(), {glassesPresent: true});
  assert.ok(report.bands.landmarkDriven === false);
  assert.ok(report.maxAbsOnWhite <= 2, `오차 ${report.maxAbsOnWhite}/255`);
});

test('right 프로파일은 렌즈 하나만 씨드로 써도 테 전체를 찾는다', async () => {
  const right = (await splitHeadLayers(face(), {landmarks: LANDMARKS, glassesPresent: true, direction: 'right'})).report;
  const front = (await splitHeadLayers(face(), {landmarks: LANDMARKS, glassesPresent: true})).report;

  // 렌즈 디스크는 하나만 쓴다
  assert.ok(right.lensInteriorPixels < front.lensInteriorPixels * 0.6,
    `렌즈를 둘 다 썼다 (right ${right.lensInteriorPixels} vs front ${front.lensInteriorPixels})`);

  // 그래도 테는 브리지로 이어진 하나의 연결 구조라, 한쪽에서 퍼져도 전체가 잡힌다.
  // 측면 그림에서 반대쪽 테가 살짝 보이는 경우를 놓치지 않는 성질이다.
  assert.ok(right.parts.glasses > front.parts.glasses * 0.9,
    `테가 끊겼다 (right ${right.parts.glasses} vs front ${front.parts.glasses})`);
  assert.ok(right.maxAbsOnWhite <= 2, `오차 ${right.maxAbsOnWhite}/255`);
});

test('외곽 안티에일리어싱 링은 한 레이어만 소유한다', async () => {
  const {layers} = await splitHeadLayers(face(), {landmarks: LANDMARKS, glassesPresent: true});
  const src = face();
  let shared = 0, checked = 0;
  for (let i = 0; i < W * H; i++) {
    const a0 = src.data[i * 4 + 3];
    if (a0 === 0 || a0 === 255) continue;
    checked++;
    let partial = 0;
    for (const l of layers) if (l.data[i * 4 + 3] > 0) partial++;
    if (partial > 1) shared++;
  }
  assert.ok(checked > 1000, '검사할 외곽 링 픽셀이 있어야 한다');
  assert.equal(shared, 0, `${shared}개 링 픽셀을 두 레이어가 나눠 가졌다. 실루엣 후광이 생긴다`);
});

test('캔버스 크기와 좌표가 보존된다', async () => {
  const {layers} = await splitHeadLayers(face(), {landmarks: LANDMARKS, glassesPresent: true});
  for (const l of layers) {
    assert.equal(l.width, W);
    assert.equal(l.height, H);
    assert.equal(l.data.length, W * H * 4);
  }
});

test('잘못된 입력 버퍼는 거절한다', async () => {
  await assert.rejects(
    () => splitHeadLayers({data: new Uint8ClampedArray(16), width: 4, height: 4}),
    /RGBA 원본 버퍼/);
});

test('verifyRecomposition 은 레이어가 틀렸을 때 실제로 잡아낸다', async () => {
  const src = face();
  const {layers} = await splitHeadLayers(src, {landmarks: LANDMARKS, glassesPresent: true});
  const broken = layers.map(l => Uint8ClampedArray.from(l.data));
  // hair 레이어를 3px 옮긴다. 기존 파이프라인이 겪던 드리프트와 같은 상황이다.
  const shifted = new Uint8ClampedArray(broken[2].length);
  for (let y = 0; y < H; y++) {
    for (let x = 3; x < W; x++) {
      const to = (y * W + x) * 4, from = (y * W + x - 3) * 4;
      for (let c = 0; c < 4; c++) shifted[to + c] = broken[2][from + c];
    }
  }
  const bad = verifyRecomposition([broken[0], broken[1], shifted], src.data, W, H);
  assert.ok(bad.maxAbsOnWhite > 20, `드리프트를 못 잡았다 (${bad.maxAbsOnWhite}/255)`);
  assert.ok(bad.pixelsOver8 > 1000);
});

test('헤어 경계에 어두운 후광이 face 레이어로 새지 않는다', async () => {
  // 선과 살색 사이 안티에일리어싱 픽셀이 face 로 떨어지면, 헤어를 교체했을 때
  // 옛 헤어 자리에 1px 어두운 테두리가 남는다. 히스테리시스가 막아야 한다.
  const src = face();
  const {layers} = await splitHeadLayers(src, {landmarks: LANDMARKS, glassesPresent: true});
  const [faceL, , hairL] = layers;

  // 이마 중앙에서 헤어/피부 경계를 수직으로 가로지른다
  const x = 512;
  let boundary = -1;
  for (let y = 300; y < 520; y++) {
    if (alphaAt(hairL, x, y) === 255 && alphaAt(hairL, x, y + 6) === 0) { boundary = y; break; }
  }
  assert.ok(boundary > 0, '헤어/피부 경계를 찾지 못했다');

  // 경계 바로 아래 face 레이어는 살색이어야 한다. 머리색이 섞이면 안 된다.
  let worst = null;
  for (let y = boundary + 1; y <= boundary + 8; y++) {
    const c = rgbAt(faceL.data, x, y);
    if (!worst || c[0] < worst.c[0]) worst = {y, c};
  }
  assert.ok(worst.c[0] > 190,
    `헤어 경계 아래 face 레이어가 어둡다: y=${worst.y} ${worst.c} (머리색이 섞였다)`);
});

test('각 레이어 단독으로도 검은 프린지가 없다', async () => {
  // 소프트 알파가 기존 받침 밖으로 밀려난 픽셀에 색을 연장하지 않으면 그
  // 픽셀이 검게 합성된다. 알파가 있는 모든 픽셀에 쓸 만한 색이 있어야 한다.
  const {layers} = await splitHeadLayers(face(), {landmarks: LANDMARKS, glassesPresent: true});
  for (const layer of layers) {
    let black = 0;
    for (let i = 0; i < W * H; i++) {
      const p = i * 4, a = layer.data[p + 3];
      if (a < 8 || a === 255) continue;          // 소프트 엣지만 본다
      const max = Math.max(layer.data[p], layer.data[p + 1], layer.data[p + 2]);
      if (max < 6) black++;                      // 알파는 있는데 색이 거의 검정
    }
    // 실제 검은 선(속눈썹·테)의 소프트 엣지는 정상이므로 0 을 요구할 수 없다.
    // 전체 소프트 엣지 대비 비율로 본다.
    let soft = 0;
    for (let i = 0; i < W * H; i++) {
      const a = layer.data[i * 4 + 3];
      if (a >= 8 && a !== 255) soft++;
    }
    assert.ok(black <= soft * 0.25,
      `${layer.name}: 소프트 엣지 ${soft}px 중 ${black}px 가 검정. 색 연장이 빠졌다`);
  }
});

test('분할 중에 이벤트 루프가 막히지 않는다', async () => {
  // 1024x1024 분할은 CPU 로 1.3초쯤 쓴다. 한 덩어리로 돌리면 Express 응답과
  // head-jobs 워커 폴링(1초 간격)이 그만큼 멈춘다. pixel-layer-registration
  // 의 같은 이름 테스트와 같은 기준을 쓴다.
  const src = face();
  let last = performance.now(), longest = 0;
  const timer = setInterval(() => {
    const t = performance.now();
    longest = Math.max(longest, t - last);
    last = t;
  }, 1);
  try {
    await splitHeadLayers(src, {landmarks: LANDMARKS, glassesPresent: true});
  } finally {
    clearInterval(timer);
  }
  assert.ok(longest < 500, `이벤트 루프가 ${Math.round(longest)}ms 막혔다`);
});

test('onStage: null 이면 양보하지 않고 동기적으로 끝낸다', async () => {
  // 배치 재분할처럼 응답성이 필요 없는 경로에서 양보 비용을 뺄 수 있어야 한다.
  const {report} = await splitHeadLayers(face(), {
    landmarks: LANDMARKS, glassesPresent: true, onStage: null
  });
  assert.ok(report.maxAbsOnWhite <= 2);
});

test('onStage 훅이 단계 이름을 순서대로 알려준다', async () => {
  const seen = [];
  await splitHeadLayers(face(), {
    landmarks: LANDMARKS, glassesPresent: true,
    onStage: label => { seen.push(label); }
  });
  assert.ok(seen.length >= 7, `단계 훅이 ${seen.length}번만 불렸다`);
  assert.deepEqual(seen.slice(0, 3), ['glasses', 'segment', 'amodal']);
  assert.ok(seen.includes('quantize:face'));
  assert.ok(seen.includes('quantize:hair'));
  assert.equal(seen.at(-1), 'exactify');
});
