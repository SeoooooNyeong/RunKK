// head-layer-split.mjs
//
// 완성된 머리 PNG 하나를 face / eyes / hair 세 레이어로 쪼갠다. 모델 호출도,
// 네트워크도, 외부 패키지도 쓰지 않는다. 원본 RGBA 버퍼를 받아 RGBA 버퍼
// 셋을 돌려주므로 sharp 없이도 테스트할 수 있다.
//
// 왜 이렇게 바꿨나
// ----------------
// 생성 모델에 "안경만 남겨줘"라고 하면 모델은 잘라내지 않고 다시 그린다. 선
// 두께와 색이 코드값 단위로 틀어지고, 세 장을 겹치면 윤곽이 두 겹으로 보인다.
// 프롬프트로는 없앨 수 없는 오차다. 여기서는 원본 RGB를 건드리지 않고 알파와
// 마스크만 계산한다. 생성(복원)은 위 레이어에 가려져 최종 결과에 보이지 않는
// 영역에만 쓴다.
//
// 이 그림에서 어려운 점은 헤어·안경테·눈썹·속눈썹·눈윤곽·얼굴 라인아트가
// 전부 같은 검정이고 서로 닿아 있다는 것이다. 색만으로는 못 나눈다. 두 가지
// 구조적 단서를 쓴다.
//
//   포위  안경 렌즈 테두리는 닫힌 곡선이라 검정 마스크 안의 "구멍"으로
//         나타난다. 그 구멍에서 씨드를 뿌리면 색을 몰라도 테를 찾는다.
//   두께  거리 변환으로 국소 반폭을 재면 테·속눈썹·라인아트는 얇고 머리
//         덩어리는 두껍다. 두꺼운 픽셀을 통과하지 못하는 플러드 필이라서
//         안경 다리가 머리 뒤로 들어가도 거기서 멈춘다.
//
// 정확성은 추정이 아니라 수식으로 보장한다. 각 레이어의 프리멀티플라이드 색을
// 아래 합성 결과의 잔차로 치환하면
//
//     P_i  =  O_pm  -  (1 - A_i) * U_i_pm
//
// `over` 합성이 P_i + (1-A_i)*U_i = O_pm 을 구성적으로 만족한다. 알파 추정이
// 틀려도 재합성은 정확하다. 프리멀티플라이드 공간이라 알파로 나누는 연산이
// 없어서 소프트 엣지에서도 불안정하지 않다.

export const LAYER_NAMES = Object.freeze(['face', 'eyes', 'hair']);
export const PART_NAMES = Object.freeze(['face', 'eyes', 'hair', 'brows', 'glasses']);

// 레이어별 소속 파트. hair 레이어가 눈썹과 안경을 함께 갖는 기존 계약을 유지한다.
export const LAYER_PARTS = Object.freeze({
  face: ['face'],
  eyes: ['eyes'],
  hair: ['hair', 'brows', 'glasses']
});

export const DEFAULTS = Object.freeze({
  // 알파/배경
  alphaEps: 2,              // 0..255. 이하는 빈 픽셀로 본다
  opaqueMin: 253,           // 이상은 완전 불투명으로 본다

  // 명도 밴드 (HSV V, 0..255)
  darkV: 107,               // 헤어·라인아트·안경·속눈썹
  midV: 189,                // 홍채 갈색, 머리 하이라이트
  midSMin: 46,              // 중간톤은 채도가 있어야 한다
  whiteVMin: 235,           // 반사 하이라이트
  whiteSMax: 26,

  // 히스테리시스. 선과 살색 사이 안티에일리어싱 픽셀은 어느 밴드에도 안 들어가
  // 기본값(face)으로 떨어진다. 머리 경계 1px 링이 face 레이어로 가면 헤어를
  // 교체했을 때 어두운 테두리가 남는다. 확실한 dark 에 붙은 애매한 픽셀만
  // 끌어올린다. 범위를 좁게 묶어서 홍채 본체는 건드리지 않는다.
  hystV: 160,
  hystBand: 2,

  // 두께 게이트 (픽셀, 1024 캔버스 기준 반폭)
  rimMaxThick: 7,           // 안경테 반폭 상한
  rimBoundaryThinFrac: 0.75,// 렌즈 후보의 바깥 경계가 테다워야 하는 비율
  hairMinThick: 11,         // 머리 덩어리 반폭 하한
  browThickMin: 3,
  browThickMax: 12,

  // amodal 복원
  holeDilate: 3,            // 구멍을 넓혀서 채운다. 덮는 레이어의 소프트 엣지 밑에
                            // 항상 올바른 색이 있어야 한다
  rimBridge: 9,             // 눈을 가로지른 안경테 밑에 눈을 복원할 범위

  // 소프트 엣지
  softBand: 2,

  // 면적 필터
  minPartArea: 24,
  maxRimAreaFrac: 0.06,     // 테 플러드가 머리까지 먹었는지 감지하는 안전장치

  // 검수 임계값 (0..255)
  maxErrorLsb: 2
});

// ───────────────────────────────────────────────────────────── 기본 자료구조

const sq = v => v * v;

// 1024x1024 분할은 CPU 로 1.3초쯤 쓴다. 한 번에 다 돌리면 Express 응답과
// head-jobs 워커 폴링이 그만큼 멈춘다. pixel-layer-registration.mjs 가 쓰는
// 것과 같은 방식으로 단계 사이에 이벤트 루프를 놓아준다. 기본값이 양보하는
// 쪽이어야 production 에서 빠뜨릴 일이 없다.
const yieldToEventLoop = () => new Promise(resolve => setImmediate(resolve));

function maskNot(mask) {
  const out = new Uint8Array(mask.length);
  for (let i = 0; i < mask.length; i++) out[i] = mask[i] ? 0 : 1;
  return out;
}

function maskAnd(a, b) {
  const out = new Uint8Array(a.length);
  for (let i = 0; i < a.length; i++) out[i] = a[i] && b[i] ? 1 : 0;
  return out;
}

function maskAndNot(a, b) {
  const out = new Uint8Array(a.length);
  for (let i = 0; i < a.length; i++) out[i] = a[i] && !b[i] ? 1 : 0;
  return out;
}

function maskOr(...masks) {
  const out = new Uint8Array(masks[0].length);
  for (const m of masks) for (let i = 0; i < m.length; i++) if (m[i]) out[i] = 1;
  return out;
}

function maskCount(mask) {
  let n = 0;
  for (let i = 0; i < mask.length; i++) if (mask[i]) n++;
  return n;
}

// ───────────────────────────────────────────── 정확 유클리드 거리 변환 (EDT)

