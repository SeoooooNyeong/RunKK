// pixel-head-regions.mjs
import {refineEyes, refineProfileEye} from './eye-mask.mjs';

const W = 1024, N = W * W;

function components(mask) {
  const seen = new Uint8Array(N), queue = new Int32Array(N), out = [];
  for (let start = 0; start < N; start++) {
    if (!mask[start] || seen[start]) continue;
    let read = 0, write = 1, left = W, top = W, right = 0, bottom = 0;
    queue[0] = start; seen[start] = 1;
    while (read < write) {
      const p = queue[read++], x = p % W, y = (p / W) | 0;
      left = Math.min(left, x); right = Math.max(right, x);
      top = Math.min(top, y); bottom = Math.max(bottom, y);
      for (const q of [x > 0 ? p - 1 : -1, x < W - 1 ? p + 1 : -1, y > 0 ? p - W : -1, y < W - 1 ? p + W : -1]) {
        if (q >= 0 && mask[q] && !seen[q]) { seen[q] = 1; queue[write++] = q; }
      }
    }
    if (write > 8) out.push({
      pixels: queue.slice(0, write), count: write,
      left, top, right, bottom,
      width: right - left + 1, height: bottom - top + 1,
      cx: (left + right) / 2, cy: (top + bottom) / 2
    });
  }
  return out;
}

function dilate(mask, radius) {
  const out = new Uint8Array(N);
  for (let p = 0; p < N; p++) if (mask[p]) {
    const x = p % W, y = (p / W) | 0;
    for (let dy = -radius; dy <= radius; dy++) {
      const yy = y + dy; if (yy < 0 || yy >= W) continue;
      for (let dx = -radius; dx <= radius; dx++) {
        const xx = x + dx; if (xx >= 0 && xx < W) out[yy * W + xx] = 1;
      }
    }
  }
  return out;
}

function erode(mask, radius) {
  const out = new Uint8Array(N);
  for (let p = 0; p < N; p++) if (mask[p]) {
    const x = p % W, y = (p / W) | 0;
    let keep = 1;
    for (let dy = -radius; dy <= radius && keep; dy++) {
      const yy = y + dy; if (yy < 0 || yy >= W) { keep = 0; break; }
      for (let dx = -radius; dx <= radius; dx++) {
        const xx = x + dx; if (xx < 0 || xx >= W || !mask[yy * W + xx]) { keep = 0; break; }
      }
    }
    if (keep) out[p] = 1;
  }
  return out;
}

function fillHoles(mask) {
  const inverse = new Uint8Array(N);
  for (let p = 0; p < N; p++) inverse[p] = 1 - mask[p];
  const out = Uint8Array.from(mask);
  for (const c of components(inverse)) {
    if (c.left > 0 && c.top > 0 && c.right < W - 1 && c.bottom < W - 1) {
      for (const p of c.pixels) out[p] = 1;
    }
  }
  return out;
}

