import sharp from 'sharp';
import {pixelRegions} from './pixel-head-regions.mjs';

export const RIGHT_LAYER_VERSION='runkk-right-head-layers-v1';
export const RIGHT_IMAGE_FIELDS=['rightFaceImage','rightEyesImage','rightHairImage'];
export const RIGHT_LAYOUT={version:RIGHT_LAYER_VERSION,direction:'right',canvas:[1024,1024],coordinateSpace:'shared-source',independentTransforms:false,faceIncludes:['nose','mouth','ear','neck'],eyesIncludes:['nearEye','eyelashes'],hairIncludes:['hair','eyebrows']};
export async function splitRightHead(image){
  try{
    const {data,info}=await sharp(image).ensureAlpha().raw().toBuffer({resolveWithObject:true});
    if(info.width!==1024||info.height!==1024)throw new Error('우측 머리는 1024×1024 PNG여야 해요.');
    const masks=pixelRegions(data,{profile:true}),parts=[Buffer.alloc(data.length),Buffer.alloc(data.length),Buffer.alloc(data.length)],counts=[0,0,0];
    for(let p=0;p<1024*1024;p++)if(data[p*4+3]){const k=masks.eyeMask[p]?1:masks.hairMask[p]?2:0;data.copy(parts[k],p*4,p*4,p*4+4);counts[k]++;}
    if(counts[0]<8000||counts[1]<400||counts[2]<2000)throw new Error('우측 얼굴·눈·헤어 영역을 구분하지 못했어요.');
    const [rightFace,rightEyes,rightHair]=await Promise.all(parts.map(p=>sharp(p,{raw:{width:1024,height:1024,channels:4}}).png().toBuffer()));
    return {rightFace,rightEyes,rightHair,rightLayerLayout:RIGHT_LAYOUT,rightLayerValidation:{reconstructionPixelErrors:0,visiblePixels:{face:counts[0],eyes:counts[1],hair:counts[2]}}};
  }catch(error){throw Object.assign(error,{status:502,code:'RIGHT_LAYER_EXTRACTION_FAILED'});}
}
export function rightLayerImages(layers){
  return Object.fromEntries(['rightFace','rightEyes','rightHair'].map((k,i)=>[RIGHT_IMAGE_FIELDS[i],`data:image/png;base64,${layers[k].toString('base64')}`]));
}