// Felzenszwalb & Huttenlocher 의 1차원 포물선 하단 포락선. 행·열 두 번 돌면
// 정확한 제곱 유클리드 거리가 나온다. 근사 체머퍼 마스크와 달리 대각 방향에서
// 오차가 없어서, 두께 게이트 임계값을 픽셀 단위로 신뢰할 수 있다.
function dt1d(f, n, d, v, z) {
  let k = 0;
  v[0] = 0;
  z[0] = -Infinity;
  z[1] = Infinity;
  for (let q = 1; q < n; q++) {
    let s = 0;
    for (;;) {
      const denom = 2 * q - 2 * v[k];
      s = (f[q] + sq(q) - (f[v[k]] + sq(v[k]))) / denom;
      if (s <= z[k] && k > 0) k--;
      else break;
    }
    k++;
    v[k] = q;
    z[k] = s;
    z[k + 1] = Infinity;
  }
  k = 0;
  for (let q = 0; q < n; q++) {
    while (z[k + 1] < q) k++;
    d[q] = sq(q - v[k]) + f[v[k]];
  }
}

// mask 가 켜진 가장 가까운 픽셀까지의 거리.
function distanceTo(mask, w, h) {
  const BIG = 1e12;
  const grid = new Float64Array(w * h);
  for (let i = 0; i < grid.length; i++) grid[i] = mask[i] ? 0 : BIG;

  const n = Math.max(w, h);
  const f = new Float64Array(n);
  const d = new Float64Array(n);
  const v = new Int32Array(n + 1);
  const z = new Float64Array(n + 2);

  for (let y = 0; y < h; y++) {
    const row = y * w;
    for (let x = 0; x < w; x++) f[x] = grid[row + x];
    dt1d(f, w, d, v, z);
    for (let x = 0; x < w; x++) grid[row + x] = d[x];
  }
  for (let x = 0; x < w; x++) {
    for (let y = 0; y < h; y++) f[y] = grid[y * w + x];
    dt1d(f, h, d, v, z);
    for (let y = 0; y < h; y++) grid[y * w + x] = d[y];
  }

  const out = new Float32Array(w * h);
  for (let i = 0; i < out.length; i++) out[i] = Math.sqrt(grid[i]);
  return out;
}

// mask 내부 픽셀의 국소 반폭. 바깥까지의 거리이므로 선의 절반 두께다.
function halfThickness(mask, w, h) {
  return distanceTo(maskNot(mask), w, h);
}

// 작은 반지름은 EDT 를 돌리지 않고 8-이웃 팽창을 반복한다. 결과는 디스크가
// 아니라 사각형이지만 r<=4 에서 차이가 1px 미만이고, 씨드 띠나 히스테리시스
// 범위처럼 조금 넉넉한 쪽이 안전한 용도에만 쓴다. 전체 분할에서 이 경로가
// 20번 넘게 불려서 EDT 로 두면 비용이 가장 크다.
const SMALL_RADIUS = 4;

function dilateSmall(mask, radius, w, h) {
  let cur = Uint8Array.from(mask);
  for (let step = 0; step < radius; step++) {
    const next = Uint8Array.from(cur);
    for (let y = 0; y < h; y++) {
      const row = y * w;
      for (let x = 0; x < w; x++) {
        const i = row + x;
        if (!cur[i]) continue;
        const x0 = x > 0 ? -1 : 0, x1 = x < w - 1 ? 1 : 0;
        const y0 = y > 0 ? -1 : 0, y1 = y < h - 1 ? 1 : 0;
        for (let dy = y0; dy <= y1; dy++) {
          for (let dx = x0; dx <= x1; dx++) next[i + dy * w + dx] = 1;
        }
      }
    }
    cur = next;
  }
  return cur;
}

function dilate(mask, radius, w, h) {
  if (radius <= 0) return Uint8Array.from(mask);
  if (radius <= SMALL_RADIUS) return dilateSmall(mask, radius, w, h);
  const dist = distanceTo(mask, w, h);
  const out = new Uint8Array(mask.length);
  for (let i = 0; i < out.length; i++) out[i] = dist[i] <= radius ? 1 : 0;
  return out;
}

function erode(mask, radius, w, h) {
  if (radius <= 0) return Uint8Array.from(mask);
  if (radius <= SMALL_RADIUS) return maskAndNot(mask, dilateSmall(maskNot(mask), radius, w, h));
  const dist = distanceTo(maskNot(mask), w, h);
  const out = new Uint8Array(mask.length);
  for (let i = 0; i < out.length; i++) out[i] = mask[i] && dist[i] > radius ? 1 : 0;
  return out;
}

// 반지름 r 디스크가 들어갈 수 있는 부분만 남긴다. 두꺼운 덩어리는 살아남고
// 얇은 구조는 사라진다.
function opening(mask, radius, w, h) {
  return maskAnd(dilate(erode(mask, radius, w, h), radius, w, h), mask);
}

// ─────────────────────────────────────────── 가장 가까운 known 픽셀 (8SSEDT)

// 경계 링의 색은 발명하지 않고 가장 가까운 실제 픽셀을 복사한다. 깊은 내부는
// 완전히 가려지므로 대충이어도 결과가 같다.
function nearestKnown(known, w, h, passes = 2) {
  const n = w * h;
  const nx = new Int32Array(n).fill(-1);
  const ny = new Int32Array(n).fill(-1);
  const d2 = new Float64Array(n).fill(Infinity);

  for (let y = 0, i = 0; y < h; y++) {
    for (let x = 0; x < w; x++, i++) {
      if (known[i]) { nx[i] = x; ny[i] = y; d2[i] = 0; }
    }
  }

  const relax = (i, j, x, y) => {
    const px = nx[j];
    if (px < 0) return;
    const dd = sq(x - px) + sq(y - ny[j]);
    if (dd < d2[i]) { d2[i] = dd; nx[i] = px; ny[i] = ny[j]; }
  };

  for (let pass = 0; pass < passes; pass++) {
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        const i = y * w + x;
        if (x > 0) relax(i, i - 1, x, y);
        if (y > 0) relax(i, i - w, x, y);
        if (y > 0 && x > 0) relax(i, i - w - 1, x, y);
        if (y > 0 && x < w - 1) relax(i, i - w + 1, x, y);
      }
    }
    for (let y = h - 1; y >= 0; y--) {
      for (let x = w - 1; x >= 0; x--) {
        const i = y * w + x;
        if (x < w - 1) relax(i, i + 1, x, y);
        if (y < h - 1) relax(i, i + w, x, y);
        if (y < h - 1 && x < w - 1) relax(i, i + w + 1, x, y);
        if (y < h - 1 && x > 0) relax(i, i + w - 1, x, y);
      }
    }
  }
  return { nx, ny };
}

// ────────────────────────────────────────────── 연결 요소 / 플러드 / 구멍

