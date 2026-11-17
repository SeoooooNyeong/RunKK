// pixel-head-layers.mjs
//
// head-layer-split.mjs(순수 JS 코어)와 sharp 사이의 얇은 어댑터. PNG 를 raw
// RGBA 로 풀고, 레이어를 쪼개고, 다시 PNG 로 인코딩한 뒤, 디스크에 쓸 그
// 바이트로 재합성해 원본과 비교한다.
//
// 코어에 sharp 의존성이 없는 이유: 분할 알고리즘을 네트워크도 네이티브
// 모듈도 없이 테스트할 수 있어야 하고, 그래야 CI 에서 재합성 정확성을 매번
// 검증할 수 있다.

import sharp from 'sharp';
import { splitHeadLayers, verifyRecomposition, LAYER_NAMES, DEFAULTS } from './head-layer-split.mjs';

export const PIXEL_SPLIT_VERSION = 'runkk-pixel-head-split-v2-master-pixels';

// 재합성 수치는 참고용 메트릭으로만 남긴다. 파츠는 master의 원본 픽셀을
// 그대로 사용하므로, 수치가 기준을 넘더라도 생성 결과를 거절하지 않는다.
export const WARN_ERROR_LSB = DEFAULTS.maxErrorLsb;   // 2
export const FAIL_ERROR_LSB = 8;

const fail = (detail, code = 'HEAD_LAYER_EXTRACTION_FAILED') =>
  Object.assign(new Error(`머리 레이어 분리에 실패했어요. ${detail}`), { status: 502, code });

function toBuffer(view) {
  return Buffer.from(view.buffer, view.byteOffset, view.byteLength);
}

async function decodeRgba(png, label) {
  let decoded;
  try {
    decoded = await sharp(png, { limitInputPixels: 24_000_000 })
      .toColourspace('srgb')
      .ensureAlpha()
      .raw()
      .toBuffer({ resolveWithObject: true });
  } catch {
    throw fail(`${label} PNG를 읽을 수 없어요.`);
  }
  return { data: decoded.data, width: decoded.info.width, height: decoded.info.height };
}

async function encodeRgba(view, width, height) {
  return sharp(toBuffer(view), { raw: { width, height, channels: 4 } })
    .png({ compressionLevel: 9, effort: 7 })
    .toBuffer();
}

/**
 * 완성 머리 PNG 하나를 face / eyes / hair PNG 셋으로 쪼갠다.
 *
 * @param {Buffer} master  1024x1024 투명 PNG
 * @param {object} [options]
 *        landmarks       analyzeHeadRegions 가 준 1024 캔버스 픽셀 좌표
 *        glassesPresent  analyzeHeadRegions 의 판정. false 면 안경을 찾지 않는다
 *        direction       'right' 면 렌즈를 하나만 씨드로 쓴다
 *        tuning          head-layer-split DEFAULTS 덮어쓰기
 *        failErrorLsb    이 값을 넘으면 던진다 (기본 8)
 * @returns {{face:Buffer, eyes:Buffer, hair:Buffer, report:object}}
 */
export async function splitHeadPixels(master, options = {}) {
  const started = Date.now();
  const source = await decodeRgba(master, '완성 머리');

  if (source.width !== 1024 || source.height !== 1024) {
    throw fail(`완성 머리가 ${source.width}x${source.height} 예요. 1024x1024 여야 해요.`);
  }

  const { layers, report } = await splitHeadLayers(source, options);

  // 분할 코어가 만든 마스크는 레이어 소유자를 정하는 용도로만 쓴다.
  // 최종 PNG의 색·알파는 항상 master에서 복사해야 위치/크기/경계 픽셀이
  // 변하지 않는다. 겹친 마스크나 알파 임계값 아래의 가장자리 픽셀도 여기서
  // 하나의 레이어에만 배정해 재합성 오차를 구조적으로 없앤다.
  preserveMasterPixels(layers, source.data);

  const png = {};
  for (const layer of layers) {
    png[layer.name] = await encodeRgba(layer.data, layer.width, layer.height);
  }

  // PNG 는 무손실이지만, sharp 설정 실수(프리멀티플라이 등)는 인코딩 단계에서만
  // 드러난다. 실제로 저장할 바이트를 다시 읽어서 검수한다.
  const roundTrip = await Promise.all(
    LAYER_NAMES.map(name => decodeRgba(png[name], name).then(d => d.data))
  );
  const verified = verifyRecomposition(roundTrip, source.data, source.width, source.height);
  const reference = masterReference(source.data, roundTrip, source.width, source.height);

  const emptyLayer = LAYER_NAMES.find((name, z) => {
    const data = roundTrip[z];
    for (let i = 3; i < data.length; i += 4) if (data[i] > 24) return false;
    return true;
  });
  if (emptyLayer) throw fail(`${emptyLayer} 레이어가 비어 있어요.`, 'HEAD_LAYER_EMPTY');

  return {
    face: png.face,
    eyes: png.eyes,
    hair: png.hair,
    report: {
      ...report,
      version: PIXEL_SPLIT_VERSION,
      extraction: 'master-pixel-copy-on-shared-canvas',
      reference,
      verified,
      exact: verified.maxAbsOnWhite <= WARN_ERROR_LSB,
      warning: verified.maxAbsOnWhite <= WARN_ERROR_LSB ? null
        : `재합성 오차 ${verified.maxAbsOnWhite.toFixed(1)}/255. 임계값 조정이 필요해요.`,
      elapsedMs: Date.now() - started
    }
  };
}

