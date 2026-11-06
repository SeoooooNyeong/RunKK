// Adapted from user-provided hair-fringe-studio-source.zip, dist/engine.mjs.
// Browser-local, deterministic boundary color decontamination. No image coordinates.
const INF = 1e12;
const SQRT2 = Math.SQRT2;
const linear = Float32Array.from({ length: 256 }, (_, i) => {
  const v = i / 255;
  return v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4;
});

function lab(r, g, b, out, i) {
  const R = linear[r], G = linear[g], B = linear[b];
  const f = v => v > 0.008856 ? Math.cbrt(v) : 7.787 * v + 16 / 116;
  const x = f((0.4124564 * R + 0.3575761 * G + 0.1804375 * B) / 0.95047);
  const y = f(0.2126729 * R + 0.7151522 * G + 0.072175 * B);
  const z = f((0.0193339 * R + 0.119192 * G + 0.9503041 * B) / 1.08883);
  // OpenCV-compatible Lab scale, with less weight on luminance variation.
  out[i] = (116 * y - 16) * 2.55 * 0.4;
  out[i + 1] = 500 * (x - y);
  out[i + 2] = 200 * (y - z);
}

function field(seed, w, h, labels = false) {
  const n = w * h, d = new Float32Array(n);
  const nearest = labels ? new Int32Array(n).fill(-1) : null;
  for (let i = 0; i < n; i++) {
    d[i] = seed[i] ? 0 : INF;
    if (nearest && seed[i]) nearest[i] = i;
  }
  function update(i, j, cost) {
    const v = d[j] + cost;
    if (v < d[i]) { d[i] = v; if (nearest) nearest[i] = nearest[j]; }
  }
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const i = y * w + x;
    if (x) update(i, i - 1, 1);
    if (y) { update(i, i - w, 1); if (x) update(i, i - w - 1, SQRT2); if (x + 1 < w) update(i, i - w + 1, SQRT2); }
  }
  for (let y = h - 1; y >= 0; y--) for (let x = w - 1; x >= 0; x--) {
    const i = y * w + x;
    if (x + 1 < w) update(i, i + 1, 1);
    if (y + 1 < h) { update(i, i + w, 1); if (x) update(i, i + w - 1, SQRT2); if (x + 1 < w) update(i, i + w + 1, SQRT2); }
  }
  return { d, nearest };
}

function palette(features, core) {
  const sample = [];
  const step = Math.max(1, Math.floor(core.length / 16000));
  for (let i = 0; i < core.length; i += step) sample.push(core[i]);
  const k = Math.min(12, sample.length);
  const centers = [];
  // Deterministic farthest-point initialization.
  centers.push(Array.from(features.subarray(sample[Math.floor(sample.length / 2)] * 3, sample[Math.floor(sample.length / 2)] * 3 + 3)));
  const distance = new Float32Array(sample.length).fill(INF);
  for (let c = 1; c < k; c++) {
    let best = -1, selected = 0;
    const last = centers[c - 1];
    for (let j = 0; j < sample.length; j++) {
      const p = sample[j] * 3;
      const ds = (features[p] - last[0]) ** 2 + (features[p + 1] - last[1]) ** 2 + (features[p + 2] - last[2]) ** 2;
      distance[j] = Math.min(distance[j], ds);
      if (distance[j] > best) { best = distance[j]; selected = j; }
    }
    const p = sample[selected] * 3;
    centers.push(Array.from(features.subarray(p, p + 3)));
  }
  let counts;
  for (let iteration = 0; iteration < 14; iteration++) {
    counts = new Uint32Array(k);
    const sums = Array.from({ length: k }, () => [0, 0, 0]);
    for (const index of sample) {
      const p = index * 3;
      let best = INF, selected = 0;
      for (let c = 0; c < k; c++) {
        const ds = (features[p] - centers[c][0]) ** 2 + (features[p + 1] - centers[c][1]) ** 2 + (features[p + 2] - centers[c][2]) ** 2;
        if (ds < best) { best = ds; selected = c; }
      }
      counts[selected]++;
      for (let c = 0; c < 3; c++) sums[selected][c] += features[p + c];
    }
    for (let c = 0; c < k; c++) if (counts[c]) centers[c] = sums[c].map(v => v / counts[c]);
  }
  return centers.filter((_, i) => counts[i] >= Math.max(2, sample.length * 0.012));
}