function labelComponents(mask, w, h) {
  const labels = new Int32Array(w * h).fill(0);
  const stats = [null];
  const stack = new Int32Array(w * h);
  let next = 1;

  for (let seed = 0; seed < labels.length; seed++) {
    if (!mask[seed] || labels[seed]) continue;
    const id = next++;
    let top = 0, area = 0;
    let minX = w, minY = h, maxX = -1, maxY = -1, sumX = 0, sumY = 0;
    stack[top++] = seed;
    labels[seed] = id;

    while (top > 0) {
      const i = stack[--top];
      const x = i % w, y = (i - x) / w;
      area++; sumX += x; sumY += y;
      if (x < minX) minX = x;
      if (x > maxX) maxX = x;
      if (y < minY) minY = y;
      if (y > maxY) maxY = y;

      for (let dy = -1; dy <= 1; dy++) {
        const ny2 = y + dy;
        if (ny2 < 0 || ny2 >= h) continue;
        for (let dx = -1; dx <= 1; dx++) {
          const nx2 = x + dx;
          if ((dx === 0 && dy === 0) || nx2 < 0 || nx2 >= w) continue;
          const j = ny2 * w + nx2;
          if (mask[j] && !labels[j]) { labels[j] = id; stack[top++] = j; }
        }
      }
    }
    stats.push({ id, area, minX, minY, maxX, maxY, cx: sumX / area, cy: sumY / area });
  }
  return { labels, count: next - 1, stats };
}

// seeds 에서 출발해 passable 안에서만 퍼진 영역.
function floodFromSeeds(passable, seeds, w, h) {
  const { labels, count } = labelComponents(passable, w, h);
  if (!count) return new Uint8Array(w * h);
  const hit = new Uint8Array(count + 1);
  for (let i = 0; i < seeds.length; i++) {
    if (seeds[i] && labels[i]) hit[labels[i]] = 1;
  }
  const out = new Uint8Array(w * h);
  for (let i = 0; i < out.length; i++) if (labels[i] && hit[labels[i]]) out[i] = 1;
  return out;
}

function dropSmall(mask, minArea, w, h) {
  const { labels, count, stats } = labelComponents(mask, w, h);
  const keep = new Uint8Array(count + 1);
  for (let id = 1; id <= count; id++) if (stats[id].area >= minArea) keep[id] = 1;
  const out = new Uint8Array(mask.length);
  for (let i = 0; i < out.length; i++) if (labels[i] && keep[labels[i]]) out[i] = 1;
  return out;
}

// mask 로 둘러싸인 배경 영역. 안경 렌즈 내부가 여기로 나온다.
function enclosedRegions(mask, w, h) {
  const open = maskNot(mask);
  const seen = new Uint8Array(w * h);
  const stack = new Int32Array(w * h);
  let top = 0;

  const push = i => { if (open[i] && !seen[i]) { seen[i] = 1; stack[top++] = i; } };
  for (let x = 0; x < w; x++) { push(x); push((h - 1) * w + x); }
  for (let y = 0; y < h; y++) { push(y * w); push(y * w + w - 1); }

  while (top > 0) {
    const i = stack[--top];
    const x = i % w, y = (i - x) / w;
    if (x > 0) push(i - 1);
    if (x < w - 1) push(i + 1);
    if (y > 0) push(i - w);
    if (y < h - 1) push(i + w);
  }

  const out = new Uint8Array(w * h);
  for (let i = 0; i < out.length; i++) out[i] = open[i] && !seen[i] ? 1 : 0;
  return out;
}

function fillHoles(mask, w, h) {
  const holes = enclosedRegions(mask, w, h);
  const out = new Uint8Array(mask.length);
  for (let i = 0; i < out.length; i++) out[i] = mask[i] || holes[i] ? 1 : 0;
  return out;
}

// ──────────────────────────────────────────────────────────────── 명도 밴드

function tonalBands(rgba, w, h, opt) {
  const n = w * h;
  const opaque = new Uint8Array(n);
  const core = new Uint8Array(n);
  const dark = new Uint8Array(n);
  const mid = new Uint8Array(n);
  const white = new Uint8Array(n);
  const maybeDark = new Uint8Array(n);

  for (let i = 0, p = 0; i < n; i++, p += 4) {
    const a = rgba[p + 3];
    if (a <= opt.alphaEps) continue;
    opaque[i] = 1;
    if (a >= opt.opaqueMin) core[i] = 1;

    const r = rgba[p], g = rgba[p + 1], b = rgba[p + 2];
    const v = Math.max(r, g, b);
    const min = Math.min(r, g, b);
    const s = v === 0 ? 0 : Math.round(((v - min) * 255) / v);

    if (v < opt.darkV) dark[i] = 1;
    else if (v < opt.midV && s >= opt.midSMin) mid[i] = 1;
    if (v >= opt.whiteVMin && s <= opt.whiteSMax) white[i] = 1;
    if (v < opt.hystV) maybeDark[i] = 1;
  }

  // 히스테리시스: 확실한 dark 에 hystBand 안쪽으로 붙어 있는 애매한 픽셀만
  // dark 로 올린다. 범위를 안 묶으면 홍채 전체가 눈 윤곽을 통해 dark 로
  // 번져서 mid 기반 홍채 검출이 깨진다.
  const near = dilate(dark, opt.hystBand, w, h);
  for (let i = 0; i < n; i++) {
    if (dark[i] || !maybeDark[i] || !near[i]) continue;
    dark[i] = 1;
    mid[i] = 0;
  }

  return { opaque, core, dark, mid, white };
}

// ──────────────────────────────────────────────────────── 랜드마크 기반 밴드

// head-layers.mjs 의 analyzeHeadRegions 가 이미 1024 캔버스 픽셀 좌표로
// 랜드마크를 준다. 고정 비율 대신 그 값으로 밴드를 잡으면 머리 크기나 화면
// 위치가 달라도 따라간다. 없으면 비율로 떨어진다.
function searchBands(landmarks, w, h) {
  const fallback = {
    eyeTop: Math.round(0.44 * h), eyeBottom: Math.round(0.74 * h),
    browTop: Math.round(0.38 * h), browBottom: Math.round(0.58 * h),
    eyePoints: [], eyeRadius: Math.round(0.085 * h)
  };
  const point = name => {
    const p = landmarks?.[name];
    const x = Number(p?.x), y = Number(p?.y);
    return Number.isFinite(x) && Number.isFinite(y) && x >= 0 && x < w && y >= 0 && y < h
      ? { x: Math.round(x), y: Math.round(y) } : null;
  };
  const left = point('leftEye'), right = point('rightEye');
  const chin = point('chin'), brow = point('leftEyebrow') || point('rightEyebrow');
  if (!left && !right) return fallback;

  const eyes = [left, right].filter(Boolean);
  const eyeY = eyes.reduce((s, p) => s + p.y, 0) / eyes.length;
  // 눈~턱 거리를 얼굴 크기 척도로 쓴다. 치비 비율에서 눈 영역 반경은 그 25% 정도다.
  const span = chin ? Math.max(40, chin.y - eyeY) : 0.2 * h;
  const radius = Math.round(Math.max(24, Math.min(0.18 * h, span * 0.42)));

  return {
    eyeTop: Math.max(0, Math.round(eyeY - span * 0.75)),
    eyeBottom: Math.min(h - 1, Math.round(eyeY + span * 0.55)),
    browTop: Math.max(0, Math.round((brow ? brow.y : eyeY - span * 0.45) - span * 0.5)),
    browBottom: Math.min(h - 1, Math.round(eyeY - span * 0.04)),
    eyePoints: eyes,
    eyeRadius: radius
  };
}

