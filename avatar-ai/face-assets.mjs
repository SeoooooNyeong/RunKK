import {createHash} from 'node:crypto';
import {latestUserHead,readUserHead,saveUserRightLayers} from './user-storage.mjs';
import {splitRightHead,rightLayerImages,RIGHT_IMAGE_FIELDS,RIGHT_LAYOUT} from './right-head-layers.mjs';
import {mobileHead} from './mobile-composition.mjs';
import sharp from 'sharp';
import {HEAD_LAYER_VERSION,HEAD_SEGMENTATION_VERSION,splitHead} from './head-layers.mjs';
import {defringeHair} from './hair-defringe.mjs';
import {fillFaceEyeHoles} from './public/eye-skin-fill.mjs';
export const FACE_ASSETS_VERSION='runkk-face-assets-v1';
export const FACE_IMAGE_FIELDS=Object.freeze(['frontImage','rightImage','faceImage','eyesImage','hairImage']);
const dataUrl=buffer=>'data:image/png;base64,'+buffer.toString('base64');
const incomplete=()=>Object.assign(new Error('우측 얼굴까지 완성된 얼굴 세트가 필요해요. 우측 얼굴을 생성한 뒤 다시 조회해 주세요.'),{status:409,code:'FACE_ASSETS_INCOMPLETE'});
export function faceImages(layers){
  if(![layers.composite,layers.right,layers.face,layers.eyes,layers.hair].every(b=>Buffer.isBuffer(b)&&b.length))throw incomplete();
  const extra=['rightFace','rightEyes','rightHair'].every(k=>Buffer.isBuffer(layers[k]))?rightLayerImages(layers):{};
  return {frontImage:dataUrl(layers.composite),rightImage:dataUrl(layers.right),faceImage:dataUrl(layers.face),eyesImage:dataUrl(layers.eyes),hairImage:dataUrl(layers.hair),...extra};
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
async function restoreCleanHair(layers,metadata){
  if(metadata.styleVersion!==HEAD_LAYER_VERSION||['runkk-eyes-only-hair-brows-v5',HEAD_SEGMENTATION_VERSION].includes(metadata.segmentationVersion))return 0;
  const face=await sharp(layers.face).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  const hair=await sharp(layers.hair).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  if(face.info.width!==1024||face.info.height!==1024||hair.info.width!==1024||hair.info.height!==1024)return 0;
  let left=1024,top=1024,right=0,bottom=0;
  for(let p=0;p<1024*1024;p++){
    const i=p*4;if(face.data[i+3]<220||face.data[i]<face.data[i+1]+5||face.data[i+1]<face.data[i+2]+2)continue;
    const x=p%1024,y=(p/1024)|0;left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
  }
  if(right<=left||bottom<=top)return 0;
  const adjusted=defringeHair(hair.data,face.data,1024,1024,{left,top,right,bottom});
  if(!adjusted)return 0;
  const options={raw:{width:1024,height:1024,channels:4}};
  [layers.face,layers.hair]=await Promise.all([sharp(face.data,options).png().toBuffer(),sharp(hair.data,options).png().toBuffer()]);
  layers.composite=await sharp(layers.face).composite([{input:layers.eyes},{input:layers.hair}]).png().toBuffer();
  metadata.validation={...metadata.validation,hairFringeAdjustedPixels:adjusted};
  return adjusted;
}
async function restoreEyeSkin(layers,metadata){
  if(metadata.styleVersion!==HEAD_LAYER_VERSION||metadata.segmentationVersion===HEAD_SEGMENTATION_VERSION)return 0;
  const face=await sharp(layers.face).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  const eyes=await sharp(layers.eyes).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  if(face.info.width!==1024||face.info.height!==1024||eyes.info.width!==1024||eyes.info.height!==1024)return 0;
  const filled=fillFaceEyeHoles(face.data,eyes.data,1024,1024);
  if(!filled)return 0;
  layers.face=await sharp(face.data,{raw:{width:1024,height:1024,channels:4}}).png().toBuffer();
  layers.composite=await sharp(layers.face).composite([{input:layers.eyes},{input:layers.hair}]).png().toBuffer();
  // Skin filling alone does not upgrade the semantic extraction version.
  metadata.validation={...metadata.validation,eyeSkinFilledPixels:filled};
  return filled;
}
export async function savedFaceResult(root,userId,findRight,headId=null){
  const {metadata,layers}=headId?await readUserHead(root,userId,headId):await latestUserHead(root,userId);
  const sourceHash=!layers.right?createHash('sha256').update(layers.composite).digest('hex'):null;
  let resegmented=false,resegmentationWarning=null;
  if(metadata.styleVersion===HEAD_LAYER_VERSION && metadata.segmentationVersion!==HEAD_SEGMENTATION_VERSION){
    // Reuse the saved image; no AI call or credit charge. Keep original files untouched.
    const regions=metadata.regions;
    if(regions?.features?.bothEyes && regions.features.nose && regions.features.mouth && regions.features.visibleNeckStub){
      try{
        const fresh=await splitHead(layers.composite,regions);
        Object.assign(layers,{face:fresh.face,eyes:fresh.eyes,hair:fresh.hair,composite:fresh.composite});
        Object.assign(metadata,{segmentationVersion:fresh.segmentationVersion,layerLayout:fresh.layout,regions:fresh.regions,validation:fresh.validation});
        resegmented=true;
      }catch(error){
        if(error.status!==502)throw error;
        // Keep the original version and assets; do not report a failed migration as success.
        resegmentationWarning='저장된 정면 이미지의 재분리에 실패해 이전 결과를 표시합니다.';
      }
    }
  }
  const restoredHair=resegmentationWarning?0:await restoreCleanHair(layers,metadata);
  const restoredEyeSkin=resegmentationWarning?0:await restoreEyeSkin(layers,metadata);
  let rightProfile=metadata.rightProfile;
  if(!layers.right){
    const hash=sourceHash,profile=await findRight(userId,hash);
    if(!profile||profile.scope!=='head'||profile.direction!=='right'||profile.sourceHash!==hash||profile.userId!==userId)throw incomplete();
    layers.right=Buffer.from(profile.image.split(',')[1],'base64');rightProfile=profileMetadata(profile);
  }
  const mobile=!resegmented&&!restoredHair&&!restoredEyeSkin&&metadata.mobile?.version==='runkk-mobile-compose-v2'&&metadata.mobile.renderFaceImage?.startsWith('data:image/png;base64,')?metadata.mobile:await mobileHead(layers.face,metadata.alignment);
  if(!['rightFace','rightEyes','rightHair'].every(k=>Buffer.isBuffer(layers[k]))){Object.assign(layers,await splitRightHead(layers.right));await saveUserRightLayers(root,userId,metadata.id,layers);}
  return assertFaceResult({...metadata,userId,schemaVersion:FACE_ASSETS_VERSION,headId:metadata.id,mobile,rightProfile,rightLayerLayout:RIGHT_LAYOUT,...faceImages(layers),saved:true,warning:resegmentationWarning});
}
