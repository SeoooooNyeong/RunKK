// hair-defringe.mjs
// 2번(Edge Zone 한정 HSV/YCrCb 살색 검출) + 3번(Color Unmixing: C = a*F + (1-a)*B) 결합 구현
const EDGE_LIMIT = 25; // 두 가닥 사이에 낀 살색까지 잡도록 경계에서 최대 25픽셀 탐색
const SKIN_SEARCH_LIMIT = 64;
const HAIR_SEARCH_LIMIT = 28;
const DEFAULT_SKIN = [250, 207, 188]; // 기본 살색 기준값 RGB(250, 207, 188)

function nearestSeeds(length, width, height, seeds, limit) {
  const distance = new Uint8Array(length);
  distance.fill(255);
  const nearest = new Int32Array(length);
  nearest.fill(-1);
  const queue = new Int32Array(length);
  let read = 0, write = 0;

  for (let p = 0; p < length; p++) {
    if (seeds(p)) {
      distance[p] = 0;
      nearest[p] = p;
      queue[write++] = p;
    }
  }
  while (read < write) {
    const p = queue[read++], d = distance[p] + 1;
    if (d > limit) continue;
    const x = p % width, y = (p / width) | 0;
    for (const q of [
      x > 0 ? p - 1 : -1,
      x < width - 1 ? p + 1 : -1,
      y > 0 ? p - width : -1,
      y < height - 1 ? p + width : -1
    ]) {
      if (q >= 0 && distance[q] === 255) {
        distance[q] = d;
        nearest[q] = nearest[p];
        queue[write++] = q;
      }
    }
  }
  return {distance, nearest};
}

// OpenCV 호환 HSV(H: 0~180, S: 0~255, V: 0~255) 및 YCrCb 변환 후 살색 판별
function isSkinColorHsvYCrCb(r, g, b) {
  const max = Math.max(r, g, b), min = Math.min(r, g, b), delta = max - min;
  const v = max;
  const s = max === 0 ? 0 : (delta / max) * 255;
  let h = 0;
  if (delta > 0) {
    if (max === r) h = 60 * (((g - b) / delta) % 6);
    else if (max === g) h = 60 * ((b - r) / delta + 2);
    else h = 60 * ((r - g) / delta + 4);
    if (h < 0) h += 360;
  }
  const hOpenCV = h / 2; // OpenCV H 스케일 (0~180)

  // YCrCb 변환
  const cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b;
  const cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b;

  // HSV 조건: H 3~28, S 15~185, V 135~255 / YCrCb 조건: Cr 133~178, Cb 77~132 / RGB 적색 우위
  const hsvMatch = hOpenCV >= 3 && hOpenCV <= 28 && s >= 15 && s <= 185 && v >= 135;
  const ycrcbMatch = cr >= 132 && cr <= 178 && cb >= 77 && cb <= 135;
  const rgbMatch = r > 135 && r > g + 6 && g >= b - 4 && r > b + 12;

  return (hsvMatch || ycrcbMatch) && rgbMatch;
}

