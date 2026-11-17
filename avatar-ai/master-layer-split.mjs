import sharp from 'sharp';

const SIZE = 1024;
const OPAQUE = 20;
const PARTS = ['face', 'eyes', 'hair'];

/**
 * AI가 만든 PNG는 누끼 마스크로만 사용하고, 최종 색상·좌표는 master에서
 * 복사한다. 세 레이어는 모두 동일한 1024×1024 master 캔버스를 유지한다.
 */
export async function splitMasterPixels(master, masks, options = {}) {
  const source = await raw(master);
  const maskBuffers = await Promise.all(PARTS.map(part => raw(masks[part])));
  if (source.info.width !== SIZE || source.info.height !== SIZE) {
    throw new Error('master 얼굴 캔버스는 1024×1024이어야 해요.');
  }
  if (maskBuffers.some(({info}) => info.width !== SIZE || info.height !== SIZE)) {
    throw new Error('AI 파츠 캔버스는 master와 같아야 해요.');
  }

  const outputs = PARTS.map(() => new Uint8Array(source.data.length));
  for (let index = 0; index < source.data.length; index += 4) {
    const pixel = index / 4;
    const x = pixel % SIZE;
    const y = Math.floor(pixel / SIZE);
    const skin = isSkinPixel(source.data[index], source.data[index + 1], source.data[index + 2]);
    const hair = maskBuffers[2].data[index + 3] > OPAQUE && !skin;
    const eyeWindow = insideEyeWindow(x, y, options.landmarks, options.direction);
    const eyeMask = maskBuffers[1].data[index + 3] > OPAQUE;
    // AI가 눈 마스크를 비워 보내는 경우에는 master의 눈 영역에서만 눈 픽셀을
    // 복구한다. 피부색은 제외하므로 눈 주변 살이 eyes에 붙지 않는다.
    const eyes = eyeWindow && !skin && (eyeMask || looksLikeEyePixel(source.data[index], source.data[index + 1], source.data[index + 2]));
    // face는 다른 두 레이어가 차지하지 않은 master 피부만 받는다. AI face
    // 마스크가 눈·헤어를 잘못 포함해도 여기서 제거한다.
    const face = !hair && !eyes && (maskBuffers[0].data[index + 3] > OPAQUE || skin);
    // 겹친 누끼는 앱 합성과 같은 순서로 hair → eyes → face 우선이다.
    const owner = hair ? 2 : eyes ? 1 : face ? 0 : -1;
    if (owner >= 0) outputs[owner].set(source.data.subarray(index, index + 4), index);
  }

  const parts = await Promise.all(outputs.map(data => png(data)));
  return {
    parts: {face: parts[0], eyes: parts[1], hair: parts[2]},
    reference: await reference(source.data, outputs, SIZE, SIZE)
  };
}

function insideEyeWindow(x, y, landmarks, direction) {
  if (!landmarks) return true;
  const points = direction === 'right'
    ? [landmarks.leftEye, landmarks.rightEye].filter(Boolean).slice(0, 1)
    : [landmarks.leftEye, landmarks.rightEye].filter(Boolean);
  if (!points.length) return true;
  const distance = Math.max(80, Math.abs((landmarks.leftEye?.x ?? points[0].x) - (landmarks.rightEye?.x ?? points[0].x)));
  const halfWidth = distance * 0.34;
  const halfHeight = distance * 0.24;
  return points.some(point => Math.abs(x - point.x) <= halfWidth && Math.abs(y - point.y) <= halfHeight);
}

function isSkinPixel(r, g, b) {
  // 흰자(r≈g≈b)와 갈색 홍채/헤어를 피부로 오인하지 않는다.
  // 피부는 따뜻한 색 편차가 있어야 하며, 너무 어두운 갈색은 헤어·홍채로 본다.
  const warmSkin = r > 135 && r > g + 8 && g > b + 5 && g - b < 72;
  const paleSkin = r > 170 && g > 120 && b > 95 && r > g + 8 && g > b + 5;
  return warmSkin || paleSkin;
}

function looksLikeEyePixel(r, g, b) {
  const maximum = Math.max(r, g, b);
  const minimum = Math.min(r, g, b);
  const neutralLight = minimum > 145 && maximum - minimum < 48;
  const darkLine = maximum < 105;
  const irisBrown = r > b + 12 && g > b + 4 && r < 190;
  return neutralLight || darkLine || irisBrown;
}

async function raw(input) {
  return sharp(input, {limitInputPixels: 24_000_000}).ensureAlpha().raw().toBuffer({resolveWithObject: true});
}

function png(data) {
  return sharp(data, {raw: {width: SIZE, height: SIZE, channels: 4}}).png().toBuffer();
}

async function reference(master, parts, width, height) {
  const bounds = data => {
    let left = width, top = height, right = -1, bottom = -1, pixels = 0;
    for (let y = 0, p = 0; y < height; y++) {
      for (let x = 0; x < width; x++, p += 4) {
        if (data[p + 3] <= 24) continue;
        pixels++; left = Math.min(left, x); top = Math.min(top, y);
        right = Math.max(right, x); bottom = Math.max(bottom, y);
      }
    }
    return pixels ? {left, top, right, bottom, width: right - left + 1, height: bottom - top + 1, pixels} : null;
  };
  const layerReference = Object.fromEntries(PARTS.map((part, index) => {
    const box = bounds(parts[index]);
    return [part, {
      bounds: box,
      normalized: box ? {left: box.left / width, top: box.top / height, width: box.width / width, height: box.height / height} : null,
      transform: {scale: 1, scaleX: 1, scaleY: 1, offsetX: 0, offsetY: 0}
    }];
  }));
  return {
    source: 'master', coordinateSpace: 'master-canvas-1024', canvas: [width, height],
    bounds: bounds(master), layers: layerReference, recomposition: 'exact-on-shared-canvas'
  };
}