function bandMask(top, bottom, w, h) {
  const out = new Uint8Array(w * h);
  for (let y = Math.max(0, top); y <= Math.min(h - 1, bottom); y++) out.fill(1, y * w, y * w + w);
  return out;
}

function discMask(points, radius, w, h) {
  const out = new Uint8Array(w * h);
  if (!points.length) return out;
  for (const p of points) {
    const r = Math.ceil(radius);
    for (let y = Math.max(0, p.y - r); y <= Math.min(h - 1, p.y + r); y++) {
      for (let x = Math.max(0, p.x - r); x <= Math.min(w - 1, p.x + r); x++) {
        if (sq(x - p.x) + sq(y - p.y) <= sq(radius)) out[y * w + x] = 1;
      }
    }
  }
  return out;
}

// ────────────────────────────────────────── 1단계: 안경 (포위 + 두께 게이트)

// 렌즈 안쪽은 "테로 둘러싸인 디스크 전체"여야 한다. 포위된 열린 영역을 그대로
// 쓰면 안 된다. 눈 윤곽선이 렌즈 내부를 둘로 쪼개서, 테에 붙은 고리 모양만
// 잡히고 눈은 빠진다. 그 상태로 테를 찾으면 눈 윤곽선까지 안경으로 끌려온다.
// 고리를 fillHoles 로 메워 디스크로 만든 뒤에 쓴다. 눈 안쪽처럼 단일 연결
// 영역은 메워도 그대로이므로, 디스크 면적이 큰 쪽이 렌즈다.
function findLensInteriors(dark, thick, search, maxLenses, opt, w, h) {
  const enclosed = enclosedRegions(dark, w, h);
  if (!maskCount(enclosed)) return new Uint8Array(w * h);

  const { labels, count, stats } = labelComponents(enclosed, w, h);
  const shortlist = [];
  for (let id = 1; id <= count; id++) {
    const s = stats[id];
    if (s.area < opt.minPartArea) continue;
    if (s.cy < search.eyeTop || s.cy > search.eyeBottom) continue;
    shortlist.push(s);
  }
  if (!shortlist.length) return new Uint8Array(w * h);

  // fillHoles 는 캔버스 전체를 훑으므로 후보를 먼저 줄인다.
  shortlist.sort((a, b) => b.area - a.area);

  const ranked = [];
  for (const s of shortlist.slice(0, 8)) {
    const comp = new Uint8Array(w * h);
    for (let i = 0; i < comp.length; i++) if (labels[i] === s.id) comp[i] = 1;
    const disc = fillHoles(comp, w, h);

    // 걸러내야 하는 가짜 후보: 머리카락·눈썹·테 사이에 갇힌 피부 주머니.
    // 이 그림에서 그런 주머니가 실제 렌즈보다 넓게 나와 랭킹을 이겨버렸고,
    // 그 바람에 반대쪽 눈이 통째로 빠졌다. 두 가지로 판별한다.

    // (1) 진짜 렌즈는 눈 중심을 품는다. 피부 주머니는 품지 않는다.
    if (search.eyePoints.length) {
      const holds = search.eyePoints.some(p => disc[p.y * w + p.x] === 1);
      if (!holds) continue;
    }

    // (2) 진짜 렌즈의 바깥 경계는 전부 얇은 테다. 피부 주머니의 경계에는
    //     두꺼운 머리카락이 섞인다. 랜드마크가 없을 때의 유일한 판별이다.
    const boundary = maskAnd(maskAndNot(dilate(disc, 2, w, h), disc), dark);
    let total = 0, thin = 0;
    for (let i = 0; i < boundary.length; i++) {
      if (!boundary[i]) continue;
      total++;
      if (thick[i] <= opt.rimMaxThick) thin++;
    }
    if (total < 32 || thin / total < opt.rimBoundaryThinFrac) continue;

    ranked.push({ disc, area: maskCount(disc) });
  }
  if (!ranked.length) return new Uint8Array(w * h);

  ranked.sort((a, b) => b.area - a.area);
  return maskOr(...ranked.slice(0, maxLenses).map(r => r.disc));
}

function extractGlasses(dark, thick, lens, opt, w, h) {
  if (!maskCount(lens)) return new Uint8Array(w * h);

  // 픽셀 단위 두께 조건만으로는 부족하다. 두꺼운 머리 덩어리도 "경계에서
  // 7px 안쪽" 껍질은 반폭이 작아서 통과 가능으로 잡히고, 플러드가 그 껍질을
  // 타고 머리 표면을 기어서 눈썹·앞머리까지 번진다. 모폴로지 열림으로 두꺼운
  // 덩어리를 껍질까지 통째로 빼야 한다.
  const bulk = opening(dark, opt.hairMinThick, w, h);
  const passable = new Uint8Array(w * h);
  for (let i = 0; i < passable.length; i++) {
    passable[i] = dark[i] && !bulk[i] && thick[i] <= opt.rimMaxThick ? 1 : 0;
  }

  // 씨드는 렌즈 디스크 "바로 바깥" 띠에서만 뽑는다. 디스크 내부를 포함하면
  // 눈 윤곽선이 씨드가 되어 눈이 안경 레이어로 넘어간다.
  const outerBand = maskAndNot(dilate(lens, 2, w, h), lens);
  const seeds = maskAnd(outerBand, passable);
  if (!maskCount(seeds)) return new Uint8Array(w * h);

  let rim = floodFromSeeds(passable, seeds, w, h);

  // 혹시 디스크 안쪽으로 샌 부분은 눈 그림이지 테가 아니다.
  rim = maskAndNot(rim, erode(lens, 2, w, h));

  if (maskCount(rim) > opt.maxRimAreaFrac * w * h) {
    // 플러드가 머리까지 먹었다. 렌즈 주변 링으로만 후퇴한다.
    rim = maskAnd(maskAndNot(dilate(lens, Math.round(opt.rimMaxThick), w, h), lens), dark);
  }
  return rim;
}

// ───────────────────────────────────────────────────────────── 2단계: 눈

