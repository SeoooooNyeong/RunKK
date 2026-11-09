import {splitAiLayers} from './ai-head-layers.mjs';
import {HEAD_SEGMENTATION_VERSION} from './head-layers.mjs';

// Both saved-head storage and the profile cache compare this version. A change
// to the shared front segmentation must invalidate the right layers as well.
export const RIGHT_LAYER_VERSION=`runkk-right-head-layers-v22-${HEAD_SEGMENTATION_VERSION}`;
export const RIGHT_IMAGE_FIELDS=['rightFaceImage','rightEyesImage','rightHairImage'];
export const RIGHT_LAYOUT={version:RIGHT_LAYER_VERSION,segmentationVersion:HEAD_SEGMENTATION_VERSION,direction:'right',canvas:[1024,1024],coordinateSpace:'shared-source',independentTransforms:false,faceIncludes:['nose','mouth','ear','neck','eyelidCreases'],eyesIncludes:['nearEye','eyelashes'],hairIncludes:['hair','eyebrows']};
export async function splitRightHead(image,options={}){
  try{
    const parts=await splitAiLayers(image,{...options,direction:'right'});
    return {rightFace:parts.face,rightEyes:parts.eyes,rightHair:parts.hair,rightLayerLayout:RIGHT_LAYOUT,rightLayerValidation:{...parts.validation,segmentationVersion:HEAD_SEGMENTATION_VERSION,extraction:'ai-image-edit'}};
  }catch(error){throw Object.assign(error,{status:502,code:'RIGHT_LAYER_EXTRACTION_FAILED'});}
}
export function rightLayerImages(layers){
  return Object.fromEntries(['rightFace','rightEyes','rightHair'].map((k,i)=>[RIGHT_IMAGE_FIELDS[i],`data:image/png;base64,${layers[k].toString('base64')}`]));
}
