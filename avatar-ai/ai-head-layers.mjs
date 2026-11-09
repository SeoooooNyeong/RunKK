import sharp from 'sharp';
import {toFile} from 'openai';
import {alignAiParts} from './ai-layer-align.mjs';

const partPrompts = {
  hair: 'Character design asset, the SAME hair and eyebrows from the supplied character ONLY, isolated on transparent background. Retain the exact hairstyle, silhouette, colors, bangs, highlights, roots and eyebrow shapes shown in the source. Remove the whole face, skin, eyes, eyelashes, eyelid creases, ears and neck; leave true transparency wherever they were. Do not invent a bun or change the haircut.',
  eyes: 'Character design asset, the SAME eyes, eyelashes and double eyelids from the supplied character ONLY, isolated on transparent background. Preserve each eye shape, color, size and expression. Remove eyebrows, bangs, hair, face skin, nose, mouth and ears; leave true transparency wherever they were.',
  face: 'Character design asset, the SAME face shape, forehead, skin, nose, mouth, ears and short neck from the supplied character ONLY, isolated on transparent background. Fill the eye and hair covered areas with matching skin. Remove ALL hair, bangs, eyebrows, eyes, eyelashes and double eyelids. The result must be a blank bald face base, not a complete head portrait.'
};

const fail = (part, detail) => Object.assign(new Error(`${part} AI 레이어 생성에 실패했어요. ${detail}`), {
  status:502, code:'HEAD_LAYER_EXTRACTION_FAILED'
});

function sharedLandmarkRule(landmarks) {
  if(!landmarks)return 'Use the unchanged source canvas as the coordinate reference.';
  const point=name=>`${name} (${landmarks[name].x}, ${landmarks[name].y})`;
  return `COMMON REGISTRATION CONTRACT for ALL three extracted PNGs. Coordinates are PIXELS on the ORIGINAL 1024x1024 canvas, origin top-left, x rightward, y downward. Visible feature centers: ${['leftEye','rightEye','leftEyebrow','rightEyebrow','nose','mouth','chin','hairCrown','neckBottom'].map(point).join('; ')}. Treat IMAGE 1 as the only visual source. Keep each retained feature centered at its listed coordinate. Keep its original width, height, silhouette and distance from the other landmarks. Transparent removal MUST NOT shift or enlarge remaining content. Leave the original surrounding empty margins. The face, eyes, and hair outputs must superimpose without any per-layer adjustment.`;
}

async function oneLayer(master, part, {client,imageModel,quality,direction,landmarks}) {
  const profile = direction === 'right';
  const prompt = `This is an ASSET EXTRACTION from IMAGE 1, NOT a new character illustration. ${sharedLandmarkRule(landmarks)} Delete unwanted content and preserve only the requested pixels and their positions. ${partPrompts[part]} ${profile ? 'Preserve the exact right-facing profile and wind-swept hair pose from IMAGE 1. Never draw a second eye.' : 'Preserve the exact front-facing pose from IMAGE 1.'} Use the input canvas unchanged at 1024x1024. Do not move, resize, recenter, mirror, redraw, stylize or replace the character. Put only this requested part at exactly the source x/y coordinates. All removed content and background must be alpha 0; no colored background, white face holes, head duplicate, clothes, text or extra objects.`;
  const result = await client.images.edit({
    model:imageModel,
    image:[await toFile(master,'source-head.png',{type:'image/png'})],
    prompt,size:'1024x1024',quality,background:'transparent',output_format:'png'
  });
  const encoded = result.data?.[0]?.b64_json;
  if (!encoded) throw fail(part,'이미지 응답이 비어 있어요.');
  const png = Buffer.from(encoded,'base64');
  let stats;
  try { stats=await sharp(png,{limitInputPixels:24_000_000}).stats(); }
  catch { throw fail(part,'PNG를 읽을 수 없어요.'); }
  const {width,height,hasAlpha} = await sharp(png).metadata();
  if(width!==1024||height!==1024||!hasAlpha||stats.channels[3]?.min!==0||stats.channels[3]?.max<128) {
    throw fail(part,'1024×1024 투명 PNG 규격에 맞지 않아요.');
  }
  return png;
}

export async function splitAiLayers(master,options={}) {
  const {client,imageModel,visionModel,quality='high',direction='front',landmarks}=options;
  if(!client||!imageModel)throw fail('전체','이미지 모델 연결 정보가 없어요.');
  let parts={};
  // Sequential calls keep one request's credit use bounded if a layer fails.
  for(const part of ['face','eyes','hair']) parts[part]=await oneLayer(master,part,{client,imageModel,quality,direction,landmarks});
  const aligned=await alignAiParts(master,parts,{direction});
  parts=aligned.parts;
  if(visionModel){
    const images=[master,parts.face,parts.eyes,parts.hair].map(image=>({type:'image_url',image_url:{url:`data:image/png;base64,${image.toString('base64')}`,detail:'high'}}));
    const review=await client.chat.completions.create({model:visionModel,temperature:0,max_tokens:220,response_format:{type:'json_object'},messages:[
      {role:'system',content:'Inspect the four images strictly. Return JSON only; false if unsure.'},
      {role:'user',content:[{type:'text',text:'IMAGE 1 is the source head. IMAGE 2 must contain ONLY its bald face, nose, mouth, ears and neck: no hair or eyes. IMAGE 3 must contain ONLY its eyes, lashes and eyelid creases: no hair or eyebrows. IMAGE 4 must contain ONLY its original hair and eyebrows: no face or eyes, and the SAME hairstyle as IMAGE 1. All three parts must be at the same original scale and position. Return {"faceOnly":true,"eyesOnly":true,"hairOnly":true,"sameHair":true,"aligned":true}. Mark false if any large unwanted facial/hair region appears or the hairstyle changes.'},...images]}
    ]});
    let verdict;try{verdict=JSON.parse(review.choices?.[0]?.message?.content||'');}catch{throw fail('전체','파츠 확인 응답을 읽지 못했어요.');}
    if(['faceOnly','eyesOnly','hairOnly','sameHair','aligned'].some(key=>verdict[key]!==true))throw fail('전체','원본과 다른 헤어가 생성되거나 파츠가 섞여 결과를 적용하지 않았어요.');
  }
  return {...parts,validation:{method:'ai-image-edit-3-parts',model:imageModel,direction,imageCalls:3,canvas:[1024,1024],alignment:aligned.metrics}};
}