function extractEyes(bands, lens, glasses, searchband, eyeBand, opt, w, h) {
  let zone;
  if (maskCount(lens)) {
    zone = dilate(lens, Math.round(opt.rimMaxThick) + 4, w, h);
  } else if (searchband.eyePoints.length) {
    zone = maskAnd(discMask(searchband.eyePoints, searchband.eyeRadius, w, h), bands.opaque);
  } else {
    // 안경도 랜드마크도 없을 때. 눈 밴드 전체에서 찾는다.
    zone = maskAnd(eyeBand, bands.opaque);
  }
  if (!maskCount(zone)) return new Uint8Array(w * h);

  const candidate = maskAndNot(
    maskAnd(maskOr(bands.dark, bands.mid, bands.white), zone),
    glasses
  );

  // 홍채는 렌즈 안쪽의 큰 채도 있는 중간톤 덩어리다.
  const inner = maskCount(lens) ? erode(lens, 2, w, h) : zone;
  let iris = dropSmall(maskAnd(bands.mid, inner), opt.minPartArea, w, h);
  if (!maskCount(iris)) {
    // 홍채가 어둡게 칠해진 그림이면 중간톤이 안 잡힌다. 눈 중심 근처의
    // 어두운 덩어리로 떨어진다.
    const near = searchband.eyePoints.length
      ? discMask(searchband.eyePoints, searchband.eyeRadius, w, h)
      : inner;
    iris = dropSmall(maskAnd(maskAnd(bands.dark, inner), near), opt.minPartArea, w, h);
  }
  if (!maskCount(iris)) return new Uint8Array(w * h);

  // 홍채에 닿은 연결 요소만 눈이다. 홍채에 닿지 않는 얇은 호(쌍꺼풀 선)는
  // 여기서 자동으로 빠져 face 레이어에 남는다.
  const grown = floodFromSeeds(candidate, iris, w, h);

  // 눈 안쪽 구멍을 메운다. 흰 하이라이트와 홍채 사이 전이 링은 V 가 white
  // 하한과 mid 상한 사이에 끼어 어느 밴드에도 안 들어간다. 그대로 두면 눈
  // 내부 픽셀이 face 파트로 떨어지고, 눈 자리를 메울 살색 씨드로 쓰여서
  // 눈 자리가 탁한 회색으로 채워진다. 눈을 둘러싼 영역이 메우므로 안전하다.
  return fillHoles(grown, w, h);
}

// ───────────────────────────────────────────────── 3단계: 헤어 / 눈썹

function extractHair(dark, taken, opt, w, h) {
  const free = maskAndNot(dark, taken);
  if (!maskCount(free)) return new Uint8Array(w * h);

  const thick = halfThickness(free, w, h);
  const core = new Uint8Array(w * h);
  for (let i = 0; i < core.length; i++) core[i] = free[i] && thick[i] >= opt.hairMinThick ? 1 : 0;

  if (!maskCount(core)) {
    const { labels, count, stats } = labelComponents(free, w, h);
    if (!count) return new Uint8Array(w * h);
    let best = 1;
    for (let id = 2; id <= count; id++) if (stats[id].area > stats[best].area) best = id;
    const out = new Uint8Array(w * h);
    for (let i = 0; i < out.length; i++) if (labels[i] === best) out[i] = 1;
    return out;
  }
  // 두꺼운 코어가 속한 요소 전체. 얇은 머리카락 끝까지 따라온다.
  return floodFromSeeds(free, core, w, h);
}

function extractBrows(dark, taken, searchband, opt, w, h) {
  // 눈썹과 헤어는 둘 다 hair 레이어로 간다. 이 분리는 파트 매니페스트와
  // 디버그 출력에만 쓰이므로, 눈썹이 헤어 요소에 흡수돼도 3레이어 결과는 같다.
  const free = maskAndNot(dark, taken);
  if (!maskCount(free)) return new Uint8Array(w * h);

  const band = maskAnd(free, bandMask(searchband.browTop, searchband.browBottom, w, h));
  if (!maskCount(band)) return new Uint8Array(w * h);

  const thick = halfThickness(free, w, h);
  const cand = new Uint8Array(w * h);
  for (let i = 0; i < cand.length; i++) {
    cand[i] = band[i] && thick[i] >= opt.browThickMin && thick[i] <= opt.browThickMax ? 1 : 0;
  }
  return floodFromSeeds(band, dropSmall(cand, opt.minPartArea, w, h), w, h);
}

// ───────────────────────────────────────────────── 분할의 배타성 / 전수성

// 겹칠 때 우선순위: glasses > eyes > brows > hair > face.
// 불투명인데 아무도 안 가져간 픽셀은 모두 face 로 떨어진다.
function ensurePartition(raw, opaque, w, h) {
  const claimed = new Uint8Array(w * h);
  const parts = {};
  for (const name of ['glasses', 'eyes', 'brows', 'hair']) {
    const m = new Uint8Array(w * h);
    const src = raw[name];
    for (let i = 0; i < m.length; i++) {
      if (src[i] && opaque[i] && !claimed[i]) { m[i] = 1; claimed[i] = 1; }
    }
    parts[name] = m;
  }
  const face = new Uint8Array(w * h);
  for (let i = 0; i < face.length; i++) face[i] = opaque[i] && !claimed[i] ? 1 : 0;
  parts.face = face;
  return parts;
}

// ───────────────────────────────────────────────────────── amodal 복원

// known 에서 가장 가까운 색을 target 전체로 퍼뜨린다. 경계 링의 색을 발명하지
// 않는 것이 핵심이다.
function propagateColour(rgba, known, target, w, h) {
  const n = w * h;
  const out = new Uint8ClampedArray(n * 3);
  for (let i = 0, p = 0; i < n; i++, p += 4) {
    out[i * 3] = rgba[p]; out[i * 3 + 1] = rgba[p + 1]; out[i * 3 + 2] = rgba[p + 2];
  }
  // 채울 게 없으면 거리 변환을 아예 건너뛴다. hair 레이어는 target 과 known 이
  // 같아서 매번 1M 픽셀 전파를 공짜로 돌리고 있었다.
  let pending = 0;
  for (let i = 0; i < n; i++) if (target[i] && !known[i]) { pending = 1; break; }
  if (!pending || !maskCount(known)) return out;

  const { nx, ny } = nearestKnown(known, w, h);
  for (let i = 0; i < n; i++) {
    if (known[i] || !target[i]) continue;
    const sx = nx[i];
    if (sx < 0) continue;
    const p = (ny[i] * w + sx) * 4;
    out[i * 3] = rgba[p]; out[i * 3 + 1] = rgba[p + 1]; out[i * 3 + 2] = rgba[p + 2];
  }
  return out;
}

// ───────────────────────────────────────────────────── 소프트 알파 (언믹싱)

// 경계 픽셀 O 가 전경 F 와 배경 B 의 혼합일 때 최소제곱 커버리지는 투영이다.
//     a = <O - B, F - B> / |F - B|²
// F 는 파트 내부에서 가장 가까운 픽셀, B 는 이미 복원된 아래 레이어에서 가져온다.
function softAlpha(rgba, support, underRgb, alpha0, band, w, h) {
  const n = w * h;
  const out = new Float32Array(n);
  for (let i = 0; i < n; i++) out[i] = support[i] ? 1 : 0;
  if (!maskCount(support)) return out;

  const grown = dilate(support, band, w, h);
  const shrunk = erode(support, band, w, h);
  const { nx, ny } = nearestKnown(support, w, h);

  for (let i = 0; i < n; i++) {
    const onEdge = (grown[i] && !support[i]) || (support[i] && !shrunk[i]);
    if (!onEdge) continue;
    const sx = nx[i];
    if (sx < 0) continue;

    const fp = (ny[i] * w + sx) * 4;
    const fr = rgba[fp], fg = rgba[fp + 1], fb = rgba[fp + 2];
    const br = underRgb[i * 3], bg = underRgb[i * 3 + 1], bb = underRgb[i * 3 + 2];
    const dr = fr - br, dg = fg - bg, db = fb - bb;
    const denom = dr * dr + dg * dg + db * db;
    if (denom < 24) continue;   // 전경·배경이 거의 같은 색이면 추정이 무의미하다

    const op = i * 4;
    const est = ((rgba[op] - br) * dr + (rgba[op + 1] - bg) * dg + (rgba[op + 2] - bb) * db) / denom;
    out[i] = Math.min(Math.max(est, 0), 1);
  }

  // 원본보다 더 덮을 수는 없다.
  for (let i = 0; i < n; i++) out[i] = Math.min(out[i], alpha0[i] / 255);
  return out;
}

