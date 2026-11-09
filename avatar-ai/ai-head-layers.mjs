import sharp from 'sharp';
import {toFile} from 'openai';
import {alignAiParts} from './ai-layer-align.mjs';

const partPrompts = {
  hair: 'PIXEL-FAITHFUL PART EXTRACTION. Keep only the exact hairstyle and both eyebrows visible in IMAGE 1. Preserve every original hair strand, bangs, hairline, bun/ponytail if present, silhouette, color, shading and highlight at its original position. Eyebrows belong to this hair layer. Eyelashes do NOT: remove upper and lower eyelashes, eyeliner, eye corners, sclera, iris, pupils, eye highlights, eyelid folds/creases, and all skin. Do not create replacement hair, fill the face opening, or invent/change the hairstyle. Everything outside the original hair and eyebrow pixels is fully transparent.',
  eyes: 'PIXEL-FAITHFUL PART EXTRACTION. Keep only BOTH complete original eyes from IMAGE 1, including sclera, iris, pupil, highlights, upper/lower eyelid contours, eyeliner, eyelashes and double-eyelid crease lines. Preserve their exact shape, expression, size and source positions. Eyebrows are NOT part of the eyes: remove them completely. Remove bangs/hair, forehead and all other facial skin, nose, mouth, ears and neck; those pixels must be fully transparent. Do not redraw, beautify, enlarge, separate or reposition the eyes. Do not include any eyebrow strokes.',
  face: 'PIXEL-FAITHFUL FACE BASE EXTRACTION. Keep the exact original face shape and skin from IMAGE 1, including forehead, nose, mouth, ears and short neck. This is the blank base onto which the separate eyes layer will be overlaid. Therefore erase BOTH ENTIRE EYES: sclera, iris, pupil, highlights, eyeliner, upper/lower eyelid lines, eyelashes, eye corners AND double-eyelid crease lines. Replace those eye regions with continuous skin sampled and smoothly reconstructed from immediately adjacent forehead/cheek skin, matching the original color, gradients, shading and texture. Also remove all hair, bangs and eyebrows and naturally reconstruct the skin underneath. Do not leave eye-shaped holes, white patches, rectangular fill boundaries, blur smears, ghost eye shadows, or any eye/eyebrow marks. Do not alter the face outline, nose, mouth, ears, neck or overall face position.'
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
      {role:'user',content:[{type:'text',text:'IMAGE 1 is the source. Audit strict layer ownership and registration. IMAGE 2 (face base) must have the original face/nose/mouth/ears/neck, with BOTH eye areas reconstructed as natural continuous skin: absolutely no sclera, iris, pupil, eye highlight, eyeliner, eyelash, eyelid line, double-eyelid crease, eyebrow, hair or bangs; no rectangular seams or blank cutout holes. IMAGE 3 (eyes) must contain both complete eyes including sclera/iris/pupil/highlights, eyeliner, upper/lower lashes and double-eyelid creases, but absolutely NO eyebrows, hair, bangs or face skin. IMAGE 4 (hair) must contain the same original hair and both eyebrows, but absolutely NO eyelashes, eyeliner, eye/eyelid pixels, face skin or ears. Verify the eye, face and hair pixels remain at their source positions and the hairstyle is unchanged. Return JSON only: {"faceOnly":true,"eyesOnly":true,"hairOnly":true,"sameHair":true,"aligned":true}. Mark a field false if the corresponding requirement is visibly violated or uncertain.'},...images]}
    ]});
    let verdict;try{verdict=JSON.parse(review.choices?.[0]?.message?.content||'');}catch{throw fail('전체','파츠 확인 응답을 읽지 못했어요.');}
    if(['faceOnly','eyesOnly','hairOnly','sameHair','aligned'].some(key=>verdict[key]!==true))throw fail('전체','원본과 다른 헤어가 생성되거나 파츠가 섞여 결과를 적용하지 않았어요.');
  }
  return {...parts,validation:{method:'ai-image-edit-3-parts',model:imageModel,direction,imageCalls:3,canvas:[1024,1024],alignment:aligned.metrics}};
}
