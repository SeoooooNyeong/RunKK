import {skinColor} from './skin-edge-matting.mjs';
import {lab,palette,field} from './fringe-studio-engine.mjs';

function neighbors(p,w,h){const x=p%w,y=Math.floor(p/w);return [x?p-1:-1,x<w-1?p+1:-1,y?p-w:-1,y<h-1?p+w:-1];}
function nearest(w,h,seeds,limit){
  const n=w*h,distance=new Uint16Array(n).fill(65535),owner=new Int32Array(n).fill(-1),queue=new Int32Array(n);
  let read=0,write=0;
  for(let p=0;p<n;p++)if(seeds[p]){owner[p]=p;distance[p]=0;queue[write++]=p;}
  while(read<write){const p=queue[read++];if(distance[p]>=limit)continue;
    for(const q of neighbors(p,w,h))if(q>=0&&owner[q]<0){owner[q]=owner[p];distance[q]=distance[p]+1;queue[write++]=q;}
  }
  return {owner,distance};
}

export function reassignFaceHair(parts,source,masks,w,h){
  const [face,eyes,hair]=parts,n=w*h,faceBefore=Buffer.from(face),hairBefore=Buffer.from(hair);
  const faceBounds=masks.diagnostics.face,bounds=masks.diagnostics.skinBoundaryBounds||faceBounds;
  const faceWidth=faceBounds.right-faceBounds.left+1;
  const eyeTop=Math.min(...masks.diagnostics.eyes.map(e=>e.top));
  const eyeBottom=Math.max(...masks.diagnostics.eyes.map(e=>e.bottom));
  const skinSeeds=new Uint8Array(n),hairSeeds=new Uint8Array(n);
  const features=new Float32Array(n*3),skinCore=[],hairCore=[];
  const transparent=new Uint8Array(n);
  for(let p=0;p<n;p++)transparent[p]=hairBefore[p*4+3]<8?1:0;
  const hairDepth=field(transparent,w,h).d;
  const protectedPixel=p=>masks.eyeMask[p]||masks.browMask[p]||masks.eyelidCreaseMask?.[p]||eyes[p*4+3]>0;
  for(let p=0;p<n;p++){
    const i=p*4;
    if(source[i+3])lab(source[i],source[i+1],source[i+2],features,p*3);
    if(protectedPixel(p)||source[i+3]<220)continue;
    const luma=(source[i]+source[i+1]+source[i+2])/3;
    const isSkin=skinColor(source[i],source[i+1],source[i+2]);
    if(faceBefore[i+3]>240&&isSkin&&luma>masks.diagnostics.threshold*1.08){skinSeeds[p]=1;skinCore.push(p);}
    const x=p%w,y=Math.floor(p/w);
    const outsideFace=x<bounds.left||x>bounds.right||y<bounds.top-12||y>bounds.bottom;
    if(hairBefore[i+3]>240 && (luma<masks.diagnostics.threshold*.9 || (outsideFace&&hairDepth[p]>=9))){
      hairSeeds[p]=1;if(hairDepth[p]>=3)hairCore.push(p);
    }
  }
  if(skinCore.length<16||hairCore.length<16)return {faceToHairPixels:0,hairToFacePixels:0};
  const skinPalette=palette(features,skinCore),hairPalette=palette(features,hairCore);
  if(!skinPalette.length||!hairPalette.length)return {faceToHairPixels:0,hairToFacePixels:0};
  const skin=nearest(w,h,skinSeeds,96),hairReference=nearest(w,h,hairSeeds,64);
  const toHair=new Uint8Array(n),toFace=new Uint8Array(n),mixedHair=new Uint8Array(n);
  const hairFraction=new Float32Array(n).fill(-1),confidentHair=new Uint8Array(n);
  const colorDistance=(p,colors)=>{
    const i=p*3;let best=Infinity;
    for(const c of colors)best=Math.min(best,Math.hypot(features[i]-c[0],features[i+1]-c[1],features[i+2]-c[2]));
    return best;
  };
  for(let p=0;p<n;p++){
    const i=p*4,x=p%w,y=Math.floor(p/w);
    if(!source[i+3]||protectedPixel(p)||skin.owner[p]<0||hairReference.owner[p]<0)continue;
    if(x<bounds.left-8||x>bounds.right+8||y<bounds.top-8||y>bounds.bottom)continue;
    // Keep nose/mouth, cheek detail, ears and neck out of the hair-transfer search.
    const hairZone=y<eyeTop || (y<=eyeBottom && (x<faceBounds.left+faceWidth*.2||x>faceBounds.right-faceWidth*.2));
    const skinError=colorDistance(p,skinPalette),hairError=colorDistance(p,hairPalette);
    if(faceBefore[i+3] && hairZone){
      if(hairError<13 && hairError+5<skinError){toHair[p]=1;if(hairError<8&&skinError>18)confidentHair[p]=1;}
      // Residual antialiased strands may resemble neither pure hair nor pure skin.
      // Compare their observed color to a mixture of nearby real hair and skin.
      const f=hairReference.owner[p]*4,b=skin.owner[p]*4;
      let numerator=0,denominator=0;
      for(let k=0;k<3;k++){const v=source[f+k]-source[b+k];numerator+=(source[i+k]-source[b+k])*v;denominator+=v*v;}
      if(denominator>1600){
        const t=Math.max(0,Math.min(1,numerator/denominator));let residual=0;
        for(let k=0;k<3;k++)residual+=(source[i+k]-(t*source[f+k]+(1-t)*source[b+k]))**2;
        if(t>=.12&&t<.98&&residual<18*18&&skinError>3){mixedHair[p]=1;hairFraction[p]=t;}
      }
    }
    if(hairBefore[i+3] && skinColor(source[i],source[i+1],source[i+2]) &&
       skinError<13 && skinError+5<hairError)toFace[p]=1;
  }
  // Grow only from nearby, already identified regions. Detached color lookalikes
  // deep inside the hair are not enough to reclassify a highlight as facial skin.
  const connected=(candidate,reference,strong=null)=>{
    const selected=new Uint8Array(n),queue=new Int32Array(n);let read=0,write=0;
    for(let p=0;p<n;p++)if(candidate[p]&&(reference.distance[p]<=16||(strong?.[p]&&reference.distance[p]<=48))){selected[p]=1;queue[write++]=p;}
    while(read<write){const p=queue[read++],x=p%w,y=Math.floor(p/w);
      for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){
        const xx=x+dx,yy=y+dy,q=yy*w+xx;
        if(xx>=0&&xx<w&&yy>=0&&yy<h&&candidate[q]&&!selected[q]){selected[q]=1;queue[write++]=q;}
      }
    }
    return selected;
  };
  const hairMoves=connected(toHair,hairReference,confidentHair),faceMoves=connected(toFace,skin);
  const strandDistance=nearest(w,h,hairMoves,5).distance;
  // Extend only a narrow contour around confirmed strands; do not recolor broad shadows.
  for(let p=0;p<n;p++)if(mixedHair[p]&&(strandDistance[p]<=4||hairReference.distance[p]<=3))hairMoves[p]=1;
  let faceToHairPixels=0,hairToFacePixels=0;
  // Both decisions use immutable originals: transferred pixels cannot bounce back.
  for(let p=0;p<n;p++){
    const i=p*4;
    if(hairMoves[p]){
      if(hairFraction[p]>=0){
        const f=hairReference.owner[p]*4,b=skin.owner[p]*4,t=hairFraction[p];
        const hairAlpha=Math.round(faceBefore[i+3]*t);
        for(let k=0;k<3;k++){hair[i+k]=source[f+k];face[i+k]=source[b+k];}
        hair[i+3]=hairAlpha;
        face[i+3]=Math.round((faceBefore[i+3]-hairAlpha)/(1-hairAlpha/255));
        masks.skinMask[p]=1;
      }else{
        faceBefore.copy(hair,i,i,i+4);face.fill(0,i,i+4);
      }
      masks.hairMask[p]=1;faceToHairPixels++;
    }else if(faceMoves[p]){
      hairBefore.copy(face,i,i,i+4);hair.fill(0,i,i+4);
      masks.hairMask[p]=0;masks.skinMask[p]=1;hairToFacePixels++;
    }
  }
  return {faceToHairPixels,hairToFacePixels};
}
