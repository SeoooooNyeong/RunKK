// head-layers.mjs
import sharp from 'sharp';
import {pixelRegions} from './pixel-head-regions.mjs';
import {refineLayerEdges} from './layer-refinement.mjs';

export const HEAD_LAYER_VERSION = 'runkk-coherent-head-v2';
export const HEAD_SEGMENTATION_VERSION = 'runkk-eyes-folds-hair-brows-v24';
export const HEAD_LAYOUT = Object.freeze({
  version: HEAD_LAYER_VERSION,
  segmentationVersion: HEAD_SEGMENTATION_VERSION,
  canvas: [1024, 1024],
  coordinateSpace: 'shared-source',
  layers: ['face', 'eyes', 'hair'],
  faceIncludes: ['nose', 'mouth', 'ears', 'neck'],
  eyesIncludes: ['eyes', 'eyelashes', 'eyelidCreases'],
  hairIncludes: ['hair', 'eyebrows'],
  independentTransforms: false
});

const fail = message => Object.assign(new Error(message), {status: 502});

export async function normalizeMaster(buffer) {
  const result = await sharp(buffer, {limitInputPixels: 24_000_000})
    .rotate()
    .resize(1024, 1024, {fit: 'contain', background: {r: 0, g: 0, b: 0, alpha: 0}})
    .toColourspace('srgb')
    .ensureAlpha()
    .png()
    .toBuffer();
  const {data, info} = await sharp(result).raw().toBuffer({resolveWithObject: true});
  let visible = 0, empty = 0;
  for (let i = 3; i < data.length; i += 4) {
    if (data[i] > 24) visible++;
    if (data[i] < 16) empty++;
  }
  if (visible < 2000 || empty / (info.width * info.height) < 0.015) {
    throw fail('완성 머리에 그림 또는 투명 배경이 없어요. 기존 아바타를 유지해요.');
  }
  return result;
}

export async function analyzeHeadRegions(client, model, image) {
  const guide = await sharp(image).flatten({background: '#eef4e7'}).png().toBuffer();
  const answer = await client.chat.completions.create({
    model, temperature: 0, max_tokens: 400, response_format: {type: 'json_object'},
    messages: [
      {role: 'system', content: 'Inspect the illustrated head. Return JSON only. Never invent hidden or absent features.'},
      {role: 'user', content: [
        {type: 'text', text: 'Return {"features":{"bothEyes":true,"nose":true,"mouth":true,"visibleNeckStub":true},"skinPalette":0}. Set a feature false if absent or obscured by hair. Both eyes, nose, mouth and a short visible neck directly below the jaw must be clearly drawn and naturally aligned. visibleNeckStub is true only when a distinct neck shape connects below the chin; lower-face pixels alone do not count. skinPalette nearest illustrated color: 0 #f5cfbc,1 #e8b99d,2 #cf9775,3 #a66e4d,4 #754c39. Color matching only, no demographic inference.'},
        {type: 'image_url', image_url: {url: `data:image/png;base64,${guide.toString('base64')}`, detail: 'high'}}
      ]}
    ]
  });
  const content = answer.choices?.[0]?.message?.content;
  let regions;
  try { regions = JSON.parse(content); } catch { throw fail('얼굴 영역 분석 응답을 읽지 못했어요. 기존 아바타를 유지해요.'); }
  if (!regions?.features?.bothEyes || !regions.features.nose || !regions.features.mouth) {
    throw fail('생성한 머리에 눈·코·입이 모두 보이지 않아 적용하지 않았어요.');
  }
  return regions;
}

