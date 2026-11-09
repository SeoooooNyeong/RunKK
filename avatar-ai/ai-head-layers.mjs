import sharp from 'sharp';
import {toFile} from 'openai';
import {alignAiParts} from './ai-layer-align.mjs';

const partPrompts = {
  hair: 'PIXEL-FAITHFUL PART EXTRACTION. Keep only the exact hairstyle, both eyebrows, and the original eyeglasses from IMAGE 1. Eyeglasses belong to this hair layer so the frames move with the hair: retain both rims, bridge, nose pads and visible temple arms in their exact positions. Keep lenses optically clear/transparent; do not include eyes, skin, glare or reflections inside the lens area. Preserve every original hair strand, bangs, hairline, bun/ponytail if present, silhouette, color, shading and highlight at its original position. Eyebrows belong to this layer. Eyelashes and eyelid crease lines do NOT: remove upper/lower eyelashes, eyeliner, eye corners, sclera, iris, pupils, highlights, eyelid folds/creases and all face skin. Do not create replacement hair, fill the face opening, or invent/change the hairstyle. Everything outside the original hair, eyebrow and eyeglass pixels is fully transparent.',
  eyes: 'PIXEL-FAITHFUL PART EXTRACTION. Keep only the original visible eye(s) from IMAGE 1, including sclera, iris, pupil, highlights, upper/lower eyelid contours, eyeliner and eyelashes. Preserve exact shape, expression, size and source positions. Double-eyelid crease lines belong to the FACE layer, and eyeglasses belong to the HAIR layer: do not include either in this eyes layer. Eyebrows are also NOT part of the eyes: remove them completely. Remove glasses frames, bridge, arms, lens tint/reflections, bangs/hair, forehead and all other facial skin, nose, mouth, ears and neck; those pixels must be fully transparent. Do not redraw, beautify, enlarge, separate or reposition the eyes.',
  face: 'PIXEL-FAITHFUL FACE BASE EXTRACTION. Keep the exact original face shape and skin from IMAGE 1, including forehead, nose, mouth, ears and short neck. This is the base onto which the separate eye and hair-with-glasses layers will be overlaid. Erase eye contents (sclera, iris, pupil, highlights, eyeliner, eye-corner marks and eyelashes) and replace those regions with continuous naturally shaded skin sampled/reconstructed from adjacent skin. Preserve the original double-eyelid crease line(s) as part of the face skin at their exact positions. Remove eyeglasses frames, bridge, nose pads, temple arms and lens artifacts, and naturally reconstruct skin where they covered it. Also remove all hair, bangs and eyebrows and naturally reconstruct the skin underneath. Do not leave eye-shaped holes, white patches, rectangular fill boundaries, blur smears, ghost eye contents, or glasses marks. Do not alter the face outline, nose, mouth, ears, neck or overall face position.'
};

const fail = (part, detail) => Object.assign(new Error(`${part} AI 레이어 생성에 실패했어요. ${detail}`), {
  status:502, code:'HEAD_LAYER_EXTRACTION_FAILED'
});

function sharedLandmarkRule(landmarks) {
  if(!landmarks)return 'Use the unchanged source canvas as the coordinate reference.';
  const point=name=>`${name} (${landmarks[name].x}, ${landmarks[name].y})`;
  return `COMMON REGISTRATION CONTRACT for ALL three extracted PNGs. Coordinates are PIXELS on the ORIGINAL 1024x1024 canvas, origin top-left, x rightward, y downward. Visible feature centers: ${['leftEye','rightEye','leftEyebrow','rightEyebrow','nose','mouth','chin','hairCrown','neckBottom'].map(point).join('; ')}. Treat IMAGE 1 as the only visual source. Keep each retained feature centered at its listed coordinate. Keep its original width, height, silhouette and distance from the other landmarks. Transparent removal MUST NOT shift or enlarge remaining content. Leave the original surrounding empty margins. The face, eyes, and hair outputs must superimpose without any per-layer adjustment.`;
}

