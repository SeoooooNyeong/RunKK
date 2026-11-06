// Boundary-only HSV/YCrCb selection, followed by C = alpha*F + (1-alpha)*B.
// Buffers are straight-alpha RGBA. Never infer transparency from RGB brightness.
export function skinColor(r,g,b){
  const hi=Math.max(r,g,b),lo=Math.min(r,g,b),d=hi-lo;
  let hue=0;
  if(d){hue=hi===r?60*((g-b)/d):hi===g?60*((b-r)/d+2):60*((r-g)/d+4);if(hue<0)hue+=360;}
  const saturation=hi?255*d/hi:0;
  const cr=128+.5*r-.418688*g-.081312*b,cb=128-.168736*r-.331264*g+.5*b;
  const hsv=hue>=3&&hue<=56&&saturation>=12&&saturation<=190&&hi>=105;
  const ycrcb=cr>=130&&cr<=181&&cb>=75&&cb<=137;
  return (hsv||ycrcb)&&r>g+4&&g>b+1&&r>b+10;
}

function neighbors(p,w,h){const x=p%w,y=Math.floor(p/w);return [x?p-1:-1,x<w-1?p+1:-1,y?p-w:-1,y<h-1?p+w:-1];}
function nearest(w,h,seed,limit){
  const n=w*h,dist=new Uint16Array(n).fill(65535),owner=new Int32Array(n).fill(-1),queue=new Int32Array(n);
  let read=0,write=0;
  for(let p=0;p<n;p++)if(seed(p)){dist[p]=0;owner[p]=p;queue[write++]=p;}
  while(read<write){const p=queue[read++];if(dist[p]>=limit)continue;
    for(const q of neighbors(p,w,h))if(q>=0&&dist[q]===65535){dist[q]=dist[p]+1;owner[q]=owner[p];queue[write++]=q;}
  }
  return {dist,owner};
}

export function matteHairSkin(hair,face,source,masks,w,h){
  const original=Buffer.from(hair),skinSource=Buffer.from(face),n=w*h;
  const edge=nearest(w,h,p=>original[p*4+3]<=8,7).dist;
  const skin=nearest(w,h,p=>{
    const i=p*4;
    return !masks.hairMask[p]&&!masks.eyeMask[p]&&!masks.browMask[p]&&skinSource[i+3]>240&&
      skinColor(skinSource[i],skinSource[i+1],skinSource[i+2]);
  },96);
  const pure=nearest(w,h,p=>{
    const i=p*4;if(original[i+3]<240)return false;
    const luma=(original[i]+original[i+1]+original[i+2])/3;
    const warm=skinColor(original[i],original[i+1],original[i+2]);
    // Sample actual interior hair 3+ pixels from the cutout, excluding skin pockets.
    return (edge[p]>=3 && (!warm||luma<masks.diagnostics.threshold*.85)) || luma<85;
  },64);
  const roi=masks.diagnostics.skinBoundaryBounds||masks.diagnostics.face;
  let adjusted=0;
  for(let p=0;p<n;p++){
    const i=p*4,x=p%w,y=Math.floor(p/w);
    if(!original[i+3]||edge[p]>6||skin.owner[p]<0||pure.owner[p]<0)continue;
    if(x<roi.left-8||x>roi.right+8||y<roi.top-8||y>roi.bottom)continue;
    if(!skinColor(original[i],original[i+1],original[i+2]))continue;
    const s=skin.owner[p]*4,f=pure.owner[p]*4;
    let dot=0,den=0,skinError=0;
    for(let k=0;k<3;k++){const v=original[f+k]-skinSource[s+k];dot+=(original[i+k]-skinSource[s+k])*v;den+=v*v;skinError+=(original[i+k]-skinSource[s+k])**2;}
    if(den<625)continue;
    let alpha=Math.max(0,Math.min(1,dot/den)),residual=0;
    for(let k=0;k<3;k++)residual+=(original[i+k]-(alpha*original[f+k]+(1-alpha)*skinSource[s+k]))**2;
    if(skinError<25*25)alpha=0;
    else if(alpha>.97||residual>30*30)continue;
    for(let k=0;k<3;k++)hair[i+k]=original[f+k];
    hair[i+3]=Math.round(original[i+3]*alpha);
    if(!hair[i+3])hair.fill(0,i,i+4);
    // Keep the removed background component on the face at this exact pixel.
    // This is mandatory even when the old skin mask excluded that forehead row.
    for(let k=0;k<3;k++)face[i+k]=skinSource[s+k];
    face[i+3]=Math.max(face[i+3],source[i+3]);
    adjusted++;
  }
  return adjusted;
}

export function repairForeheadSlits(face,eyes,hair,source,masks,w,h){
  const bounds=masks.diagnostics.skinBoundaryBounds||masks.diagnostics.face;
  const eyeTop=Math.min(...masks.diagnostics.eyes.map(e=>e.top));
  const snapshot=Buffer.from(face),limit=Math.max(4,Math.round(h*.016));
  const left=Math.max(1,Math.ceil(bounds.left)),right=Math.min(w-2,Math.floor(bounds.right));
  const top=Math.max(1,Math.floor(bounds.top)),bottom=Math.min(h-2,Math.ceil(eyeTop));
  const opaque=p=>Math.max(snapshot[p*4+3],eyes[p*4+3],hair[p*4+3])>220;
  const missing=new Uint8Array(w*h);
  for(let x=left;x<=right;x++)for(let y=top;y<=bottom;y++){
    const p=y*w+x;
    if(opaque(p)||Math.max(snapshot[p*4+3],eyes[p*4+3],hair[p*4+3])>24)continue;
    let end=y;while(end<=bottom&&!opaque(end*w+x)&&end-y<=limit)end++;
    if(y<=top||end>bottom||end-y>limit||!opaque((y-1)*w+x)||!opaque(end*w+x))continue;
    for(let yy=y;yy<end;yy++)missing[yy*w+x]=1;
    y=end-1;
  }
  // Only repair a horizontally extended, short alpha slit inside the forehead.
  // Narrow natural gaps between individual hair strands are not filled.
  const clean=nearest(w,h,p=>{
    const i=p*4;return snapshot[i+3]>240&&!masks.eyeMask[p]&&!masks.browMask[p]&&skinColor(snapshot[i],snapshot[i+1],snapshot[i+2]);
  },32);
  let repaired=0;
  for(let y=top;y<=bottom;y++)for(let x=left;x<=right;){
    if(!missing[y*w+x]){x++;continue;}
    const start=x;while(x<=right&&missing[y*w+x])x++;
    if(x-start<12)continue;
    for(let xx=start;xx<x;xx++){
      const p=y*w+xx,i=p*4,s=clean.owner[p]*4;if(s<0)continue;
      for(let k=0;k<3;k++)face[i+k]=snapshot[s+k];
      face[i+3]=255;repaired++;
    }
  }
  return repaired;
}