// ─────────────────────────────────────────────────── 알파 불변식 강제

// 재합성 알파가 원본 알파와 정확히 같아지려면 두 가지만 지키면 된다.
//
//   외곽 링 (0 < alpha0 < 255)  소유자 한 레이어만 alpha0 을 갖고 나머지는 0.
//                               두 레이어가 각각 분수 알파를 들면 합성 알파가
//                               1에 못 미쳐 실루엣 전체에 창백한 후광이 생긴다.
//   내부   (alpha0 == 255)       분수 알파를 가진 레이어 아래에 불투명한 받침이
//                               있어야 한다. 없으면 소프트 엣지가 투명과 섞여
//                               어두운 테두리가 생긴다.
//
// face 레이어가 머리 내부 전체를 알파 255 로 덮으므로 두 번째는 구성적으로
// 성립한다. 이 함수는 그걸 강제하고 위반 횟수를 보고한다.
function enforceAlphaInvariant(alphas, owners, alpha0, w, h) {
  const n = w * h;
  let rimReassigned = 0, backingAdded = 0;

  for (let i = 0; i < n; i++) {
    const a0 = alpha0[i];

    if (a0 === 0) {
      for (const a of alphas) a[i] = 0;
      continue;
    }

    if (a0 < 255) {
      let owner = -1;
      for (let z = alphas.length - 1; z >= 0; z--) if (owners[z][i]) { owner = z; break; }
      if (owner < 0) for (let z = alphas.length - 1; z >= 0; z--) if (alphas[z][i] > 0) { owner = z; break; }
      if (owner < 0) continue;
      for (let z = 0; z < alphas.length; z++) {
        if (z === owner) continue;
        if (alphas[z][i] > 0) { alphas[z][i] = 0; rimReassigned++; }
      }
      alphas[owner][i] = a0 / 255;
      continue;
    }

    // 내부. 위에서부터 처음 보이는 레이어를 찾는다.
    let topZ = -1;
    for (let z = alphas.length - 1; z >= 0; z--) if (alphas[z][i] > 0) { topZ = z; break; }
    if (topZ < 0) continue;
    if (alphas[topZ][i] >= 1) continue;

    let backed = false;
    for (let z = topZ - 1; z >= 0; z--) if (alphas[z][i] >= 1) { backed = true; break; }
    if (backed) continue;

    // 받침이 없다. 바로 아래에 색이 있으면 불투명하게 올리고, 없으면 자기를
    // 불투명하게 만든다. 둘 다 재합성 정확성은 exactify 가 보장한다.
    let fixed = false;
    for (let z = topZ - 1; z >= 0; z--) {
      if (alphas[z][i] > 0) { alphas[z][i] = 1; fixed = true; break; }
    }
    if (!fixed) alphas[topZ][i] = 1;
    backingAdded++;
  }
  return { rimReassigned, backingAdded };
}

// ──────────────────────────────────────────────────────────── 메인 진입점

/**
 * @param {{data:Uint8Array|Uint8ClampedArray|Buffer, width:number, height:number}} image
 *        straight-alpha RGBA 원본.
 * @param {object} [options]
 *        landmarks     analyzeHeadRegions 가 준 1024 캔버스 픽셀 좌표 (선택)
 *        glassesPresent  false 면 안경을 아예 찾지 않는다
 *        direction     'right' 면 렌즈를 하나만 찾는다
 *        tuning        DEFAULTS 덮어쓰기
 * @returns {{layers:Array, parts:object, report:object}}
 *        layers[i] = {name, z, data: Uint8ClampedArray RGBA straight alpha}
 */
