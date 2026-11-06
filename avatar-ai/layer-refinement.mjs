import {repairForeheadSlits} from './skin-edge-matting.mjs';
import {applyStudioFringe} from './studio-fringe-adapter.mjs';
import {reassignFaceHair} from './layer-ownership.mjs';
import {recoverForeheadStrands} from './forehead-residuals.mjs';
import {smoothSkinBacking} from './skin-inpaint.mjs';
// Shared front/profile finishing. Work only on skin-facing boundaries.
export function refineLayerEdges(parts, source, masks, width, height) {
  const reassigned=reassignFaceHair(parts,source,masks,width,height);
  const [face,eyes,hair]=parts, n=width*height;
  const neighbors=p=>{const x=p%width,y=Math.floor(p/width);return [x?p-1:-1,x<width-1?p+1:-1,y?p-width:-1,y<height-1?p+width:-1].filter(q=>q>=0);};
  const skin=p=>{const i=p*4;return face[i+3]>220 && face[i]>face[i+1]+3 && face[i+1]>face[i+2] && (face[i]+face[i+1]+face[i+2])/3>masks.diagnostics.threshold*1.08;};
  // Nearest actual skin samples, instead of a hard-coded light skin color.
  const nearest=new Int32Array(n).fill(-1), distance=new Uint16Array(n).fill(65535), queue=new Int32Array(n);
  let read=0,write=0;
  for(let p=0;p<n;p++)if(skin(p)){nearest[p]=p;distance[p]=0;queue[write++]=p;}
  while(read<write){const p=queue[read++];if(distance[p]>=48)continue;for(const q of neighbors(p))if(nearest[q]<0){nearest[q]=nearest[p];distance[q]=distance[p]+1;queue[write++]=q;}}
  const skinSource=Buffer.from(face);
  const distanceMap=(isSeed,limit)=>{
    const dist=new Uint16Array(n).fill(65535), nearestPixel=new Int32Array(n).fill(-1);
    let head=0,tail=0;
    for(let p=0;p<n;p++)if(isSeed(p)){dist[p]=0;nearestPixel[p]=p;queue[tail++]=p;}
    while(head<tail){const p=queue[head++];if(dist[p]>=limit)continue;
      for(const q of neighbors(p))if(dist[q]===65535){dist[q]=dist[p]+1;nearestPixel[q]=nearestPixel[p];queue[tail++]=q;}}
    return {dist,nearestPixel};
  };
  let adjusted=0;
  const removedSkin=new Uint8Array(n);
  // Hair uses the dedicated HSV/YCrCb + boundary-matting pass below.
  for(const layer of [eyes]) {
    const original=Buffer.from(layer), isEye=layer===eyes;
    // Include the complete narrow fringe band, including skin pockets between lashes.
    const edge=distanceMap(p=>original[p*4+3]<=8,28).dist;
    const pure=distanceMap(p=>{
      const i=p*4,s=nearest[p]*4;
      if(original[i+3]<220 || s<0 || distance[p]>48)return false;
      const brightness=(original[i]+original[i+1]+original[i+2])/3;
      const background=(skinSource[s]+skinSource[s+1]+skinSource[s+2])/3;
      if(isEye)return brightness<background-65;
      // A darkened skin/hair blend must not select itself as the "pure hair" color.
      const warmEdge=original[i]-original[i+1]>16 && original[i+1]-original[i+2]>8;
      return (edge[p]>=4&&brightness<background-65&&!warmEdge) ||
        (brightness<background-105&&edge[p]>=2);
    },64).nearestPixel;
    for(let p=0;p<n;p++) {
      const i=p*4,s=nearest[p]*4;
      // Pale lid folds are real eye strokes, not skin fringe. Recover their opacity
      // against skin above/below the stroke using an observed dark eye color.
      if(isEye && masks.eyelidCreaseMask?.[p] && original[i+3]) {
        const j=pure[p]*4, local=masks.eyelidCreaseBackground;
        const bg=local?.[i+3] ? [local[i],local[i+1],local[i+2]] : s>=0 ?
          [skinSource[s],skinSource[s+1],skinSource[s+2]] : null;
        if(j>=0 && bg){
          let dot=0,den=0;
          for(let k=0;k<3;k++){const f=original[j+k]-bg[k];dot+=(original[i+k]-bg[k])*f;den+=f*f;}
          if(den>900){
            const a=Math.max(0,Math.min(1,dot/den));
            let residual=0;
            for(let k=0;k<3;k++)residual+=(original[i+k]-(bg[k]+a*(original[j+k]-bg[k])))**2;
            if(a>0 && residual<=24*24){
              for(let k=0;k<3;k++)layer[i+k]=original[j+k];
              layer[i+3]=Math.max(1,Math.round(original[i+3]*a));adjusted++;
            }
          }
        }
        if(layer[i+3]<original[i+3])removedSkin[p]=1;
        // Never pass an identified crease through the near-skin deletion rule.
        continue;
      }
      if(!original[i+3] || s<0 || distance[p]>32 || edge[p]>(isEye?12:24))continue;
      if(isEye && masks.eyeInteriorMask?.[p])continue;
      // For hair, process only the boundary facing the detected face/eyebrows.
      const x=p%width,y=Math.floor(p/width),b=masks.diagnostics.skinBoundaryBounds||masks.diagnostics.face;
      if(!isEye && (x<b.left-12 || x>b.right+12 || y<b.top-12 || y>b.bottom))continue;
      let skinDifference=0;
      for(let k=0;k<3;k++)skinDifference+=(original[i+k]-skinSource[s+k])**2;
      const j=pure[p]*4;
      // Remove genuine skin even if no thick foreground region is nearby.
      if(skinDifference<18*18 && (isEye || j>=0)){
        layer.fill(0,i,i+4);removedSkin[p]=1;adjusted++;continue;
      }
      if(j<0)continue;
      let dot=0,den=0;
      for(let k=0;k<3;k++){const f=original[j+k]-skinSource[s+k];dot+=(original[i+k]-skinSource[s+k])*f;den+=f*f;}
      if(den<900)continue;
      const a=Math.max(0,Math.min(1,dot/den));let residual=0;
      for(let k=0;k<3;k++)residual+=(original[i+k]-(a*original[j+k]+(1-a)*skinSource[s+k]))**2;
      // Require a real foreground/skin mixture, not just a warm hue.
      const residualLimit=!isEye&&distance[p]<=8?28:18;
      if(a>.92 || residual>residualLimit*residualLimit)continue;
      for(let k=0;k<3;k++)layer[i+k]=original[j+k];
      layer[i+3]=Math.round(original[i+3]*a);
      if(layer[i+3]<4)layer.fill(0,i,i+4);
      removedSkin[p]=1;adjusted++;
    }
  }
  // Fill the removed eyes/brows on the face with surrounding skin. No outer-alpha expansion.
  const unknown=[];
  for(let p=0;p<n;p++) {
    const i=p*4;
    if(!source[i+3] || (!masks.skinMask[p]&&!removedSkin[p]) || nearest[p]<0)continue;
    if(!masks.eyeMask[p] && !masks.browMask[p] && !(masks.hairMask[p] && (distance[p]<=12 || removedSkin[p])))continue;
    const s=nearest[p]*4;
    for(let k=0;k<3;k++)face[i+k]=skinSource[s+k];
    face[i+3]=source[i+3];unknown.push(p);
  }
  const skinFilledPixels=smoothSkinBacking(face,skinSource,source,masks,unknown,width,height);
  const studio=applyStudioFringe(hair,face,masks,width,height);
  const foreheadStrandPixels=recoverForeheadStrands(face,hair,eyes,source,masks,width,height);
  const hairMattePixels=studio.changed;
  const repairedForeheadPixels=repairForeheadSlits(face,eyes,hair,source,masks,width,height);
  return {edgeAdjustedPixels:adjusted+hairMattePixels,skinFilledPixels,repairedForeheadPixels,hairMattePixels,studioSkinTransferredPixels:studio.transferred,...reassigned,foreheadStrandPixels};
}
