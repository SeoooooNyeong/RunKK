import {splitAiLayers} from './ai-head-layers.mjs';
import {HEAD_SEGMENTATION_VERSION} from './head-layers.mjs';

// Both saved-head storage and the profile cache compare this version. A change
// to the shared front segmentation must invalidate the right layers as well.
// v27: 생성 호출로 다시 그리는 방식에서 결정론적 픽셀 분할로 바뀌었다.
export const RIGHT_LAYER_VERSION=`runkk-right-head-layers-v27-${HEAD_SEGMENTATION_VERSION}`;
export const RIGHT_IMAGE_FIELDS=['rightFaceImage','rightEyesImage','rightHairImage'];
export const RIGHT_LAYOUT={version:RIGHT_LAYER_VERSION,segmentationVersion:HEAD_SEGMENTATION_VERSION,direction:'right',canvas:[1024,1024],coordinateSpace:'shared-source',independentTransforms:false,faceIncludes:['nose','mouth','ear','neck','eyelidCreases'],eyesIncludes:['nearEye','eyelashes'],hairIncludes:['hair','eyebrows'],optionalHairIncludes:['glasses'],extraction:'ai-mask-master-pixel-split',recomposition:'exact-on-shared-canvas',faceLayerIsAmodal:true};
export async function splitRightHead(image,options={}){
  try{
    // 측면은 랜드마크가 없다. 분할기가 눈 밴드 비율로 떨어지고, 안경은
    // 렌즈 디스크 하나만 씨드로 써서 반대쪽이 안 보여도 테 전체를 찾는다.
    const parts=await splitAiLayers(image,{...options,direction:'right'});
    return {rightFace:parts.face,rightEyes:parts.eyes,rightHair:parts.hair,rightLayerLayout:RIGHT_LAYOUT,rightLayerValidation:{...parts.validation,segmentationVersion:HEAD_SEGMENTATION_VERSION,extraction:'ai-mask-master-pixel-split'}};
  }catch(error){throw Object.assign(error,{status:502,code:'RIGHT_LAYER_EXTRACTION_FAILED'});}
}
export function rightLayerImages(layers){
  return Object.fromEntries(['rightFace','rightEyes','rightHair'].map((k,i)=>[RIGHT_IMAGE_FIELDS[i],`data:image/png;base64,${layers[k].toString('base64')}`]));
}