export async function splitHeadLayers(image, options = {}) {
  const { data, width: w, height: h } = image;
  if (!data || !Number.isInteger(w) || !Number.isInteger(h) || data.length !== w * h * 4) {
    throw Object.assign(new Error('RGBA 원본 버퍼 크기가 캔버스와 맞지 않아요.'), {
      status: 500, code: 'HEAD_LAYER_BAD_INPUT'
    });
  }
  const opt = { ...DEFAULTS, ...(options.tuning || {}) };
  const rgba = data;
  const n = w * h;

  // options.onStage === null 이면 양보하지 않는다 (동기 테스트용).
  const tick = options.onStage === null ? null : (options.onStage || yieldToEventLoop);
  const stage = tick ? label => tick(label) : () => undefined;

  const alpha0 = new Uint8Array(n);
  for (let i = 0, p = 3; i < n; i++, p += 4) alpha0[i] = rgba[p];

  // ── 1. 분할
  const bands = tonalBands(rgba, w, h, opt);
  const searchband = searchBands(options.landmarks, w, h);
  const eyeBand = bandMask(searchband.eyeTop, searchband.eyeBottom, w, h);

  // glassesPresent 는 analyzeHeadRegions 가 이미 비전 모델로 판정해 넘겨준다.
  // 있다/없다는 불리언 판단이라 비전 모델이 잘하는 영역이고, 기하 측정과 달리
  // 틀려도 좌표가 흔들리지 않는다. 그 신호를 그대로 쓴다.
  const darkThickness = halfThickness(bands.dark, w, h);
  const lens = options.glassesPresent === false
    ? new Uint8Array(n)
    : findLensInteriors(bands.dark, darkThickness, searchband,
        options.direction === 'right' ? 1 : 2, opt, w, h);

  const glasses = options.glassesPresent === false
    ? new Uint8Array(n)
    : extractGlasses(bands.dark, darkThickness, lens, opt, w, h);

  await stage('glasses');

  const eyes = extractEyes(bands, lens, glasses, searchband, eyeBand, opt, w, h);
  const hair = extractHair(bands.dark, maskOr(glasses, eyes), opt, w, h);
  const brows = extractBrows(bands.dark, maskOr(glasses, eyes, hair), searchband, opt, w, h);

  const parts = ensurePartition({ glasses, eyes, brows, hair }, bands.opaque, w, h);

  const partitioned = maskCount(parts.face) + maskCount(parts.eyes) + maskCount(parts.hair)
    + maskCount(parts.brows) + maskCount(parts.glasses);
  if (partitioned !== maskCount(bands.opaque)) {
    throw Object.assign(new Error('레이어 분할에서 픽셀이 누락되거나 중복됐어요.'), {
      status: 500, code: 'HEAD_LAYER_PARTITION_BROKEN'
    });
  }

  await stage('segment');

  // ── 2. 레이어별 소유 영역과 amodal 받침
  const owners = LAYER_NAMES.map(name => maskOr(...LAYER_PARTS[name].map(p => parts[p])));

  // 완성 얼굴을 다시 만들 때 픽셀 위치가 변하면 안 된다. AI/분할 마스크는
  // 어느 레이어가 해당 픽셀을 소유하는지 판단하는 용도로만 사용하고, 실제
  // 레이어의 RGBA 값은 완성 얼굴(master)에서 그대로 복사한다.
  //
  // 기존 방식은 amodal 보정과 soft-alpha 색상 재구성을 거치면서 가장자리의
  // RGB/alpha를 다시 양자화했다. 그 결과 마스크 자체는 맞아도 3장을 합친
  // 결과가 master와 달라져 재합성 오차 검사를 통과하지 못했다.
  if (options.exactSharedCanvas !== false) {
    const exactOutputs = LAYER_NAMES.map(() => new Uint8ClampedArray(rgba.length));
    for (let i = 0; i < n; i++) {
      const p = i * 4;
      let assigned = false;
      for (let z = 0; z < owners.length; z++) {
        if (!owners[z][i]) continue;
        exactOutputs[z][p] = rgba[p];
        exactOutputs[z][p + 1] = rgba[p + 1];
        exactOutputs[z][p + 2] = rgba[p + 2];
        exactOutputs[z][p + 3] = rgba[p + 3];
        assigned = true;
        break;
      }
      // opaque 임계값(24) 아래의 반투명 안티앨리어싱 픽셀도 버리면 안 된다.
      // 이 픽셀을 누락하면 흰 배경 기준 재합성에서 최대 25.6/255 오차가 난다.
      if (!assigned && rgba[p + 3] > 0) {
        exactOutputs[0][p] = rgba[p];
        exactOutputs[0][p + 1] = rgba[p + 1];
        exactOutputs[0][p + 2] = rgba[p + 2];
        exactOutputs[0][p + 3] = rgba[p + 3];
      }
    }

    const exactReport = verifyRecomposition(exactOutputs, rgba, w, h);
    exactReport.parts = Object.fromEntries(PART_NAMES.map(name => [name, maskCount(parts[name])]));
    exactReport.partitionedPixels = partitioned;
    exactReport.opaquePixels = maskCount(bands.opaque);
    exactReport.invariant = {
      method: 'exclusive-master-owner',
      overlapPixels: 0,
      unassignedPixels: 0
    };
    exactReport.bands = {
      eyeTop: searchband.eyeTop, eyeBottom: searchband.eyeBottom,
      browTop: searchband.browTop, browBottom: searchband.browBottom,
      landmarkDriven: searchband.eyePoints.length > 0
    };

    return {
      layers: LAYER_NAMES.map((name, z) => ({ name, z: z + 1, width: w, height: h, data: exactOutputs[z] })),
      parts,
      report: exactReport
    };
  }

  // face 레이어는 머리 내부 전체를 살색으로 덮는다. 타원으로 두개골을
  // 추정하는 것보다 단순하고, 받침이 모자랄 일이 없다. 넘치게 채운 부분은
  // 100% 헤어에 가려지고, 헤어를 교체해도 구멍이 드러나지 않는다.
  // interior 는 원본이 완전 불투명한 영역만 포함하므로 외곽 링을 침범하지 않는다.
  const interior = fillHoles(bands.core, w, h);
  const faceShape = maskOr(parts.face, interior);
  const eyeShape = maskOr(parts.eyes, maskAnd(parts.glasses, dilate(parts.eyes, opt.rimBridge, w, h)));
  const hairShape = owners[2];

  const supports = [faceShape, eyeShape, hairShape];

  // 라인아트에서 색을 퍼뜨리면 검은 선이 이마로 번진다. 살색 씨드만 쓴다.
  // dark 를 빼는 것만으로는 부족하다. 검은 선 바로 옆 1~2px 은 안티에일리어싱
  // 으로 선과 살색이 섞인 중간색인데 V 가 dark 임계값을 넘어 살아남는다. 그걸
  // 씨드로 쓰면 눈 자리가 탁한 회색으로 메워진다. 선 주변 띠까지 제외한다.
  const skinSeed = maskAndNot(parts.face, dilate(bands.dark, 2, w, h));
  const faceRgb = propagateColour(rgba, maskCount(skinSeed) ? skinSeed : parts.face, faceShape, w, h);
  for (let i = 0; i < n; i++) {
    if (!parts.face[i]) continue;              // 실제 얼굴 픽셀은 원본 그대로
    const p = i * 4;
    faceRgb[i * 3] = rgba[p]; faceRgb[i * 3 + 1] = rgba[p + 1]; faceRgb[i * 3 + 2] = rgba[p + 2];
  }
  const eyeRgb = propagateColour(rgba, parts.eyes, eyeShape, w, h);
  const hairRgb = propagateColour(rgba, hairShape, hairShape, w, h);
  const straightArt = [faceRgb, eyeRgb, hairRgb];

  await stage('amodal');

  // ── 3. 소프트 알파. 아래/배경을 향한 경계만.
  const alphas = supports.map(s => {
    const a = new Float32Array(n);
    for (let i = 0; i < n; i++) a[i] = s[i] ? 1 : 0;
    return a;
  });

  const underRgb = new Uint8ClampedArray(n * 3);
  const underA = new Float32Array(n);
  for (let z = 0; z < LAYER_NAMES.length; z++) {
    if (z > 0) {
      // 위 레이어를 향한 경계는 amodal 받침이라 불투명해야 한다.
      let blocked = new Uint8Array(n);
      for (let up = z + 1; up < LAYER_NAMES.length; up++) blocked = maskOr(blocked, owners[up]);
      blocked = dilate(blocked, 1, w, h);

      const soft = softAlpha(rgba, supports[z], underRgb, alpha0, opt.softBand, w, h);
      const { nx, ny } = nearestKnown(supports[z], w, h);
      for (let i = 0; i < n; i++) {
        if (blocked[i]) continue;
        if (soft[i] > 0 && !supports[z][i]) {
          // 소프트 알파가 기존 받침 밖으로 1~2px 밀려났다. 자기 색을 바깥으로
          // 연장해야 한다. 안 하면 그 픽셀이 검게 합성된다.
          const sx = nx[i];
          if (sx < 0) { continue; }
          const src = (ny[i] * w + sx) * 3;
          straightArt[z][i * 3] = straightArt[z][src];
          straightArt[z][i * 3 + 1] = straightArt[z][src + 1];
          straightArt[z][i * 3 + 2] = straightArt[z][src + 2];
        }
        alphas[z][i] = soft[i];
      }
    }
    // 다음 레이어의 배경색으로 쓸 아래 합성 결과를 갱신한다.
    for (let i = 0; i < n; i++) {
      const a = alphas[z][i];
      if (a <= 0) continue;
      for (let c = 0; c < 3; c++) {
        underRgb[i * 3 + c] = a * straightArt[z][i * 3 + c] + (1 - a) * underRgb[i * 3 + c];
      }
      underA[i] = a + (1 - a) * underA[i];
    }
  }

  await stage('soft-alpha');

  // ── 4. 알파 불변식
  const invariant = enforceAlphaInvariant(alphas, owners, alpha0, w, h);

  await stage('alpha-invariant');

  // ── 5. 아래에서 위로, 한 레이어씩 8비트로 확정하면서 잔차를 치환한다.
  //        아래 레이어를 먼저 양자화하고 그 값으로 잔차를 계산하므로 레이어를
  //        거치며 오차가 누적되지 않는다.
  const alphaInt = alphas.map(a => {
    const q = new Uint8Array(n);
    for (let i = 0; i < n; i++) q[i] = Math.round(Math.min(Math.max(a[i], 0), 1) * 255);
    return q;
  });

  const outputs = LAYER_NAMES.map(() => new Uint8ClampedArray(n * 4));
  const uPm = new Float64Array(n * 3);   // 확정된 아래 레이어들의 프리멀티플라이드 합성

  for (let z = 0; z < LAYER_NAMES.length; z++) {
    const out = outputs[z], aInt = alphaInt[z], art = straightArt[z];

    // 위 레이어가 이 픽셀을 완전히 가리면 잔차 치환은 의미가 없다.
    for (let i = 0; i < n; i++) {
      const ai = aInt[i];
      const p = i * 4;
      if (ai === 0) { out[p] = out[p + 1] = out[p + 2] = out[p + 3] = 0; continue; }

      let hidden = false;
      for (let up = z + 1; up < LAYER_NAMES.length; up++) if (alphaInt[up][i] === 255) { hidden = true; break; }
      const soft = ai > 0 && ai < 255;
      const rewrite = !hidden && (owners[z][i] === 1 || soft);

      const a = ai / 255;
      for (let c = 0; c < 3; c++) {
        let straight;
        if (rewrite) {
          // P = O_pm - (1 - A) * U_pm,  그다음 straight = P / A
          const oPm = (rgba[p + c] / 255) * (alpha0[i] / 255);
          const pm = Math.min(Math.max(oPm - (1 - a) * uPm[i * 3 + c], 0), a);
          straight = pm / a;
        } else {
          // 가려진 영역은 복원해 둔 그림을 그대로 둔다.
          straight = art[i * 3 + c] / 255;
        }
        out[p + c] = Math.round(straight * 255);
      }
      out[p + 3] = ai;
    }

    await stage('quantize:' + LAYER_NAMES[z]);

    // 확정된 8비트 값으로 아래 합성 결과를 갱신한다.
    for (let i = 0; i < n; i++) {
      const a = aInt[i] / 255;
      if (a <= 0) continue;
      const p = i * 4;
      for (let c = 0; c < 3; c++) {
        uPm[i * 3 + c] = (out[p + c] / 255) * a + (1 - a) * uPm[i * 3 + c];
      }
    }
  }

  await stage('exactify');

  // ── 6. 검수. 쓰기 직전의 실제 8비트 값으로 재합성해 원본과 비교한다.
  const report = verifyRecomposition(outputs, rgba, w, h);
  report.parts = Object.fromEntries(PART_NAMES.map(name => [name, maskCount(parts[name])]));
  report.partitionedPixels = partitioned;
  report.opaquePixels = maskCount(bands.opaque);
  report.lensInteriorPixels = maskCount(lens);
  report.invariant = invariant;
  report.bands = {
    eyeTop: searchband.eyeTop, eyeBottom: searchband.eyeBottom,
    browTop: searchband.browTop, browBottom: searchband.browBottom,
    landmarkDriven: searchband.eyePoints.length > 0
  };

  const result = {
    layers: LAYER_NAMES.map((name, z) => ({ name, z: z + 1, width: w, height: h, data: outputs[z] })),
    parts,
    report
  };

  // 실제 일러스트에서 임계값을 맞출 때 쓸 중간 마스크. 숫자만 보고는 어디서
  // 틀어졌는지 알 수 없다.
  if (options.debug) {
    result.debug = {
      dark: bands.dark, mid: bands.mid, white: bands.white,
      opaque: bands.opaque, core: bands.core,
      lens, interior, faceShape, eyeShape, hairShape,
      skinSeed, thickness: darkThickness,
      bulk: opening(bands.dark, opt.hairMinThick, w, h),
      alpha: alphaInt
    };
  }
  return result;
}