export async function splitHead(master, regions) {
  if (!regions?.features?.bothEyes || !regions.features.nose || !regions.features.mouth || !regions.features.visibleNeckStub) {
    throw fail('눈·코·입 또는 턱 아래 목이 확인되지 않았어요. 기존 아바타를 유지해요.');
  }
  const {data, info} = await sharp(master).ensureAlpha().raw().toBuffer({resolveWithObject: true});
  if (info.width !== 1024 || info.height !== 1024) throw fail('분리 전 완성 머리는 1024×1024여야 해요.');

  let extracted;
  try { extracted = pixelRegions(data); } catch (error) { error.status = 502; throw error; }
  const {hairMask, eyeMask} = extracted;
  regions = {...regions, ...extracted.landmarks, coordinateMethod: 'pixel-components', diagnostics: extracted.diagnostics};

  const pixels = [Buffer.alloc(data.length), Buffer.alloc(data.length), Buffer.alloc(data.length)], counts = [0, 0, 0];
  for (let i = 0; i < data.length; i += 4) {
    if (!data[i + 3]) continue;
    const part = eyeMask[i / 4] ? 1 : hairMask[i / 4] ? 2 : 0;
    data.copy(pixels[part], i, i, i + 4);
    counts[part]++;
  }
  if (counts[0] < 8000 || counts[1] < 400 || counts[2] < 2000) {
    throw Object.assign(fail('얼굴·눈·헤어 분리 결과가 불완전해서 적용하지 않았어요.'), {counts, diagnostics: extracted.diagnostics});
  }

  let eyePixelsOutsideEyes = 0;
  for (let p = 0; p < info.width * info.height; p++) {
    if (extracted.requiredEyePixels[p] && data[p * 4 + 3] && (!pixels[1][p * 4 + 3] || pixels[0][p * 4 + 3] || pixels[2][p * 4 + 3])) eyePixelsOutsideEyes++;
  }
  if (eyePixelsOutsideEyes) throw fail('눈·속눈썹 픽셀이 다른 레이어에 남아 적용하지 않았어요.');

  let browPixelsOutsideHair = 0;
  for (let p = 0; p < info.width * info.height; p++) {
    if (extracted.requiredBrowPixels[p] && data[p * 4 + 3] && (!pixels[2][p * 4 + 3] || pixels[0][p * 4 + 3] || pixels[1][p * 4 + 3])) browPixelsOutsideHair++;
  }
  if (browPixelsOutsideHair) throw fail('눈썹 픽셀이 헤어 레이어와 분리되어 적용하지 않았어요.');

  for (const key of ['nose', 'mouth', 'neck', 'leftEye', 'rightEye']) {
    const p = regions[key], x = Math.min(1023, Math.round(p.x * 1024 / 1000)), y = Math.min(1023, Math.round(p.y * 1024 / 1000)), part = key.endsWith('Eye') ? 1 : 0;
    if (!pixels[part][(y * 1024 + x) * 4 + 3]) throw fail('코·입·눈 기준점이 분리 레이어와 맞지 않아 적용하지 않았어요.');
  }

  const reconstructed = Buffer.alloc(data.length);
  for (let i = 0; i < data.length; i += 4) for (const layer of pixels) if (layer[i + 3]) layer.copy(reconstructed, i, i, i + 4);
  let mismatched = 0;
  for (let i = 0; i < data.length; i += 4) {
    if (data[i + 3] && (data[i] !== reconstructed[i] || data[i + 1] !== reconstructed[i + 1] || data[i + 2] !== reconstructed[i + 2] || data[i + 3] !== reconstructed[i + 3])) mismatched++;
  }
  if (mismatched) throw fail('레이어 재합성이 원본과 일치하지 않아요.');

  const finishing = refineLayerEdges(pixels, data, extracted, info.width, info.height);

  const buffers = await Promise.all(pixels.map(p => sharp(p, {raw: {width: info.width, height: info.height, channels: 4}}).png().toBuffer()));
  const composite = await sharp(buffers[0]).composite([{input: buffers[1]}, {input: buffers[2]}]).png().toBuffer();

  return {
    face: buffers[0],
    eyes: buffers[1],
    hair: buffers[2],
    composite,
    regions,
    layout: HEAD_LAYOUT,
    segmentationVersion: HEAD_SEGMENTATION_VERSION,
    validation: {
      reconstructionPixelErrors: mismatched,
      eyePixelsOutsideEyes,
      browPixelsOutsideHair,
      edgeAdjustedPixels: finishing.edgeAdjustedPixels,
      eyeSkinFilledPixels: finishing.skinFilledPixels,
      hairMattePixels: finishing.hairMattePixels,
      fringeEngine: 'fringe-studio-lab-v1',
      fringeStrength: 70,
      studioSkinTransferredPixels: finishing.studioSkinTransferredPixels,
      repairedForeheadPixels: finishing.repairedForeheadPixels,
      faceToHairPixels: finishing.faceToHairPixels,
      hairToFacePixels: finishing.hairToFacePixels, foreheadStrandPixels: finishing.foreheadStrandPixels,
      visiblePixels: {face: counts[0], eyes: counts[1], hair: counts[2]}
    }
  };
}
