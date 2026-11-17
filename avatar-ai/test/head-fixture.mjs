// 결정론적 레이어 분리를 검증할 합성 셀 셰이딩 얼굴. sharp 도 네트워크도 쓰지
// 않으므로 어디서나 돌아간다.
//
// 4x4 서브샘플로 렌더해서 실제 안티에일리어싱을 만든다. 하드 엣지로만 된
// 그림은 소프트 알파 경로를 전혀 검사하지 못하고, 그 경로가 바로 기존
// 파이프라인이 실금과 후광을 내던 자리다.
//
// 치수는 참조 일러스트 비율에 맞췄다. 두 가지가 중요하다.
//   - 안경 렌즈가 서로 겹치지 않아야 한다. 겹치면 포위 영역이 하나로 합쳐진다.
//   - 렌즈 안에 눈과 테 사이 순수 살색 띠가 40px 이상 남아야 한다. 이 띠가
//     눈 자리를 메울 깨끗한 살색 씨드다.

export const W = 1024;
export const H = 1024;

export const SKIN   = [250, 220, 205];
export const BLUSH  = [245, 180, 180];
export const LINE   = [ 90,  60,  55];   // 코·입. dark 밴드에 들어가지만 얇다
export const CREASE = [ 70,  50,  45];   // 쌍꺼풀 선. face 레이어에 남아야 한다
export const HAIR   = [ 45,  35,  30];
export const BROW   = [ 50,  40,  35];
export const FRAME  = [ 20,  20,  22];
export const LASH   = [ 25,  20,  20];
export const IRIS   = [120,  80,  55];   // mid 밴드 (V=120, S=138)
export const SPARK  = [255, 255, 255];

export const EYE = [{x: 380, y: 600}, {x: 644, y: 600}];
export const RIM = {rx: 112, ry: 104, t: 6};   // 반폭 3 -> rimMaxThick=7 게이트 통과
export const IRIS_R = {rx: 62, ry: 66};
export const CREASE_R = {rx: 76, ry: 80, dy: -6, t: 5};

export const LANDMARKS = {
  leftEye: EYE[0], rightEye: EYE[1],
  leftEyebrow: {x: EYE[0].x, y: 476}, rightEyebrow: {x: EYE[1].x, y: 476},
  nose: {x: 512, y: 690}, mouth: {x: 512, y: 745},
  chin: {x: 512, y: 930}, hairCrown: {x: 512, y: 115}, neckBottom: {x: 512, y: 980}
};

const ellipse = (x, y, cx, cy, rx, ry) => ((x - cx) ** 2) / (rx * rx) + ((y - cy) ** 2) / (ry * ry) <= 1;
const ring = (x, y, cx, cy, rx, ry, t) =>
  ellipse(x, y, cx, cy, rx, ry) && !ellipse(x, y, cx, cy, rx - t, ry - t);
const rect = (x, y, x0, y0, x1, y1) => x >= x0 && x <= x1 && y >= y0 && y <= y1;

// 위에서 아래로 훑어 처음 맞는 모양의 색을 쓴다 (z 역순).
export function sample(x, y) {
  // 안경테: 두 렌즈 + 브리지 + 다리가 하나로 이어진 얇은 닫힌 구조.
  // 테가 닫혀 있어야 렌즈 안쪽이 "포위된 영역"으로 검출된다.
  for (const e of EYE) if (ring(x, y, e.x, e.y, RIM.rx, RIM.ry, RIM.t)) return FRAME;
  if (rect(x, y, 492, 594, 532, 606)) return FRAME;                      // 브리지
  if (rect(x, y, 180, 594, 268, 606) || rect(x, y, 756, 594, 844, 606)) return FRAME;  // 다리

  // 눈썹: 반폭 10px 로 browThickRange 안에 들고, 테·헤어와 떨어져 별 요소로 남는다
  for (const e of EYE) if (ellipse(x, y, e.x, e.y - 124, 74, 10)) return BROW;

  // 헤어: 이마를 실제로 덮는 두꺼운 캡. 반폭이 hairMinThick=11 을 크게 넘는다
  if (ellipse(x, y, 512, 440, 350, 330) && !ellipse(x, y, 512, 660, 290, 250)) return HAIR;

  for (const e of EYE) {
    if (ellipse(x, y, e.x - 18, e.y - 22, 16, 16)) return SPARK;         // 하이라이트
    if (ring(x, y, e.x, e.y, IRIS_R.rx, IRIS_R.ry, 7)) return LASH;      // 눈 윤곽·속눈썹
    if (ellipse(x, y, e.x, e.y, IRIS_R.rx, IRIS_R.ry)) return IRIS;      // 홍채
    // 쌍꺼풀 선: 눈 윤곽과 15px, 테와 12px 떨어진 얇은 호. 어느 쪽에도 닿지
    // 않으므로 별 연결 요소가 되어 face 레이어에 남는다.
    if (ring(x, y, e.x, e.y + CREASE_R.dy, CREASE_R.rx, CREASE_R.ry, CREASE_R.t)
        && y < e.y - 46) return CREASE;
  }

  if (ellipse(x, y, 512, 690, 16, 22)) return LINE;                      // 코
  if (ring(x, y, 512, 700, 54, 68, 5) && y > 735) return LINE;           // 입
  for (const e of EYE) if (ellipse(x, y, e.x, e.y + 124, 56, 24)) return BLUSH;

  if (ellipse(x, y, 512, 600, 330, 340)) return SKIN;                    // 얼굴
  if (ellipse(x, y, 196, 630, 46, 62) || ellipse(x, y, 828, 630, 46, 62)) return SKIN;  // 귀
  if (rect(x, y, 470, 920, 554, 985)) return SKIN;                       // 목
  return null;
}

export function renderFace() {
  const data = new Uint8ClampedArray(W * H * 4);
  const S = 4, inv = 1 / (S * S);
  for (let y = 0; y < H; y++) {
    for (let x = 0; x < W; x++) {
      let r = 0, g = 0, b = 0, hit = 0;
      for (let sy = 0; sy < S; sy++) {
        for (let sx = 0; sx < S; sx++) {
          const c = sample(x + (sx + 0.5) / S, y + (sy + 0.5) / S);
          if (!c) continue;
          r += c[0]; g += c[1]; b += c[2]; hit++;
        }
      }
      if (!hit) continue;
      const p = (y * W + x) * 4;
      // straight alpha: 색은 덮인 서브샘플의 평균, 알파는 커버리지
      data[p] = Math.round(r / hit);
      data[p + 1] = Math.round(g / hit);
      data[p + 2] = Math.round(b / hit);
      data[p + 3] = Math.round(hit * inv * 255);
    }
  }
  return {data, width: W, height: H};
}

// 렌더가 2초쯤 걸리므로 테스트 전체에서 한 번만 만든다.
let cached = null;
export const face = () => (cached ||= renderFace());

export const alphaAt = (layer, x, y) => layer.data[((y * W + x) * 4) + 3];
export const rgbAt = (buf, x, y) => [0, 1, 2].map(c => buf[((y * W + x) * 4) + c]);