export function defringe(input, w, h, strength = 70, report = () => {}) {
  if (!(input instanceof Uint8ClampedArray) || input.length !== w * h * 4 || !Number.isInteger(w) || !Number.isInteger(h) || w < 1 || h < 1) throw new Error('이미지 데이터를 읽을 수 없습니다.');
  if (w * h > 12582912 || w > 8192 || h > 8192) throw new Error('더 작은 해상도의 이미지를 선택해 주세요.');
  if (!Number.isFinite(strength) || strength < 0 || strength > 100) throw new Error('보정 강도를 확인해 주세요.');
  const result = new Uint8ClampedArray(input), n = w * h;
  let peak = 0, visible = 0, transparent = 0;
  for (let i = 0; i < n; i++) { const a = input[i * 4 + 3]; peak = Math.max(peak, a); if (a > 0) visible++; if (a < 8) transparent++; }
  const empty = reason => ({ pixels: result, changed: 0, skinColor: null, reason });
  if (!visible || !strength) return empty('변경 없음');
  if (!transparent) throw new Error('투명 배경의 헤어 이미지를 선택해 주세요.');
  report(8, '경계 분석');
  const scale = Math.max(0.3, Math.min(w, h) / 1024);
  const background = new Uint8Array(n);
  for (let i = 0; i < n; i++) background[i] = input[i * 4 + 3] <= Math.max(4, peak * 0.025) ? 1 : 0;
  const depth = field(background, w, h).d;
  const features = new Float32Array(n * 3), core = [];
  const coreDepth = Math.max(4, 9 * scale);
  for (let i = 0; i < n; i++) {
    const p = i * 4; if (!input[p + 3]) continue;
    lab(input[p], input[p + 1], input[p + 2], features, i * 3);
    if (input[p + 3] >= peak * 0.8 && depth[i] >= coreDepth) core.push(i);
  }
  if (core.length < 16) return empty('판별 보류');
  report(24, '헤어 색상 분석');
  const colors = palette(features, core);
  if (!colors.length) return empty('판별 보류');
  const anomaly = new Float32Array(n), seed = new Uint8Array(n);
  const threshold = 17.5 - strength * 0.09, maxBand = Math.max(4, 65 * scale);
  const candidates = [];
  for (let i = 0; i < n; i++) {
    const p = i * 4; if (input[p + 3] <= 4) continue;
    let best = INF;
    for (const c of colors) {
      const q = i * 3;
      const ds = (features[q] - c[0]) ** 2 + (features[q + 1] - c[1]) ** 2 + (features[q + 2] - c[2]) ** 2;
      best = Math.min(best, ds);
    }
    anomaly[i] = Math.sqrt(best);
    const r = input[p], g = input[p + 1], b = input[p + 2];
    if (depth[i] <= maxBand && r > 85 && r - g > 12 && g - b > 4 && r - b > 22 && anomaly[i] > threshold) { seed[i] = 1; candidates.push(i); }
  }
  if (candidates.length < Math.max(4, n * 0.000008)) return empty('검출 없음');
  // Reject rare interior colors unless their region actually reaches the alpha boundary.
  const connected = new Uint8Array(n), queue = new Int32Array(candidates.length);
  for (const start of candidates) {
    if (connected[start]) continue;
    let head = 0, tail = 1, touches = false; queue[0] = start; connected[start] = 1;
    while (head < tail) {
      const i = queue[head++], x = i % w, y = Math.floor(i / w);
      if (depth[i] <= Math.max(2, maxBand * 0.5) || input[i * 4 + 3] < peak * 0.7) touches = true;
      for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
        if ((!dx && !dy) || x + dx < 0 || x + dx >= w || y + dy < 0 || y + dy >= h) continue;
        const j = i + dy * w + dx; if (seed[j] && !connected[j]) { connected[j] = 1; queue[tail++] = j; }
      }
    }
    if (!touches) for (let j = 0; j < tail; j++) seed[queue[j]] = 0;
  }
  const retained = candidates.filter(i => seed[i]);
  if (!retained.length) return empty('검출 없음');
  // Estimate the contaminating skin color from the brightest opaque candidates.
  const opaque = retained.filter(i => input[i * 4 + 3] >= peak * 0.8);
  const skinSamples = (opaque.length >= 8 ? opaque : retained).slice().sort((i, j) => {
    const pi = i * 4, pj = j * 4;
    return (input[pj] + input[pj + 1] + input[pj + 2]) - (input[pi] + input[pi + 1] + input[pi + 2]);
  });
  const selected = skinSamples.slice(0, Math.max(1, Math.ceil(skinSamples.length * 0.12)));
  const B = [0, 0, 0];
  for (const i of selected) for (let c = 0; c < 3; c++) B[c] += input[i * 4 + c] / selected.length;
  report(52, '경계 색상 분해');
  const vicinity = field(seed, w, h).d;
  const radius = Math.max(1.5, 3 * scale), reliable = new Uint8Array(n);
  for (let i = 0; i < n; i++) reliable[i] = input[i * 4 + 3] >= peak * 0.8 && depth[i] >= Math.max(1.5, 3 * scale) && vicinity[i] > radius && anomaly[i] < threshold * 0.8 ? 1 : 0;
  if (!reliable.some(Boolean)) return empty('판별 보류');
  const nearest = field(reliable, w, h, true);
  let changed = 0;
  for (let i = 0; i < n; i++) {
    const p = i * 4, r = input[p], g = input[p + 1], b = input[p + 2];
    if (vicinity[i] > radius || depth[i] > maxBand + radius || input[p + 3] <= 4 || r < 45 || r - g < 8 || g - b < 2) continue;
    const j = nearest.nearest[i]; if (j < 0) continue;
    const q = j * 4;
    let numerator = 0, denominator = 0;
    for (let c = 0; c < 3; c++) { const v = input[q + c] - B[c]; numerator += (input[p + c] - B[c]) * v; denominator += v * v; }
    if (denominator < 1600 || r - input[q] < 8) continue;
    let t = Math.max(0, Math.min(1, numerator / denominator));
    if (t >= 0.985) continue;
    // Isolated, thin skin-colored traces have no neighboring hair to restore.
    if (depth[i] <= Math.max(1.5, 2 * scale) && nearest.d[i] > Math.max(4, 7 * scale) && anomaly[i] > threshold && r - g > 18) t = 0;
    const alpha = Math.round(input[p + 3] * t);
    for (let c = 0; c < 3; c++) result[p + c] = alpha ? input[q + c] : 0;
    result[p + 3] = alpha; changed++;
  }
  report(100, '보정 완료');
  return { pixels: result, changed, skinColor: B.map(Math.round), reason: changed ? '보정 완료' : '변경 없음' };
}

// Shared color primitives for the server ownership pass.
export {lab, field, palette};