function recoverForeheadSkin(data,rawSkin,faceMask,filled,hairMask,refined,face,foreheadTopY,eyeTop){
  // foreheadTopY is a landmark-search aid, not a horizontal hair/skin cut line.
  // Recover narrow, disconnected skin windows between bangs using observed face colors.
  const samples=[[],[],[]];
  for(let y=Math.max(face.top,eyeTop);y<face.bottom;y+=3)for(let x=face.left;x<=face.right;x+=3){
    const p=y*W+x,i=p*4;
    if(!faceMask[p]||!rawSkin[p]||refined.eyes[p]||refined.brows[p]||data[i+3]<230)continue;
    for(let k=0;k<3;k++)samples[k].push(data[i+k]);
  }
  if(samples[0].length<20)return {pixels:0,top:face.top};
  const palette=samples.map(a=>a.sort((a,b)=>a-b)[Math.floor(a.length*.6)]);
  const brightness=palette.reduce((a,b)=>a+b,0)/3;
  const searchTop=Math.max(0,Math.floor(foreheadTopY-face.height*.85));
  const searchBottom=Math.min(W-1,Math.ceil(eyeTop+face.height*.06));
  const candidates=new Uint8Array(N);
  for(let y=searchTop;y<=searchBottom;y++)for(let x=face.left;x<=face.right;x++){
    const p=y*W+x,i=p*4;
    if(data[i+3]<100||refined.eyes[p]||refined.brows[p]||refined.rejectedHair?.[p])continue;
    const rgb=[data[i],data[i+1],data[i+2]],v=(rgb[0]+rgb[1]+rgb[2])/3;
    const colorError=Math.hypot(...rgb.map((c,k)=>c-palette[k]));
    const chromaError=Math.hypot((rgb[0]-rgb[1])-(palette[0]-palette[1]),(rgb[1]-rgb[2])-(palette[1]-palette[2]));
    // Shadows between bangs can be darker than the global skin/hair threshold.
    const skinChromaticity=rgb[0]>rgb[1]+4 && rgb[1]>rgb[2]+2;
    if(skinChromaticity&&v>=brightness*.76&&colorError<85&&chromaError<24)candidates[p]=1;
  }
  let count=0,top=face.top;
  const nearFace=dilate(faceMask,6);
  for(const c of components(candidates)){
    const joinsFace=c.pixels.some(p=>nearFace[p]);
    // Skin windows may be disconnected by an eyebrow. Reject isolated crown highlights.
    if(!joinsFace && (c.bottom<foreheadTopY-face.height*.18||c.count<20))continue;
    for(const p of c.pixels){
      if(hairMask[p])count++;
      hairMask[p]=0;faceMask[p]=1;filled[p]=1;
      top=Math.min(top,Math.floor(p/W));
    }
  }
  return {pixels:count,top};
}

