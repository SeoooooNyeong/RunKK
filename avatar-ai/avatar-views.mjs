import multer from 'multer';
import sharp from 'sharp';
import {toFile} from 'openai';
import {createHash,randomUUID} from 'node:crypto';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import path from 'node:path';
import {requireUserId,userDirectory} from './user-storage.mjs';
import {splitRightHead,rightLayerImages,RIGHT_LAYER_VERSION,RIGHT_LAYOUT} from './right-head-layers.mjs';

export const AVATAR_VIEW_VERSION='runkk-head-profile-v5-face-scale-wind';
const directions=new Set(['right']);
const fail=(message,status=502,code='AVATAR_VIEW_INVALID')=>Object.assign(new Error(message),{status,code});
export function sourceHash(buffer){return createHash('sha256').update(buffer).digest('hex');}
function bounds(data,width,height){
  let left=width,top=height,right=-1,bottom=-1,visible=0,transparent=0;
  for(let y=0;y<height;y++)for(let x=0;x<width;x++){
    const a=data[(y*width+x)*4+3];if(a<16)transparent++;
    if(a>24){visible++;left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);}
  }
  return {left,top,right,bottom,width:right-left+1,height:bottom-top+1,visible,transparent};
}
export async function inspectFront(buffer){
  let decoded;try{decoded=await sharp(buffer,{limitInputPixels:24_000_000}).ensureAlpha().raw().toBuffer({resolveWithObject:true});}catch{throw fail('정면 기준 이미지를 읽지 못했어요.',422,'INVALID_FRONT_IMAGE');}
  const {data,info}=decoded;
  if(info.width!==1024||info.height!==1024)throw fail('정면 기준 이미지는 1024×1024여야 해요.',422,'INVALID_FRONT_IMAGE');
  const box=bounds(data,info.width,info.height);
  if(box.visible<5000||box.height<200||box.transparent/(info.width*info.height)<.02)throw fail('투명 배경의 정면 머리 PNG가 필요해요. 몸은 포함하지 마세요.',422,'INVALID_FRONT_IMAGE');
  return box;
}
export async function normalizeProfile(buffer,referenceBounds){
  const {data,info}=await sharp(buffer,{limitInputPixels:24_000_000}).toColourspace('srgb').ensureAlpha().raw().toBuffer({resolveWithObject:true});
  const box=bounds(data,info.width,info.height);
  if(box.visible<5000||box.height<150||box.transparent/(info.width*info.height)<.02)throw fail('우측 머리의 그림이나 투명 배경을 확인하지 못했어요.');
  if(box.width/box.height>2.5||box.left<2||box.top<2||box.right>=info.width-2||box.bottom>=info.height-2)throw fail('우측 머리가 잘렸거나 머리 PNG 규격과 맞지 않아요.');
  const scale=Math.min(referenceBounds.height/box.height,1008/box.width),height=Math.round(box.height*scale),width=Math.round(box.width*scale);
  const left=Math.max(8,Math.min(1016-width,Math.round((referenceBounds.left+referenceBounds.right+1-width)/2)));
  const resized=await sharp(data,{raw:{width:info.width,height:info.height,channels:4}}).extract({left:box.left,top:box.top,width:box.width,height:box.height}).resize(width,height,{fit:'fill'}).png().toBuffer();
  const top=referenceBounds.top+Math.round((referenceBounds.height-height)/2);
  return sharp({create:{width:1024,height:1024,channels:4,background:{r:0,g:0,b:0,alpha:0}}}).composite([{input:resized,left,top}]).png().toBuffer();
}
export function profilePrompt(direction,{frontEyeToChinPx=null}={}){
  if(!directions.has(direction))throw fail('우측 머리만 생성할 수 있어요.',400,'INVALID_VIEW_DIRECTION');
  return `Use case: identity-preserve character turnaround, static running-wind portrait.
Input IMAGE 1 is the immutable appearance reference: ONE illustrated front-facing RunKK HEAD, not a body. Create ONE coherent 90-degree RIGHT-FACING HEAD PROFILE of the SAME illustrated character. The nose must point toward the RIGHT edge. This is not a mirror of the front and not a three-quarter view. Only the near-side eye should be visible; hide the far eye naturally.
Preserve illustration style, recognizable facial design, eyes, skin palette, hair color/part/bangs/actual strand length, visible glasses and accessories. Include the face, nose, mouth, near ear, ALL hair and a very short skin neck stub only. Absolutely NO torso, shoulders, shirt, hoodie, clothing, hands, arms, legs, socks, shoes or whole body. Long hair is allowed below the chin but there must be NO body or fabric between the locks. Infer only unseen head surfaces; this is an illustrated estimate, not a measurement of the user's actual profile.
FACE SIZE STANDARD: size the face from the face itself, never from the wind-swept hair silhouette. Keep the near-eye-center to chin distance at ${Number.isFinite(frontEyeToChinPx)?`${Math.round(frontEyeToChinPx)}px on the 1024px canvas`: 'the same proportion as IMAGE 1'}. Preserve the same chibi face scale, eye-to-nose spacing, nose-to-mouth spacing, jaw size and neck width as the front reference. The wind-blown hair may extend sideways, but must not cause the face or head to be shrunk to fit its width.
STATIC WIND-SWEPT HAIR: depict a single frozen moment of running toward the RIGHT. Air comes from the RIGHT/front and carries the loose hair backward toward the LEFT, behind the head. Preserve the original hair volume, layered waves, highlights and texture. Long hair should form a full, gently lifted flowing silhouette behind the ear, with broad overlapping curved locks and softly curling tips; do not leave all long hair hanging vertically. Keep roots attached and the crown, face, eye and neck anatomically stable. Change the hair pose, never stretch its length or flatten it into a horizontal slab, thin strings, rigid spikes or a fan. For short hair, lift only existing loose tips slightly backward; for tied hair, move only the free tail; for bald or close-cropped hair, retain that style without inventing hair. Keep the face unobstructed.
Output ONE standalone complete HEAD in a 1024×1024 transparent PNG with real alpha. Fit the entire wind-swept silhouette with generous transparent margins on the LEFT and around every tip; scale uniformly if needed. Preserve head proportions. No cropped tips, wind lines, motion blur, animation frames, reference sheet, second head, text, logo, checkerboard, floor or cast shadow. Change ONLY the viewing direction and the wind-driven hair pose; no new haircut, style or person.`;
}
async function verifyFrontHead(client,visionModel,front){
  const r=await client.chat.completions.create({model:visionModel,temperature:0,max_tokens:200,response_format:{type:'json_object'},messages:[{role:'system',content:'Return JSON only. Do not assume success.'},{role:'user',content:[{type:'text',text:'Inspect this source illustration. Return {"headOnly":true,"frontFacing":true,"hasFace":true}. headOnly permits full hairstyle, ears and a very short skin neck, but is false if ANY torso, shoulders, clothing, hands, legs or shoes are drawn. Long hair below the chin is not a body. frontFacing means the face is approximately front-facing. Use false if uncertain.'},{type:'image_url',image_url:{url:`data:image/png;base64,${front.toString('base64')}`,detail:'high'}}]}]});
  let v;try{v=JSON.parse(r.choices?.[0]?.message?.content||'');}catch{throw fail('정면 머리 검증 응답을 확인하지 못했어요.');}
  if(v.headOnly!==true||v.frontFacing!==true||v.hasFace!==true)throw fail('몸·의류가 없는 정면 머리 PNG만 보내 주세요.',422,'INVALID_FRONT_IMAGE');
}
async function verifyProfile(client,visionModel,front,profile,direction,targetFaceScale){
  const response=await client.chat.completions.create({model:visionModel,temperature:0,max_tokens:500,response_format:{type:'json_object'},messages:[
    {role:'system',content:'Compare illustrated avatar images. Return JSON only. If uncertain use false/unknown, never assume success.'},
    {role:'user',content:[{type:'text',text:`IMAGE 1 is the front HEAD reference; IMAGE 2 must be the same head in a 90-degree RIGHT profile. Return {"headOnly":true,"completeHair":true,"profile":true,"direction":"right|left|unknown","sameHairAndStyle":true,"windAppropriate":true,"faceScale":{"frontEyeToChinPx":${Number.isFinite(targetFaceScale)?Math.round(targetFaceScale):0},"profileEyeToChinPx":0,"profileEye":{"x":0,"y":0},"profileChin":{"x":0,"y":0}}}. Measure the distance in PIXELS on the full 1024x1024 canvas from the center of the visible eye(s) to the lowest chin point in both images; profileEyeToChinPx is the distance for IMAGE 2. Report IMAGE 2 near-eye center and chin coordinates. Face proportions must match IMAGE 1 and be measured independently of hair. headOnly must be false if any shoulders, body, clothing, limbs or shoes appear. Only face, ears, full hair and short skin neck are allowed. completeHair means no cropped hair tips. profile means nose points RIGHT with at most the near eye visible, not front or three-quarter. sameHairAndStyle compares illustration, hair design/color/actual length, face design and visible accessories, not real identity; allow the silhouette to change as wind sweeps the same hair backward. windAppropriate means loose long hair visibly flows backward toward image LEFT, with natural volume and attached roots, not hanging entirely downward, stretched, spiky or threadlike. For short or tied hair, only existing loose tips or the free tail should respond. Bald or close-cropped hair passes windAppropriate when faithfully preserved without invented hair. False/unknown for uncertain results.`},
      {type:'image_url',image_url:{url:`data:image/png;base64,${front.toString('base64')}`,detail:'high'}},
      {type:'image_url',image_url:{url:`data:image/png;base64,${profile.toString('base64')}`,detail:'high'}}]}
  ]});
  let v;try{v=JSON.parse(response.choices?.[0]?.message?.content||'');}catch{throw fail('옆모습 검증 응답을 읽지 못했어요. 기존 정면은 유지해요.');}
  if(v.headOnly!==true||v.completeHair!==true||v.profile!==true||v.direction!==direction||v.sameHairAndStyle!==true||v.windAppropriate!==true)throw fail('우측 방향·바람에 흐르는 헤어·몸 미포함을 확인하지 못했어요. 기존 정면은 유지해요.');
  const measured=Number(v.faceScale?.profileEyeToChinPx),eye=v.faceScale?.profileEye;
  v.faceScale={targetEyeToChinPx:Number.isFinite(targetFaceScale)?targetFaceScale:null,measuredEyeToChinPx:Number.isFinite(measured)?measured:null,eye:Number.isFinite(Number(eye?.x))&&Number.isFinite(Number(eye?.y))?{x:Number(eye.x),y:Number(eye.y)}:null};
  return v;
}
async function scaleProfileFromFace(buffer,faceScale){
  const target=Number(faceScale?.targetEyeToChinPx),measured=Number(faceScale?.measuredEyeToChinPx),eye=faceScale?.eye;
  if(!Number.isFinite(target)||!Number.isFinite(measured)||target<30||measured<30||!eye||target/measured<.72||target/measured>1.38)return{image:buffer,scale:1};
  const scale=target/measured,x=Number(eye.x),y=Number(eye.y);
  if(!Number.isFinite(x)||!Number.isFinite(y)||x<0||x>1023||y<0||y>1023)return{image:buffer,scale:1};
  const size=Math.round(1024*scale),left=Math.round(x-x*scale),top=Math.round(y-y*scale),cropLeft=Math.max(0,-left),cropTop=Math.max(0,-top);
  const cropWidth=Math.min(size-cropLeft,1024-Math.max(0,left)),cropHeight=Math.min(size-cropTop,1024-Math.max(0,top));
  if(cropWidth<=0||cropHeight<=0)return{image:buffer,scale:1};
  const resized=await sharp(buffer).resize(size,size,{fit:'fill'}).png().toBuffer();
  const crop=await sharp(resized).extract({left:cropLeft,top:cropTop,width:cropWidth,height:cropHeight}).png().toBuffer();
  const image=await sharp({create:{width:1024,height:1024,channels:4,background:{r:0,g:0,b:0,alpha:0}}}).composite([{input:crop,left:Math.max(0,left),top:Math.max(0,top)}]).png().toBuffer();
  return{image,scale};
}
export function registerAvatarViews(app,{client,root,imageModel,visionModel,quality,ensureApiKey,isBusy,setBusy,splitProfile=(png,glassesPresent)=>splitRightHead(png,{client,imageModel,visionModel,quality,glassesPresent})}){
  const upload=multer({storage:multer.memoryStorage(),limits:{files:1,fileSize:8*1024*1024},fileFilter:(_req,file,cb)=>cb(file.mimetype==='image/png'?null:fail('정면 기준 이미지는 PNG로 보내 주세요.',400,'INVALID_FRONT_IMAGE'),file.mimetype==='image/png')});
  const directory=userId=>path.join(userDirectory(root,userId),'head-views');
  const filename=(userId,hash,direction,extension)=>path.join(directory(userId),`${hash}-${direction}.${extension}`);
  async function cached(userId,hash,direction){
    try{
      const m=JSON.parse(await readFile(filename(userId,hash,direction,'json'),'utf8'));
      if(m.userId!==userId||m.scope!=='head'||m.sourceHash!==hash||m.direction!==direction||m.viewVersion!==AVATAR_VIEW_VERSION||m.imageModel!==imageModel||m.visionModel!==visionModel||m.quality!==quality)return null;
      const png=await readFile(filename(userId,hash,direction,'png'));
      let layers;
      if(m.rightLayerLayout?.version===RIGHT_LAYER_VERSION){try{const buffers=await Promise.all(['face','eyes','hair'].map(part=>readFile(filename(userId,hash,direction,part+'.png'))));layers={rightFace:buffers[0],rightEyes:buffers[1],rightHair:buffers[2]};}catch(e){if(e.code!=='ENOENT')throw e;}}
      if(!layers){layers=await splitProfile(png,m.glassesPresent===true);for(const part of ['Face','Eyes','Hair'])await writeFile(filename(userId,hash,direction,part.toLowerCase()+'.png'),layers['right'+part]);m.rightLayerLayout=RIGHT_LAYOUT;m.rightLayerValidation=layers.rightLayerValidation;await writeFile(filename(userId,hash,direction,'json'),JSON.stringify(m,null,2));}
      return {...m,...rightLayerImages(layers),image:`data:image/png;base64,${png.toString('base64')}`,cached:true,saved:true,warning:null};
    }catch(error){if(error.code==='ENOENT')return null;if(error.code==='RIGHT_LAYER_EXTRACTION_FAILED')throw error;throw fail('저장된 옆모습을 읽지 못했어요. 자동으로 다시 생성하지 않았어요.');}
  }
  async function generate(userId,front,{lockHeld=false,glassesPresent=false,frontEyeToChinPx=null}={}){
    requireUserId(userId);const direction='right',referenceBounds=await inspectFront(front),hash=sourceHash(front);
    const found=await cached(userId,hash,direction);if(found)return found;
    ensureApiKey();let owns=false;
    if(!lockHeld){if(isBusy())throw fail('다른 이미지를 생성 중이에요. 완료 후 다시 시도해 주세요.',409,'GENERATION_BUSY');setBusy(true);owns=true;}
    try{
      await verifyFrontHead(client,visionModel,front);
      const output=await client.images.edit({model:imageModel,image:[await toFile(front,'runkk-front-head.png',{type:'image/png'})],prompt:profilePrompt(direction,{frontEyeToChinPx}),size:'1024x1024',quality,background:'transparent',output_format:'png'});
      const encoded=output.data?.[0]?.b64_json;if(!encoded)throw fail('옆모습 이미지 데이터가 없어요.');
      let png=await normalizeProfile(Buffer.from(encoded,'base64'),referenceBounds);
      const validation=await verifyProfile(client,visionModel,front,png,direction,frontEyeToChinPx);
      const faceScale=await scaleProfileFromFace(png,validation.faceScale);png=faceScale.image;validation.faceScale.appliedScale=Number(faceScale.scale.toFixed(3));
      const layers=await splitProfile(png,glassesPresent===true);
      const metadata={userId,viewId:randomUUID(),createdAt:new Date().toISOString(),sourceHash:hash,direction,scope:'head',viewVersion:AVATAR_VIEW_VERSION,imageModel,visionModel,quality,canvas:[1024,1024],referenceBounds,validation,staticImage:true,glassesPresent:glassesPresent===true,frontEyeToChinPx};
      Object.assign(metadata,{rightLayerLayout:RIGHT_LAYOUT,rightLayerValidation:layers.rightLayerValidation});
      let saved=true;try{await mkdir(directory(userId),{recursive:true});await writeFile(filename(userId,hash,direction,'png'),png);for(const part of ['Face','Eyes','Hair'])await writeFile(filename(userId,hash,direction,part.toLowerCase()+'.png'),layers['right'+part]);await writeFile(filename(userId,hash,direction,'json'),JSON.stringify(metadata,null,2));}catch{saved=false;}
      return {...metadata,...rightLayerImages(layers),image:`data:image/png;base64,${png.toString('base64')}`,cached:false,saved,warning:saved?null:'옆모습을 서버에 저장하지 못했어요. 현재 모습 PNG 저장으로 보관해 주세요.'};
    }finally{if(owns)setBusy(false);}
  }
  app.get('/api/heads/right-profile/:sourceHash',async(req,res,next)=>{
    try{
      const userId=requireUserId(req.query.userId);
      const {sourceHash:hash}=req.params,direction='right';
      if(!/^[a-f0-9]{64}$/.test(hash))return res.status(400).json({error:'우측 머리 조회 기준을 확인해 주세요.',code:'INVALID_VIEW_REFERENCE'});
      const found=await cached(userId,hash,direction);if(!found)return res.status(404).json({error:'이 사용자의 정면 머리에 저장된 우측 얼굴이 없어요.',code:'AVATAR_VIEW_NOT_FOUND'});res.json(found);
    }catch(error){next(error);}
  });
  app.post('/api/heads/right-profile',upload.single('front'),async(req,res,next)=>{
    try{
      const userId=requireUserId(req.body?.userId);
      if(!req.file)return res.status(400).json({error:'정면 아바타 기준 이미지가 필요해요.',code:'MISSING_FRONT_IMAGE'});
      const direction='right';if(Object.keys(req.body||{}).some(k=>k!=='userId'))return res.status(400).json({error:'우측 머리 API는 userId와 front 파일만 받아요. 방향·몸·코디 필드는 보내지 마세요.',code:'INVALID_VIEW_DIRECTION'});
      res.json(await generate(userId,req.file.buffer));
    }catch(error){next(error);}
  });
  return {generate,cached:(userId,hash)=>cached(userId,hash,'right')};
}
