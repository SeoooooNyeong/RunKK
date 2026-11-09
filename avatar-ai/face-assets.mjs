import {createHash} from 'node:crypto';
import {latestUserHead,readUserHead,saveUserHead} from './user-storage.mjs';
import {rightLayerImages,RIGHT_IMAGE_FIELDS,RIGHT_LAYOUT} from './right-head-layers.mjs';
import {mobileHead} from './mobile-composition.mjs';
import {HEAD_LAYER_VERSION,HEAD_SEGMENTATION_VERSION} from './head-layers.mjs';
import {alignAiParts} from './ai-layer-align.mjs';
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
export async function savedFaceResult(root,userId,findRight,headId=null){
  const {metadata,layers}=headId?await readUserHead(root,userId,headId):await latestUserHead(root,userId);
  if(['runkk-ai-identity-checked-v35','runkk-ai-registered-layers-v36'].includes(metadata.segmentationVersion) && layers.master && layers.right &&
     ['rightFace','rightEyes','rightHair'].every(key=>Buffer.isBuffer(layers[key]))){
    const front=await alignAiParts(layers.master,layers);
    Object.assign(layers,front.parts);
    try{
      const right=await alignAiParts(layers.right,{face:layers.rightFace,eyes:layers.rightEyes,hair:layers.rightHair},{direction:'right'});
      Object.assign(layers,{rightFace:right.parts.face,rightEyes:right.parts.eyes,rightHair:right.parts.hair});
      metadata.rightLayerValidation={...metadata.rightLayerValidation,alignment:right.metrics};
    }catch(error){
      metadata.rightLayerValidation={...metadata.rightLayerValidation,alignmentWarning:error.message};
    }
    layers.composite=await sharp(layers.face).composite([{input:layers.eyes},{input:layers.hair}]).png().toBuffer();
    metadata.segmentationVersion=HEAD_SEGMENTATION_VERSION;
    metadata.validation={...metadata.validation,alignment:front.metrics};
    metadata.layerLayout={...metadata.layerLayout,segmentationVersion:HEAD_SEGMENTATION_VERSION};
    metadata.mobile=await mobileHead(layers.face,metadata.alignment);
    await saveUserHead(root,userId,layers,metadata);
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