// ────────────────────────────────────────────────────────────────── 검수

// 메모리상 실수 계산은 구성적으로 정확하다. 실제로 틀어질 수 있는 건 8비트
// straight-alpha 왕복뿐이다. PNG 는 프리멀티플라이드를 저장하지 않으므로
// 저장할 때 알파로 나눠야 한다. 합성 기여도가 alpha*rgb 라서 알파가 작으면
// 양자화 오차의 기여도도 같이 줄어든다. 그래서 소프트 엣지에서도 1 코드값
// 근처에 머문다.
export function verifyRecomposition(layers, rgba, w, h) {
  const n = w * h;
  let maxOnWhite = 0, maxAlpha = 0, sumSq = 0;
  let over1 = 0, over2 = 0, over8 = 0;
  let worstAt = null;

  for (let i = 0; i < n; i++) {
    const p = i * 4;
    let pm = [0, 0, 0], acc = 0;
    for (const layer of layers) {
      const a = layer[p + 3] / 255;
      if (a <= 0) continue;
      for (let c = 0; c < 3; c++) pm[c] = (layer[p + c] / 255) * a + (1 - a) * pm[c];
      acc = a + (1 - a) * acc;
    }
    const a0 = rgba[p + 3] / 255;
    const alphaErr = Math.abs(acc - a0) * 255;
    if (alphaErr > maxAlpha) maxAlpha = alphaErr;

    let worst = 0;
    for (let c = 0; c < 3; c++) {
      // 흰 배경에 올린 모습으로 비교한다. 사용자가 실제로 보는 상태다.
      const got = pm[c] + (1 - acc) * 1;
      const want = (rgba[p + c] / 255) * a0 + (1 - a0) * 1;
      const d = Math.abs(got - want) * 255;
      if (d > worst) worst = d;
      sumSq += d * d;
    }
    if (worst > 1) over1++;
    if (worst > 2) over2++;
    if (worst > 8) over8++;
    if (worst > maxOnWhite) { maxOnWhite = worst; worstAt = { x: i % w, y: Math.floor(i / w) }; }
  }

  const mse = sumSq / (n * 3);
  return {
    maxAbsOnWhite: Number(maxOnWhite.toFixed(3)),
    maxAbsAlpha: Number(maxAlpha.toFixed(3)),
    psnrOnWhite: mse <= 1e-12 ? Infinity : Number((10 * Math.log10(sq(255) / mse)).toFixed(2)),
    pixelsOver1: over1,
    pixelsOver2: over2,
    pixelsOver8: over8,
    totalPixels: n,
    worstPixel: worstAt
  };
}