export function pixelRegions(data, {profile = false} = {}) {
  const warm = new Uint8Array(N), hist = new Uint32Array(256);
  for (let p = 0; p < N; p++) {
    const i = p * 4, r = data[i], g = data[i + 1], b = data[i + 2], v = Math.round((r + g + b) / 3);
    if (data[i + 3] > 100 && r > g + 3 && g > b - 2 && r > b + 10) {
      warm[p] = 1; hist[v]++;
    }
  }
  let total = 0, sum = 0;
  for (let i = 0; i < 256; i++) { total += hist[i]; sum += i * hist[i]; }
  let below = 0, partial = 0, best = 0, threshold = 120;
  for (let i = 0; i < 255; i++) {
    below += hist[i]; partial += i * hist[i];
    if (!below || below === total) continue;
    const variance = below * (total - below) * (partial / below - (sum - partial) / (total - below)) ** 2;
    if (variance > best) { best = variance; threshold = i; }
  }

  const rawSkin = new Uint8Array(N);
  for (let p = 0; p < N; p++) {
    const i = p * 4;
    if (warm[p] && (data[i] + data[i + 1] + data[i + 2]) / 3 > threshold) rawSkin[p] = 1;
  }

  // 1) 정수리 가르마 줄기 차단: 침식 후 연속된 밝은 이마 스캔라인이 시작되는 지점(foreheadTopY) 검출
  const erodedSkin = erode(rawSkin, 4);
  const coreCandidates = components(erodedSkin).filter(c => c.count > 200 && c.cy < W * 0.95).sort((a, b) => b.count - a.count);
  const coreFace = coreCandidates.find(c => c.cx > W * 0.22 && c.cx < W * (profile ? 0.94 : 0.78) && c.cy < W * 0.9);
  if (!coreFace || coreFace.count < 6000) throw new Error('얼굴 피부 영역을 찾지 못했어요. 기존 아바타를 유지해요.');

  let foreheadTopY = coreFace.top;
  for (let y = Math.max(0, coreFace.top - 10); y < coreFace.cy; y++) {
    let maxRun = 0, cur = 0;
    for (let x = coreFace.left; x <= coreFace.right; x++) {
      const i = (y * W + x) * 4;
      if (rawSkin[y * W + x] && (data[i] + data[i + 1] + data[i + 2]) / 3 > threshold * 1.18) {
        cur++;
        if (cur > maxRun) maxRun = cur;
      } else {
        cur = 0;
      }
    }
    if (maxRun > coreFace.width * 0.18) {
      foreheadTopY = y;
      break;
    }
  }

  const skin = Uint8Array.from(rawSkin);
  for (let y = 0; y < foreheadTopY; y++) {
    for (let x = 0; x < W; x++) skin[y * W + x] = 0;
  }

  const candidates = components(skin).filter(c => c.count > 15 && c.cy < W * 0.95).sort((a, b) => b.count - a.count);
  const face = candidates.find(c => c.cx > W * 0.22 && c.cx < W * (profile ? 0.94 : 0.78) && c.cy < W * 0.9);
  if (!face || face.count < 12000) throw new Error('얼굴 피부 영역을 찾지 못했어요. 기존 아바타를 유지해요.');

  const faceMask = new Uint8Array(N);
  const profileCore=new Uint8Array(N);
  if(profile)for(const p of face.pixels)profileCore[p]=1;
  const profileNeighborhood=profile?dilate(profileCore,10):null;
  for (const c of candidates) {
    // Wind-swept hair has isolated warm highlights far behind a profile face.
    // Those islands must not widen the scanline-filled skin silhouette.
    if(profile&&c!==face&&!c.pixels.some(p=>profileNeighborhood[p]))continue;
    if (c.cx > face.left - face.width * 0.38 && c.cx < face.right + face.width * 0.38 &&
        c.top >= foreheadTopY && c.cy < face.bottom + face.height * 0.4) {
      for (const p of c.pixels) faceMask[p] = 1;
    }
  }

  const filled = fillHoles(erode(dilate(faceMask, 4), 3));
  for (let y = face.top; y <= face.bottom; y++) {
    let left = W, right = -1;
    for (let x = face.left; x <= face.right; x++) if (faceMask[y * W + x]) { left = Math.min(left, x); right = Math.max(right, x); }
    for (let x = left; x <= right; x++) filled[y * W + x] = 1;
  }
  for (let y = 0; y < foreheadTopY; y++) {
    for (let x = 0; x < W; x++) filled[y * W + x] = 0;
  }

  const dark = new Uint8Array(N), rows = [];
  for (let y = face.top; y <= face.bottom; y++) {
    let left = W, right = -1;
    for (let x = face.left; x <= face.right; x++) if (filled[y * W + x]) { left = Math.min(left, x); right = Math.max(right, x); }
    rows[y] = {left, right};
  }
  for (let y = face.top; y < face.bottom; y++) {
    const row = rows[y]; if (!row) continue;
    for (let x = Math.max(row.left + Math.round(face.width * 0.06), 0); x < Math.min(W, row.right - face.width * 0.06); x++) {
      const p = y * W + x, i = p * 4, relative = (y - face.top) / face.height;
      if (filled[p] && relative > (profile ? 0.12 : 0.26) && relative < 0.83 && data[i + 3] > 100 && (data[i] + data[i + 1] + data[i + 2]) / 3 < threshold * 0.88) dark[p] = 1;
    }
  }

  const features = components(dark).filter(c => c.width > face.width * 0.035 && c.width < face.width * (profile ? 0.75 : 0.48) && c.height < face.height * 0.60 && c.count > 40);
  const eyeFor = side => features.filter(c => (side === 'left' ? c.cx < face.cx : c.cx > face.cx) && c.width > face.width * 0.08 && c.height > face.height * 0.06).sort((a, b) => b.count - a.count)[0];
  const near = profile ? features.filter(c => c.cx > face.left + face.width * 0.35 && c.width > face.width * 0.08 && c.height > face.height * 0.06).sort((a, b) => b.count - a.count)[0] : null;
  const left = profile ? near : eyeFor('left'), right = profile ? near : eyeFor('right');

  if (!left || !right || Math.abs(left.cy - right.cy) > face.height * 0.12 || Math.max(left.count, right.count) / Math.min(left.count, right.count) > 3) {
    throw Object.assign(new Error('양쪽 눈 영역을 정확히 찾지 못했어요. 기존 아바타를 유지해요.'), {
      diagnostics: {threshold, face: {...face, pixels: undefined}, features: features.map(c => ({...c, pixels: undefined}))},
      status: 502
    });
  }

  const center = (left.cx + right.cx) / 2;
  const refined = profile ? refineProfileEye(data, near, threshold, features) : refineEyes(data, [left, right], threshold, center);

  // 2) 앞머리·옆머리 가닥 복원 (실제 밝은 피부 픽셀은 침범하지 않도록 보호)
  const hairMask = Uint8Array.from(filled, v => v ? 0 : 1), queue = new Int32Array(N);
  const strandBottomLimit = Math.round(Math.max(left.bottom, right.bottom) + Math.max(left.height, right.height) * 0.75);
  let read = 0, write = 0, recoveredHairPixels = 0;

  for (let p = 0; p < N; p++) {
    if (hairMask[p] && data[p * 4 + 3] > 20 && (p / W | 0) < strandBottomLimit) {
      const i = p * 4;
      if ((data[i] + data[i + 1] + data[i + 2]) / 3 < threshold * 1.05) queue[write++] = p;
    }
  }

  while (read < write) {
    const p = queue[read++], x = p % W, y = (p / W) | 0;
    for (const q of [x > 0 ? p - 1 : -1, x < W - 1 ? p + 1 : -1, y > 0 ? p - W : -1, y < strandBottomLimit - 1 ? p + W : -1]) {
      if (q < 0 || hairMask[q] || refined.eyes[q] || refined.brows[q] || data[q * 4 + 3] < 16) continue;
      const i = q * 4, luma = (data[i] + data[i + 1] + data[i + 2]) / 3;
      if (rawSkin[q] && luma > threshold * 0.96) continue;
      if (luma > threshold * 1.04) continue;
      hairMask[q] = 1; queue[write++] = q; recoveredHairPixels++;
    }
  }

  const foreheadRecovery=recoverForeheadSkin(data,rawSkin,faceMask,filled,hairMask,refined,face,foreheadTopY,Math.min(left.top,right.top));
  const faceEdgeZone = dilate(filled, 3);
  let removedFaceEdgePixels = 0;
  const earProtectTop = Math.round(Math.min(left.top, right.top));
  for (let y = earProtectTop; y < W; y++) {
    for (let x = 2; x < W - 2; x++) {
      const p = y * W + x;
      if (!hairMask[p] || !faceEdgeZone[p] || refined.eyes[p] || refined.brows[p] || !data[p * 4 + 3]) continue;
      const open = [p - 2, p + 2, p - W * 2, p + W * 2].some(q => q < 0 || q >= N || data[q * 4 + 3] < 8);
      if (open && rawSkin[p]) { hairMask[p] = 0; removedFaceEdgePixels++; }
    }
  }

  for (let p = 0; p < N; p++) {
    if (refined.brows[p]) hairMask[p] = 1;
    if (refined.rejectedHair?.[p]) hairMask[p] = 1;
    if (refined.eyes[p]) hairMask[p] = 0;
  }

  const nose = {x: Math.round(center / W * 1000), y: Math.round((Math.max(left.bottom, right.bottom) + face.height * 0.025) / W * 1000)};
  const mouth = {x: nose.x, y: Math.round((face.bottom - face.height * 0.12) / W * 1000)};
  let neckY = face.bottom;
  for (let y = face.bottom; y < W * 0.95; y++) {
    let width = 0;
    for (let x = Math.round(center - face.width * 0.13); x <= center + face.width * 0.13; x++) if (faceMask[y * W + x]) width++;
    if (width > face.width * 0.06) neckY = y;
  }

  return {
    skinMask: filled,
    hairMask,
    browMask: refined.brows,
    eyeMask: refined.eyes,
    eyeInteriorMask: refined.eyeInterior,
    eyelidCreaseMask: refined.crease,
    eyelidCreaseBackground: refined.creaseBackground,
    requiredEyePixels: refined.required,
    requiredBrowPixels: refined.requiredBrows,
    landmarks: {
      nose, mouth,
      neck: {x: nose.x, y: Math.round((neckY - 3) / W * 1000)},
      leftEye: {x: Math.round(left.cx / W * 1000), y: Math.round(left.cy / W * 1000)},
      rightEye: {x: Math.round(right.cx / W * 1000), y: Math.round(right.cy / W * 1000)}
    },
    diagnostics: {
      threshold, recoveredHairPixels, removedFaceEdgePixels,
      recoveredForeheadSkinPixels:foreheadRecovery.pixels,
      skinBoundaryBounds:{left:face.left,top:foreheadRecovery.top,right:face.right,bottom:face.bottom},
      face: {left: face.left, top: face.top, right: face.right, bottom: face.bottom},
      eyes: [{...left, pixels: undefined}, {...right, pixels: undefined}],
      refinedEyes: refined.details
    }
  };
}
