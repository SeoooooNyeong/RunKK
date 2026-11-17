import {createHash} from 'node:crypto';
import {latestUserHead,readUserHead,saveUserHead} from './user-storage.mjs';
import {rightLayerImages,RIGHT_IMAGE_FIELDS,RIGHT_LAYOUT,splitRightHead} from './right-head-layers.mjs';
import {mobileHead} from './mobile-composition.mjs';
import {HEAD_LAYER_VERSION,HEAD_SEGMENTATION_VERSION,HEAD_LAYOUT} from './head-layers.mjs';
import {splitAiLayers} from './ai-head-layers.mjs';
import sharp from 'sharp';
export const FACE_ASSETS_VERSION='runkk-face-assets-v1';
export const FACE_IMAGE_FIELDS=Object.freeze(['frontImage','rightImage','faceImage','eyesImage','hairImage']);
const dataUrl=buffer=>'data:image/png;base64,'+buffer.toString('base64');
const incomplete=()=>Object.assign(new Error('우측 얼굴까지 완성된 얼굴 세트가 필요해요. 우측 얼굴을 생성한 뒤 다시 조회해 주세요.'),{status:409,code:'FACE_ASSETS_INCOMPLETE'});
export function faceImages(layers){
  if(![layers.composite,layers.right,layers.face,layers.eyes,layers.hair].every(b=>Buffer.isBuffer(b)&&b.length))throw incomplete();
  const extra=['rightFace','rightEyes','rightHair'].every(k=>Buffer.isBuffer(layers[k]))?rightLayerImages(layers):{};
  return {frontImage:dataUrl(layers.master||layers.composite),rightImage:dataUrl(layers.right),faceImage:dataUrl(layers.face),eyesImage:dataUrl(layers.eyes),hairImage:dataUrl(layers.hair),...extra};
}
export function profileMetadata(profile){const {image,rightFaceImage,rightEyesImage,rightHairImage,metrics,warning,saved,...metadata}=profile;return metadata;}
export function assertFaceResult(data){
  if(data?.schemaVersion!==FACE_ASSETS_VERSION||!FACE_IMAGE_FIELDS.every(k=>typeof data[k]==='string'&&data[k].startsWith('data:image/png;base64,'))||['image','bodyImage','body','pack'].some(k=>Object.hasOwn(data,k)))throw incomplete();
  const count=value=>typeof value==='string'?Number(value.startsWith('data:image/')):value&&typeof value==='object'?Object.values(value).reduce((n,v)=>n+count(v),0):0;
  const render=data.mobile?.renderFaceImage;
  if(render!==undefined&&(typeof render!=="string"||!render.startsWith("data:image/png;base64,")))throw incomplete();
  const rightLayers=RIGHT_IMAGE_FIELDS.some(k=>data[k]!==undefined);
  if(rightLayers&&!RIGHT_IMAGE_FIELDS.every(k=>typeof data[k]==='string'&&data[k].startsWith('data:image/png;base64,')))throw incomplete();
  if(count(data)!==5+(render?1:0)+(rightLayers?3:0))throw incomplete();
  return data;
}
export async function savedFaceResult(root,userId,findRight,headId=null,{resplit=false,persist=true,splitOptions={}}={}){
  const {metadata,layers}=headId?await readUserHead(root,userId,headId):await latestUserHead(root,userId);
  // 오래된 분할 버전으로 저장된 머리를 결정론적으로 다시 쪼갠다. 완성 머리
  // (layers.master)만 있으면 되고 모델을 부르지 않으므로 공짜다. 기존
  // 사용자가 다시 생성하지 않아도 어긋난 레이어가 바로잡힌다.
  //
  // 실패해도 조회 자체를 막지 않는다. 옛 레이어를 그대로 두고 경고만 남긴다.
  if((resplit||metadata.segmentationVersion!==HEAD_SEGMENTATION_VERSION) && Buffer.isBuffer(layers.master) && layers.master.length){
    try{
      const glassesPresent=metadata.regions?.features?.glassesPresent===true;
      const resolvedFront=await splitAiLayers(layers.master,{...splitOptions,landmarks:metadata.regions?.landmarks,glassesPresent});
      Object.assign(layers,{face:resolvedFront.face,eyes:resolvedFront.eyes,hair:resolvedFront.hair});
      metadata.validation={...metadata.validation,...resolvedFront.validation,resplitFrom:metadata.segmentationVersion};
      metadata.layerReference=resolvedFront.layerReference;

      if(Buffer.isBuffer(layers.right)&&layers.right.length){
        try{
          const right=await splitRightHead(layers.right,{...splitOptions,glassesPresent});
          Object.assign(layers,{rightFace:right.rightFace,rightEyes:right.rightEyes,rightHair:right.rightHair});
          metadata.rightLayerLayout=RIGHT_LAYOUT;
          metadata.rightLayerValidation=right.rightLayerValidation;
        }catch(error){
          metadata.rightLayerValidation={...metadata.rightLayerValidation,resplitWarning:error.message};
        }
      }

      layers.composite=await sharp(layers.face).composite([{input:layers.eyes},{input:layers.hair}]).png().toBuffer();
      metadata.segmentationVersion=HEAD_SEGMENTATION_VERSION;
      metadata.layerLayout={...metadata.layerLayout,...HEAD_LAYOUT};
      metadata.mobile=await mobileHead(layers.face,metadata.alignment);
      if(persist)await saveUserHead(root,userId,layers,metadata);
    }catch(error){
      metadata.validation={...metadata.validation,resplitWarning:error.message};
      console.warn('[face-assets] 재분할 실패, 기존 레이어 유지:',error.code||error.message);
    }
  }
  const sourceHash=!layers.right?createHash('sha256').update(layers.master||layers.composite).digest('hex'):null;
  let rightProfile=metadata.rightProfile;
  if(!layers.right){
    const hash=sourceHash,profile=await findRight(userId,hash);
    if(!profile||profile.scope!=='head'||profile.direction!=='right'||profile.sourceHash!==hash||profile.userId!==userId)throw incomplete();
    layers.right=Buffer.from(profile.image.split(',')[1],'base64');rightProfile=profileMetadata(profile);
  }
  const mobile=metadata.mobile?.version==='runkk-mobile-compose-v2'&&metadata.mobile.renderFaceImage?.startsWith('data:image/png;base64,')?metadata.mobile:await mobileHead(layers.face,metadata.alignment);
  if(!['rightFace','rightEyes','rightHair'].every(k=>Buffer.isBuffer(layers[k])))throw incomplete();
  return assertFaceResult({...metadata,userId,schemaVersion:FACE_ASSETS_VERSION,headId:metadata.id,mobile,rightProfile,rightLayerLayout:RIGHT_LAYOUT,...faceImages(layers),saved:true,warning:null});
}