export function defringeHair(hair, face, width, height, faceBounds) {
  const count = width * height;
  if (hair.length !== count * 4 || face.length !== count * 4) {
    throw new Error('헤어 디프린지: 레이어 크기가 달라요.');
  }

  const source = Buffer.from(hair);

  // 1. 원본 알파 채널로 투명 경계선(Edge)까지의 거리 계산
  const empty = nearestSeeds(count, width, height, p => source[p * 4 + 3] <= 8, SKIN_SEARCH_LIMIT).distance;

  // 2. 얼굴 레이어의 깨끗한 피부색 샘플 시드
  const skinSeeds = nearestSeeds(count, width, height, p => {
    const i = p * 4, r = face[i], g = face[i + 1], b = face[i + 2];
    return face[i + 3] > 235 && isSkinColorHsvYCrCb(r, g, b) && (r + g + b) / 3 > 175;
  }, SKIN_SEARCH_LIMIT);

  // 3. 오염되지 않은 순수 머리카락/눈썹 색상(F) 시드 (경계에서 3px 이상 안쪽이거나 충분히 어두운 픽셀)
  const pureHair = nearestSeeds(count, width, height, p => {
    const i = p * 4, r = source[i], g = source[i + 1], b = source[i + 2], a = source[i + 3];
    if (a < 220) return false;
    const luma = 0.299 * r + 0.587 * g + 0.114 * b;
    return (empty[p] >= 3 && luma < 145) || (luma < 105 && !isSkinColorHsvYCrCb(r, g, b));
  }, HAIR_SEARCH_LIMIT);

  // 4. 처리 ROI 제한: 얼굴 쪽 머리카락과 눈썹 주변으로 제한 (아래쪽 머리카락 보존)
  const left = Math.max(0, Math.floor(faceBounds.left - 45));
  const right = Math.min(width - 1, Math.ceil(faceBounds.right + 45));
  const top = Math.max(0, Math.floor(faceBounds.top - 25));
  const bottom = Math.min(height - 1, Math.ceil(faceBounds.bottom + 15));

  let adjusted = 0;

  for (let y = top; y <= bottom; y++) {
    for (let x = left; x <= right; x++) {
      const p = y * width + x, i = p * 4;
      const a = source[i + 3], edgeDist = empty[p];

      // 투명 픽셀이거나 경계선에서 25픽셀보다 깊은 머리카락 내부 하이라이트는 건드리지 않음
      if (a <= 8 || edgeDist > EDGE_LIMIT) continue;

      const r = source[i], g = source[i + 1], b = source[i + 2];
      if (!isSkinColorHsvYCrCb(r, g, b)) continue;

      // 배경 살색 기준값 B 결정 (가까운 얼굴 피부색 또는 기본값 RGB(250, 207, 188))
      const sIdx = skinSeeds.nearest[p] * 4;
      const Br = sIdx >= 0 ? face[sIdx] : DEFAULT_SKIN[0];
      const Bg = sIdx >= 0 ? face[sIdx + 1] : DEFAULT_SKIN[1];
      const Bb = sIdx >= 0 ? face[sIdx + 2] : DEFAULT_SKIN[2];

      // 순수 머리카락 색상 F 샘플링 (주변에 없으면 다크 브라운 기본값)
      const hIdx = pureHair.nearest[p] * 4;
      const Fr = hIdx >= 0 ? source[hIdx] : 58;
      const Fg = hIdx >= 0 ? source[hIdx + 1] : 46;
      const Fb = hIdx >= 0 ? source[hIdx + 2] : 42;

      const distCB = Math.hypot(r - Br, g - Bg, b - Bb);
      const distFB = Math.hypot(Fr - Br, Fg - Bg, Fb - Bb);

      // 살색 비중이 거의 100%인 순수 살색 프린지 픽셀은 즉시 투명화하고 얼굴 레이어로 복원
      if (distCB < 24 || (edgeDist <= 18 && r > 192 && g > 148 && distCB < 42)) {
        hair.fill(0, i, i + 4);
        face[i] = Br; face[i + 1] = Bg; face[i + 2] = Bb; face[i + 3] = 255;
        adjusted++;
        continue;
      }

      if (distFB < 25) continue;

      // 3번 Color Unmixing 공식: C = alpha * F + (1 - alpha) * B
      // 벡터 투영과 거리 비율을 결합해 머리카락 섞임 비율(alpha) 역산
      const dFr = Fr - Br, dFg = Fg - Bg, dFb = Fb - Bb;
      const proj = ((r - Br) * dFr + (g - Bg) * dFg + (b - Bb) * dFb) / (distFB * distFB);
      const normAlpha = distCB / distFB;
      let alpha = Math.max(0, Math.min(1, Math.min(proj, normAlpha)));

      // 경계 안쪽 깊은 곳인데 머리카락 비중이 높은 경우(브라운 하이라이트)는 보존
      if (alpha > 0.85 || (edgeDist > 8 && alpha > 0.52)) continue;

      // 선형 혼합 잔차(Residual) 확인: C가 실제로 F와 B의 혼합색인지 검증
      const mixR = Br + alpha * dFr, mixG = Bg + alpha * dFg, mixB = Bb + alpha * dFb;
      const residual = Math.hypot(r - mixR, g - mixG, b - mixB);
      if (residual > 36 && edgeDist > 6) continue;

      // 살색 비중이 클수록 더 투명하게 조정 (감마 보정으로 잔여 살색 링 완전 제거)
      const cleanAlpha = Math.round(a * Math.pow(alpha, 1.25));

      if (cleanAlpha < 14) {
        hair.fill(0, i, i + 4);
      } else {
        hair[i] = Fr;
        hair[i + 1] = Fg;
        hair[i + 2] = Fb;
        hair[i + 3] = cleanAlpha;
      }

      // 빠져나간 살색 배경 성분은 얼굴 레이어에 채워 재합성 시 빈틈이 없게 보장
      face[i] = Br;
      face[i + 1] = Bg;
      face[i + 2] = Bb;
      face[i + 3] = 255;
      adjusted++;
    }
  }
  return adjusted;
}