async function estimateHairTransform(client,visionModel,master,hair){
  const images=await Promise.all([master,hair].map(async png=>{
    const flattened=await sharp(png).flatten({background:'#eef4e7'}).png().toBuffer();
    return {type:'image_url',image_url:{url:`data:image/png;base64,${flattened.toString('base64')}`,detail:'high'}};
  }));
  const response=await client.chat.completions.create({model:visionModel,temperature:0,max_tokens:260,response_format:{type:'json_object'},messages:[
    {role:'system',content:'Compare image geometry precisely. Return JSON only. Never invent coordinates for invisible content.'},
    {role:'user',content:[{type:'text',text:'IMAGE 1 is the AI-generated complete head and is the placement reference. IMAGE 2 is the transparent hair, eyebrows and eyeglasses extraction from that exact image. Measure only the visible HAIR silhouette in both images, excluding eyebrows, glasses, face, ears and neck so accessories do not affect its size. Include attached bangs, bun and loose hair strands. For each image return the hair bounding box left, top, right, bottom and the crown point (x,y) at the top-center/root of the hairstyle. Coordinates are on the full 1024x1024 canvas, integers 0..1023, origin top-left. Include confidence from 0 to 1. Return exactly {"source":{"left":0,"top":0,"right":0,"bottom":0,"crown":{"x":0,"y":0}},"layer":{"left":0,"top":0,"right":0,"bottom":0,"crown":{"x":0,"y":0}},"confidence":0.0}. The extracted hair is the same hairstyle, so report where it actually appears in IMAGE 2; do not assume it is already aligned.'},...images]}
  ]});
  let result;try{result=JSON.parse(response.choices?.[0]?.message?.content||'');}catch{return null;}
  const {source,layer}=result||{};
  const validBox=box=>box&&['left','top','right','bottom'].every(key=>Number.isFinite(box[key])&&box[key]>=0&&box[key]<=1023)&&box.right-box.left>80&&box.bottom-box.top>80;
  const validPoint=point=>point&&Number.isFinite(point.x)&&Number.isFinite(point.y)&&point.x>=0&&point.x<=1023&&point.y>=0&&point.y<=1023;
  if(!Number.isFinite(Number(result?.confidence))||Number(result.confidence)<0.55||!validBox(source)||!validBox(layer)||!validPoint(source.crown)||!validPoint(layer.crown))return null;
  const sourceWidth=source.right-source.left,sourceHeight=source.bottom-source.top;
  const layerWidth=layer.right-layer.left,layerHeight=layer.bottom-layer.top;
  const scale=Math.sqrt((sourceWidth/layerWidth)*(sourceHeight/layerHeight));
  if(!Number.isFinite(scale)||scale<0.7||scale>1.45)return null;
  const project=value=>(value-512)*scale+512;
  const sourceCenter={x:(source.left+source.right)/2,y:(source.top+source.bottom)/2};
  const layerCenter={x:(layer.left+layer.right)/2,y:(layer.top+layer.bottom)/2};
  // Blend the silhouette-center and crown anchors so the overall size and
  // the top/root placement both line up with the generated reference.
  const offsetX=.55*(sourceCenter.x-project(layerCenter.x))+.45*(source.crown.x-project(layer.crown.x));
  const offsetY=.55*(sourceCenter.y-project(layerCenter.y))+.45*(source.crown.y-project(layer.crown.y));
  if(Math.abs(offsetX)>220||Math.abs(offsetY)>220)return null;
  return {scale,offsetX,offsetY};
}

