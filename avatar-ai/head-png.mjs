import sharp from 'sharp';

// 머리 레이어 PNG 용량 줄이기(S15P31A207-300). 크기 1024×1024·형식 PNG·알파는 그대로 두고 바이트만 줄인다.
// 앱은 PNG 서명과 1024 크기만 본다(HeadResultPolicy.decodePng). 팔레트 PNG(색 형식 3 + tRNS)도 같은 PNG로 풀린다.
//   palette   256색 팔레트 양자화(libimagequant, quality 최대 95). 실측 회원 머리 6레이어 합계 82% 감소, 합성 PSNR 약 51dB
//   lossless  완전 투명 픽셀의 RGB만 0으로 지우고 zlib 9·적응 필터로 다시 쓴다. 보이는 픽셀과 알파는 그대로다(실측 57% 감소)
//   off       손대지 않는다(기본값). PM 확인 전까지 운영에서 켜지 않는다
// 환경 변수 HEAD_PNG_COMPRESSION으로 고른다.
export const HEAD_PNG_MODES = Object.freeze(['off', 'palette', 'lossless']);
// 다른 러너에게 보내는 레이어(GET /avatar/heads). 원본(master·right)과 합성(composite)은 그대로 둔다
export const HEAD_PNG_LAYER_KEYS = Object.freeze(['face', 'eyes', 'hair', 'rightFace', 'rightEyes', 'rightHair']);
const HEAD_SIZE = 1024;

export function headPngCompressionMode(env = process.env) {
  const mode = String(env.HEAD_PNG_COMPRESSION ?? '').trim().toLowerCase();
  return HEAD_PNG_MODES.includes(mode) ? mode : 'off';
}

async function encode(buffer, mode) {
  if (mode === 'palette') {
    return sharp(buffer).png({palette: true, quality: 95, effort: 10, dither: 1, compressionLevel: 9}).toBuffer();
  }
  const {data, info} = await sharp(buffer).ensureAlpha().raw().toBuffer({resolveWithObject: true});
  for (let i = 0; i < data.length; i += 4) if (data[i + 3] === 0) data[i] = data[i + 1] = data[i + 2] = 0;
  return sharp(data, {raw: {width: info.width, height: info.height, channels: 4}})
    .png({compressionLevel: 9, adaptiveFiltering: true})
    .toBuffer();
}

/**
 * 1024×1024 알파 PNG 하나를 줄인다. 꺼짐·대상 아님(다른 형식·크기·알파 없음)·결과가 더 크거나 같으면·결과 검사에 실패하면
 * 받은 버퍼를 그대로 돌려준다. 실패해도 머리 저장을 막지 않는다.
 */
export async function compressHeadPng(buffer, {mode = headPngCompressionMode()} = {}) {
  if (mode === 'off' || !HEAD_PNG_MODES.includes(mode) || !Buffer.isBuffer(buffer) || !buffer.length) return buffer;
  try {
    const meta = await sharp(buffer).metadata();
    if (meta.format !== 'png' || meta.width !== HEAD_SIZE || meta.height !== HEAD_SIZE || !meta.hasAlpha) return buffer;
    const out = await encode(buffer, mode);
    if (out.length >= buffer.length) return buffer;
    const check = await sharp(out).metadata();
    if (check.format !== 'png' || check.width !== HEAD_SIZE || check.height !== HEAD_SIZE || !check.hasAlpha) return buffer;
    return out;
  } catch (error) {
    console.warn('[head-png] 압축 실패, 원본 유지:', error.code || error.message);
    return buffer;
  }
}

/** 레이어 묶음에서 보여 주는 6레이어만 제자리에서 줄인다. 없는 레이어는 건너뛴다. */
export async function compressHeadLayers(layers, {mode = headPngCompressionMode()} = {}) {
  if (mode === 'off') return layers;
  for (const key of HEAD_PNG_LAYER_KEYS) if (Buffer.isBuffer(layers[key])) layers[key] = await compressHeadPng(layers[key], {mode});
  return layers;
}
