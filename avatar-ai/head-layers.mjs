// head-layers.mjs
import sharp from 'sharp';
import {splitAiLayers} from './ai-head-layers.mjs';

export const HEAD_LAYER_VERSION = 'runkk-coherent-head-v2';
// v44 까지는 파트마다 images.edit 을 불러 레이어를 다시 그렸고, 그래서 세 장을
// 겹치면 좌표와 색이 어긋났다. v45 부터는 원본 픽셀을 결정론적으로 분할한다.
// 저장된 머리와 우측 레이어 캐시를 모두 무효화해야 하므로 반드시 바꿔야 한다.
export const HEAD_SEGMENTATION_VERSION = 'runkk-ai-source-pixels-v44';
export const HEAD_LAYOUT = Object.freeze({
  version: HEAD_LAYER_VERSION,
  segmentationVersion: HEAD_SEGMENTATION_VERSION,
  canvas: [1024, 1024],
  coordinateSpace: 'shared-source',
  landmarkSpace: 'source-canvas-1024',
  layers: ['face', 'eyes', 'hair'],
  faceIncludes: ['nose', 'mouth', 'ears', 'neck', 'eyelidCreases'],
  eyesIncludes: ['eyes', 'eyelashes'],
  hairIncludes: ['hair', 'eyebrows'],
  optionalHairIncludes: ['glasses'],
  independentTransforms: false,
  // 세 레이어를 z 순서대로 그대로 겹치면 원본과 코드값 단위로 같아진다.
  // 클라이언트에서 레이어별 보정(스케일·오프셋)을 하면 안 된다.
  extraction: 'deterministic-pixel-split',
  recomposition: 'exact-on-shared-canvas',
  // face 레이어는 눈·눈썹·안경 자리와 머리에 가린 이마까지 살색으로 복원돼 있다.
  // 헤어나 안경만 교체해도 구멍이 드러나지 않는다.
  faceLayerIsAmodal: true
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
    model, temperature: 0, max_tokens: 700, response_format: {type: 'json_object'},
    messages: [
      {role: 'system', content: 'Inspect the illustrated head. Return JSON only. Never invent hidden or absent features.'},
    {role: 'user', content: [
        {type: 'text', text: 'Return JSON with features:{bothEyes,nose,mouth,visibleNeckStub,glassesPresent}, skinPalette:0, neck:{x,y}, and landmarks:{leftEye:{x,y},rightEye:{x,y},leftEyebrow:{x,y},rightEyebrow:{x,y},nose:{x,y},mouth:{x,y},chin:{x,y},hairCrown:{x,y}}. glassesPresent is true only if actual eyeglass rims/bridge are visibly worn; ordinary eyes, highlights and eyelashes are not glasses. Every x/y is normalized 0..1000 across the FULL 1024x1024 image canvas, origin at its upper-left; left and right mean VIEWER left and right. Eye and eyebrow points are the visible centers. hairCrown is top of visible hair. chin is lowest jaw point. neck is center of the bottom edge of visible skin neck. Estimate actual positions in THIS image, not template positions. Return all points even when hair overlaps a brow, but set feature false if an eye, nose or mouth is absent or obscured. visibleNeckStub is true only for a distinct neck shape below the chin. skinPalette nearest illustrated color: 0 #f5cfbc,1 #e8b99d,2 #cf9775,3 #a66e4d,4 #754c39. Color matching only, no demographic inference.'},
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
  const x=Number(regions.neck?.x), y=Number(regions.neck?.y);
  if(!regions.features.visibleNeckStub || !Number.isFinite(x) || !Number.isFinite(y) || x<0 || x>1000 || y<0 || y>1000) {
    throw fail('생성한 머리의 목 연결점을 확인하지 못했어요. 이미지 생성 전에 다시 시도해 주세요.');
  }
  regions.neck={x,y};
  regions.features.glassesPresent=regions.features.glassesPresent===true;
  const names=['leftEye','rightEye','leftEyebrow','rightEyebrow','nose','mouth','chin','hairCrown'];
  const landmarks={};
  for(const name of names){
    const point=regions.landmarks?.[name];
    const px=Number(point?.x),py=Number(point?.y);
    if(!Number.isFinite(px)||!Number.isFinite(py)||px<0||px>1000||py<0||py>1000){
      throw fail(`생성한 머리의 ${name} 기준점을 확인하지 못했어요. 기존 아바타를 유지해요.`);
    }
    landmarks[name]={x:Math.round(px*1024/1000),y:Math.round(py*1024/1000)};
  }
  if(landmarks.leftEye.x>=landmarks.rightEye.x||landmarks.hairCrown.y>=landmarks.leftEye.y||landmarks.chin.y<=landmarks.mouth.y||y<=regions.landmarks.chin.y){
    throw fail('얼굴 기준점의 순서가 맞지 않아 기존 아바타를 유지해요.');
  }
  regions.landmarks=landmarks;
  regions.landmarks.neckBottom={x:Math.round(x*1024/1000),y:Math.round(y*1024/1000)};
  return regions;
}

export async function splitHead(master, regions = {}, options = {}) {
  const parts = await splitAiLayers(master, {
    ...options,
    landmarks: regions.landmarks,
    glassesPresent: regions.features?.glassesPresent
  });
  // z 순서대로 그대로 겹친다. 레이어별 보정은 없다. 정확성은 splitAiLayers 가
  // validation.recomposition 에 실제 측정값으로 보고한다.
  const composite = await sharp(parts.face)
    .composite([{input: parts.eyes}, {input: parts.hair}])
    .png()
    .toBuffer();

  if (parts.validation?.warning) {
    console.warn('[head-layers]', parts.validation.warning);
  }

  return {
    face: parts.face, eyes: parts.eyes, hair: parts.hair, composite,
    regions: {...regions, coordinateMethod: 'deterministic-pixel-split'},
    layout: HEAD_LAYOUT, segmentationVersion: HEAD_SEGMENTATION_VERSION,
    validation: parts.validation,
    // API 응답에서도 master 기준 좌표·박스·비율을 바로 확인할 수 있게 한다.
    layerReference: parts.validation?.reference
  };
}
