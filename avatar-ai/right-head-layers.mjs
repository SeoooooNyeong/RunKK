import sharp from 'sharp';
import {refineLayerEdges} from './layer-refinement.mjs';
import {pixelRegions} from './pixel-head-regions.mjs';
import {HEAD_SEGMENTATION_VERSION} from './head-layers.mjs';

// Both saved-head storage and the profile cache compare this version. A change
// to the shared front segmentation must invalidate the right layers as well.
export const RIGHT_LAYER_VERSION=`runkk-right-head-layers-v21-${HEAD_SEGMENTATION_VERSION}`;
export const RIGHT_IMAGE_FIELDS=['rightFaceImage','rightEyesImage','rightHairImage'];
export const RIGHT_LAYOUT={version:RIGHT_LAYER_VERSION,segmentationVersion:HEAD_SEGMENTATION_VERSION,direction:'right',canvas:[1024,1024],coordinateSpace:'shared-source',independentTransforms:false,faceIncludes:['nose','mouth','ear','neck'],eyesIncludes:['nearEye','eyelashes','eyelidCreases'],hairIncludes:['hair','eyebrows']};
export async function splitRightHead(image){
  try{
    const {data,info}=await sharp(image).ensureAlpha().raw().toBuffer({resolveWithObject:true});
    if(info.width!==1024||info.height!==1024)throw new Error('우측 머리는 1024×1024 PNG여야 해요.');
    const masks=pixelRegions(data,{profile:true}),parts=[Buffer.alloc(data.length),Buffer.alloc(data.length),Buffer.alloc(data.length)],counts=[0,0,0];
    for(let p=0;p<1024*1024;p++)if(data[p*4+3]){const k=masks.eyeMask[p]?1:masks.hairMask[p]?2:0;data.copy(parts[k],p*4,p*4,p*4+4);counts[k]++;}
    if(counts[0]<8000||counts[1]<400||counts[2]<2000)throw new Error('우측 얼굴·눈·헤어 영역을 구분하지 못했어요.');
    const finishing=refineLayerEdges(parts,data,masks,1024,1024);
    const [rightFace,rightEyes,rightHair]=await Promise.all(parts.map(p=>sharp(p,{raw:{width:1024,height:1024,channels:4}}).png().toBuffer()));
    return {rightFace,rightEyes,rightHair,rightLayerLayout:RIGHT_LAYOUT,rightLayerValidation:{segmentationVersion:HEAD_SEGMENTATION_VERSION,eyeSeparation:masks.diagnostics.refinedEyes,skinFillMethod:'harmonic-interpolation',fringeEngine:'fringe-studio-lab-v1',fringeStrength:70,studioSkinTransferredPixels:finishing.studioSkinTransferredPixels,faceToHairPixels:finishing.faceToHairPixels,hairToFacePixels:finishing.hairToFacePixels,foreheadStrandPixels:finishing.foreheadStrandPixels,hairMattePixels:finishing.hairMattePixels,repairedForeheadPixels:finishing.repairedForeheadPixels,edgeAdjustedPixels:finishing.edgeAdjustedPixels,skinFilledPixels:finishing.skinFilledPixels,visiblePixels:{face:counts[0],eyes:counts[1],hair:counts[2]}}};
  }catch(error){throw Object.assign(error,{status:502,code:'RIGHT_LAYER_EXTRACTION_FAILED'});}
}
export function rightLayerImages(layers){
  return Object.fromEntries(['rightFace','rightEyes','rightHair'].map((k,i)=>[RIGHT_IMAGE_FIELDS[i],`data:image/png;base64,${layers[k].toString('base64')}`]));
}