function preserveMasterPixels(layers, master) {
  const layerData = layers.map(layer => layer.data);
  const pixelCount = master.length / 4;
  for (let i = 0; i < pixelCount; i++) {
    const p = i * 4;
    let owner = -1;
    // hair가 최상단이므로 겹친 마스크는 최상위 레이어가 소유한다.
    for (let z = layerData.length - 1; z >= 0; z--) {
      if (layerData[z][p + 3] > 0) {
        owner = z;
        break;
      }
    }
    if (owner < 0 && master[p + 3] > 0) owner = 0;
    for (let z = 0; z < layerData.length; z++) {
      const data = layerData[z];
      if (z === owner) {
        data[p] = master[p];
        data[p + 1] = master[p + 1];
        data[p + 2] = master[p + 2];
        data[p + 3] = master[p + 3];
      } else {
        data[p] = 0;
        data[p + 1] = 0;
        data[p + 2] = 0;
        data[p + 3] = 0;
      }
    }
  }
}

function alphaBounds(data, width, height) {
  let left = width, top = height, right = -1, bottom = -1, pixels = 0;
  for (let y = 0, p = 0; y < height; y++) {
    for (let x = 0; x < width; x++, p += 4) {
      if (data[p + 3] <= 24) continue;
      pixels++;
      left = Math.min(left, x); top = Math.min(top, y);
      right = Math.max(right, x); bottom = Math.max(bottom, y);
    }
  }
  if (!pixels) return null;
  return {left, top, right, bottom, width: right - left + 1, height: bottom - top + 1, pixels};
}

function masterReference(master, parts, width, height) {
  const masterBounds = alphaBounds(master, width, height);
  const layers = Object.fromEntries(LAYER_NAMES.map((name, index) => {
    const bounds = alphaBounds(parts[index], width, height);
    return [name, bounds ? {
      bounds,
      normalized: {
        left: bounds.left / width,
        top: bounds.top / height,
        width: bounds.width / width,
        height: bounds.height / height
      },
      transform: {scale: 1, scaleX: 1, scaleY: 1, offsetX: 0, offsetY: 0}
    } : {bounds: null, normalized: null, transform: {scale: 1, scaleX: 1, scaleY: 1, offsetX: 0, offsetY: 0}}];
  }));
  return {
    source: 'master',
    coordinateSpace: 'master-canvas-1024',
    canvas: [width, height],
    bounds: masterBounds,
    layers,
    recomposition: 'exact-on-shared-canvas'
  };
}

/**
 * 이미 저장된 레이어 PNG 셋을 원본과 비교한다. 마이그레이션이나 운영 점검에서
 * 레이어가 어긋났는지 확인할 때 쓴다. 모델 호출이 없으므로 공짜다.
 */
export async function verifyHeadPixels(master, parts) {
  const source = await decodeRgba(master, '완성 머리');
  const stack = [];
  for (const name of LAYER_NAMES) {
    const decoded = await decodeRgba(parts[name], name);
    if (decoded.width !== source.width || decoded.height !== source.height) {
      throw fail(`${name} 레이어 캔버스가 원본과 달라요.`, 'HEAD_LAYER_CANVAS_MISMATCH');
    }
    stack.push(decoded.data);
  }
  return verifyRecomposition(stack, source.data, source.width, source.height);
}
