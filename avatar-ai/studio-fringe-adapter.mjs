import {defringe} from './fringe-studio-engine.mjs';
import {skinColor} from './skin-edge-matting.mjs';

export function applyStudioFringe(hair,face,masks,w,h){
  const before=new Uint8ClampedArray(hair);
  const faceBefore=Buffer.from(face);
  // Use the supplied studio engine and its default strength, without stacking
  // the previous HSV/RGB remover over its output.
  const cleaned=defringe(before,w,h,70);
  let transferred=0;
  for(let p=0;p<w*h;p++){
    const i=p*4,oldAlpha=before[i+3],newAlpha=cleaned.pixels[i+3];
    if(!oldAlpha||newAlpha>=oldAlpha)continue;
    const x=p%w,y=Math.floor(p/w),samples=[[],[],[]];
    // Inverting the imperfect mixture per pixel puts the hair-shaped residual
    // back into the face. Estimate a clean background independently instead.
    for(let dy=-20;dy<=20;dy+=4)for(let dx=-20;dx<=20;dx+=4){
      const xx=x+dx,yy=y+dy;if(xx<0||xx>=w||yy<0||yy>=h||dx*dx+dy*dy<36)continue;
      const q=yy*w+xx,j=q*4;
      if(faceBefore[j+3]<240||masks.hairMask[q]||masks.eyeMask[q]||masks.browMask[q])continue;
      if(!skinColor(faceBefore[j],faceBefore[j+1],faceBefore[j+2]))continue;
      if(cleaned.skinColor&&Math.hypot(...[0,1,2].map(k=>faceBefore[j+k]-cleaned.skinColor[k]))>55)continue;
      for(let k=0;k<3;k++)samples[k].push(faceBefore[j+k]);
    }
    for(let k=0;k<3;k++){
      samples[k].sort((a,b)=>a-b);
      const background=samples[k].length>=5?samples[k][Math.floor(samples[k].length*.6)]:cleaned.skinColor?.[k];
      if(background!==undefined)face[i+k]=Math.round(Math.max(0,Math.min(255,background)));
    }
    // Aold = Anew + Abg*(1-Anew); preserve input coverage, including soft edges.
    const backingAlpha=Math.round((oldAlpha-newAlpha)/(1-newAlpha/255));
    face[i+3]=Math.max(face[i+3],backingAlpha);
    masks.skinMask[p]=1;
    if(!newAlpha)masks.hairMask[p]=0;
    transferred++;
  }
  hair.set(cleaned.pixels);
  return {changed:cleaned.changed,transferred,skinColor:cleaned.skinColor};
}