async function oneLayer(master, part, {client,imageModel,quality,direction,landmarks,glassesPresent}) {
  const profile = direction === 'right';
  const glassesRule=glassesPresent===true
    ? 'The source visibly has eyeglasses. Keep only their original frame pixels in the hair layer, exactly in place; remove glasses from face and eyes layers.'
    : 'The source has no eyeglasses. Do not invent, extract, or add any glasses in any layer.';
  const partPrompt=part==='hair'
    ? `PIXEL-FAITHFUL PART EXTRACTION. Keep only the exact hairstyle and both eyebrows. ${glassesRule} If present, glasses belong on this same hair layer so the frames move with hair. Preserve every original hair strand, bangs, hairline, bun/ponytail, silhouette, color, shading and highlight at the original position. Eyebrows belong to this layer. Eyelashes and eyelid crease lines do NOT: remove eyelashes, eyeliner, eye corners, sclera, iris, pupils, highlights, eyelid folds/creases and face skin. Do not create replacement hair or fill face opening. Everything outside original hair, eyebrows${glassesPresent===true?' and glasses':''} is fully transparent.`
    : partPrompts[part];
  const eyeScope = profile
    ? 'For the eyes part, retain ONLY the single visible near-side eye in this right-facing profile. Do not invent, reveal or add a far-side/second eye.'
    : 'For the eyes part, retain both visible eyes in the front-facing source.';
  const prompt = `This is an ASSET EXTRACTION from IMAGE 1, NOT a new character illustration. ${sharedLandmarkRule(landmarks)} Delete unwanted content and preserve only the requested pixels and their positions. ${partPrompt} ${glassesRule} ${eyeScope} ${profile ? 'Preserve the exact right-facing profile and wind-swept hair pose from IMAGE 1.' : 'Preserve the exact front-facing pose from IMAGE 1.'} Use the input canvas unchanged at 1024x1024. Do not move, resize, recenter, mirror, redraw, stylize or replace the character. Put only this requested part at exactly the source x/y coordinates. All removed content and background must be alpha 0; no colored background, white face holes, head duplicate, clothes, text or extra objects.`;
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
  const {client,imageModel,visionModel,quality='high',direction='front',landmarks,glassesPresent}=options;
  if(!client||!imageModel)throw fail('전체','이미지 모델 연결 정보가 없어요.');
  let parts={};
  // Sequential calls keep one request's credit use bounded if a layer fails.
  for(const part of ['face','eyes','hair']) parts[part]=await oneLayer(master,part,{client,imageModel,quality,direction,landmarks,glassesPresent});
  let aligned=null;
  let verdict={};
  if(visionModel){
    const images=[master,parts.face,parts.eyes,parts.hair].map(image=>({type:'image_url',image_url:{url:`data:image/png;base64,${image.toString('base64')}`,detail:'high'}}));
    const eyeScope=direction==='right'?'IMAGE 3 must contain ONLY the one visible near-side eye in the right-facing profile; a far/second eye must not be added.':'IMAGE 3 must contain the two original front-facing eyes.';
    const glassesCheck=glassesPresent===true?'The source has glasses; IMAGE 4 must retain them and IMAGE 2/3 must not contain them.':'The source has no glasses; do not invent any in IMAGE 4.';
    const review=await client.chat.completions.create({model:visionModel,temperature:0,max_tokens:650,response_format:{type:'json_object'},messages:[
      {role:'system',content:'Inspect the four images strictly. Return JSON only; false if unsure.'},
      {role:'user',content:[{type:'text',text:`IMAGE 1 is the complete source avatar and placement standard. IMAGE 2 is the face-only layer (face outline, nose, mouth, ears and neck); IMAGE 3 is the eyes-only layer; IMAGE 4 is the hair, eyebrows and optional glasses layer. Audit ownership as before. ${eyeScope} ${glassesCheck}
All four files are 1024x1024 with origin at top-left. Also return semantic bounding boxes for the visible intended content, in PIXEL coordinates 0..1023: boxes.source.face = face outline including ears and neck but excluding hair; boxes.source.eyes = both eyes or only the visible near eye for right profile, including lashes; boxes.source.hair = hair silhouette plus eyebrows and glasses only if present. Return corresponding boxes.layers.face, boxes.layers.eyes, boxes.layers.hair for the same content in IMAGES 2,3,4. Each box is {left,top,right,bottom}; do not include transparent canvas margins or unrelated features. These boxes are used to register the extracted part to the exact source proportions while preserving aspect ratio. Return JSON: {"faceOnly":true,"eyesOnly":true,"hairOnly":true,"sameHair":true,"glassesRuleSatisfied":true,"boxes":{"source":{"face":{"left":0,"top":0,"right":0,"bottom":0},"eyes":{"left":0,"top":0,"right":0,"bottom":0},"hair":{"left":0,"top":0,"right":0,"bottom":0}},"layers":{"face":{"left":0,"top":0,"right":0,"bottom":0},"eyes":{"left":0,"top":0,"right":0,"bottom":0},"hair":{"left":0,"top":0,"right":0,"bottom":0}}}}.`},...images]}
    ]});
    try{verdict=JSON.parse(review.choices?.[0]?.message?.content||'');}catch{verdict={};}
    const failed=['faceOnly','eyesOnly','hairOnly','sameHair','glassesRuleSatisfied'].filter(key=>verdict[key]===false);
    if(failed.length)throw fail('전체',`AI 검수에서 파츠 소유권 불일치를 발견했어요 (${failed.join(', ')}).`);
    aligned=await alignAiParts(master,parts,{sourceBoxes:verdict.boxes?.source,layerBoxes:verdict.boxes?.layers});
  }
  if(!aligned)aligned=await alignAiParts(master,parts,{direction});
  parts=aligned.parts;
  return {...parts,validation:{method:'ai-image-edit-3-parts',model:imageModel,direction,glassesPresent:glassesPresent===true,imageCalls:3,canvas:[1024,1024],alignment:aligned.metrics}};
}
