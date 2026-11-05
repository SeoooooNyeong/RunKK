// Segment stylized sprites using their actual pixels. Vision coordinates are
// approximate and must never be used to move artwork or to slice through eyes.
import {refineEyes,refineProfileEye} from './eye-mask.mjs';
const W=1024,N=W*W;
function components(mask){
  const seen=new Uint8Array(N),queue=new Int32Array(N),out=[];
  for(let start=0;start<N;start++){
    if(!mask[start]||seen[start])continue;
    let read=0,write=1,left=W,top=W,right=0,bottom=0;queue[0]=start;seen[start]=1;
    while(read<write){const p=queue[read++],x=p%W,y=(p/W)|0;left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
      for(const q of [x>0?p-1:-1,x<W-1?p+1:-1,y>0?p-W:-1,y<W-1?p+W:-1])if(q>=0&&mask[q]&&!seen[q]){seen[q]=1;queue[write++]=q;}
    }
    if(write>8)out.push({pixels:queue.slice(0,write),count:write,left,top,right,bottom,width:right-left+1,height:bottom-top+1,cx:(left+right)/2,cy:(top+bottom)/2});
  }
  return out;
}
function dilate(mask,radius){
  const out=new Uint8Array(N);
  for(let p=0;p<N;p++)if(mask[p]){const x=p%W,y=(p/W)|0;for(let dy=-radius;dy<=radius;dy++)for(let dx=-radius;dx<=radius;dx++){const xx=x+dx,yy=y+dy;if(xx>=0&&xx<W&&yy>=0&&yy<W)out[yy*W+xx]=1;}}
  return out;
}
function fillHoles(mask){
  const inverse=new Uint8Array(N);for(let p=0;p<N;p++)inverse[p]=1-mask[p];
  const out=Uint8Array.from(mask);
  for(const c of components(inverse))if(c.left>0&&c.top>0&&c.right<W-1&&c.bottom<W-1)for(const p of c.pixels)out[p]=1;
  return out;
}
export function pixelRegions(data,{profile=false}={}){
  const warm=new Uint8Array(N),hist=new Uint32Array(256);
  for(let p=0;p<N;p++){const i=p*4,r=data[i],g=data[i+1],b=data[i+2],v=Math.round((r+g+b)/3);if(data[i+3]>100&&r>g+3&&g>b-2&&r>b+10){warm[p]=1;hist[v]++;}}
  let total=0,sum=0;for(let i=0;i<256;i++){total+=hist[i];sum+=i*hist[i];}
  let below=0,partial=0,best=0,threshold=120;
  for(let i=0;i<255;i++){below+=hist[i];partial+=i*hist[i];if(!below||below===total)continue;const variance=below*(total-below)*(partial/below-(sum-partial)/(total-below))**2;if(variance>best){best=variance;threshold=i;}}
  const skin=new Uint8Array(N);
  for(let p=0;p<N;p++){const i=p*4;if(warm[p]&&(data[i]+data[i+1]+data[i+2])/3>threshold)skin[p]=1;}
  const candidates=components(skin).filter(c=>c.count>200&&c.cy<W*.95).sort((a,b)=>b.count-a.count);
  const face=candidates.find(c=>c.cx>W*.22&&c.cx<W*(profile?.94:.78)&&c.cy<W*.9);if(!face||face.count<12000)throw new Error('얼굴 피부 영역을 찾지 못했어요. 기존 아바타를 유지해요.');
  const faceMask=new Uint8Array(N);for(const c of candidates){if(c.count>face.count*.008&&c.cx>face.left-face.width*.3&&c.cx<face.right+face.width*.3&&c.cy>face.top&&c.cy<face.bottom+face.height*.4)for(const p of c.pixels)faceMask[p]=1;}
  const filled=fillHoles(dilate(faceMask,3));
  // White sclera can connect to an outer highlight, leaving an eye hole open.
  // Bridge only between actual skin pixels on the same face scanline.
  for(let y=face.top;y<=face.bottom;y++){let left=W,right=-1;for(let x=face.left;x<=face.right;x++)if(faceMask[y*W+x]){left=Math.min(left,x);right=Math.max(right,x);}for(let x=left;x<=right;x++)filled[y*W+x]=1;}
  // Dark eye/eyebrow islands inside the skin silhouette; the hair boundary is
  // excluded by a real skin margin. Nose and mouth stay below the eye band.
  const dark=new Uint8Array(N),rows=[];
  for(let y=face.top;y<=face.bottom;y++){let left=W,right=-1;for(let x=face.left;x<=face.right;x++)if(filled[y*W+x]){left=Math.min(left,x);right=Math.max(right,x);}rows[y]={left,right};}
  for(let y=face.top;y<face.bottom;y++){const row=rows[y];if(!row)continue;
    for(let x=Math.max(row.left+Math.round(face.width*.06),0);x<Math.min(W,row.right-face.width*.06);x++){
      const p=y*W+x,i=p*4,relative=(y-face.top)/face.height;
      if(filled[p]&&relative>(profile?.12:.28)&&relative<.83&&data[i+3]>100&&(data[i]+data[i+1]+data[i+2])/3<threshold*.88)dark[p]=1;
    }
  }
  const features=components(dark).filter(c=>c.width>face.width*.035&&c.width<face.width*(profile?.75:.48)&&c.height<face.height*.60&&c.count>40);
  const eyeFor=side=>features.filter(c=>(side==='left'?c.cx<face.cx:c.cx>face.cx)&&c.width>face.width*.08&&c.height>face.height*.06).sort((a,b)=>b.count-a.count)[0];
  const near=profile?features.filter(c=>c.cx>face.left+face.width*.35&&c.width>face.width*.08&&c.height>face.height*.06).sort((a,b)=>b.count-a.count)[0]:null;
  const left=profile?near:eyeFor('left'),right=profile?near:eyeFor('right');
  if(!left||!right||Math.abs(left.cy-right.cy)>face.height*.12||Math.max(left.count,right.count)/Math.min(left.count,right.count)>3)throw Object.assign(new Error('양쪽 눈 영역을 정확히 찾지 못했어요. 기존 아바타를 유지해요.'),{diagnostics:{threshold,face:{...face,pixels:undefined},features:features.map(c=>({...c,pixels:undefined}))},status:502});
  const refined=profile?refineProfileEye(data,near,threshold,features):refineEyes(data,[left,right],threshold);
  // Restore hair strands crossing the forehead. A filled skin envelope must
  // never swallow a fringe just because skin is visible on both sides of it.
  const hairMask=Uint8Array.from(filled,v=>v?0:1),queue=new Int32Array(N);
  const foreheadBottom=Math.round(Math.max(...refined.details.map(d=>d.whiteBox.top))-Math.max(left.height,right.height)*.05);
  let read=0,write=0,recoveredHairPixels=0;
  for(let p=0;p<N;p++)if(hairMask[p]&&data[p*4+3]>8&&(p/W|0)<foreheadBottom)queue[write++]=p;
  while(read<write){const p=queue[read++],x=p%W,y=(p/W)|0;
    for(const q of [x>0?p-1:-1,x<W-1?p+1:-1,y>0?p-W:-1,y<foreheadBottom-1?p+W:-1]){
      if(q<0||hairMask[q]||refined.eyes[q]||refined.brows[q]||data[q*4+3]<8)continue;
      const i=q*4;if((data[i]+data[i+1]+data[i+2])/3>threshold*1.25)continue;
      hairMask[q]=1;queue[write++]=q;recoveredHairPixels++;
    }
  }
  const holes=Uint8Array.from(hairMask,v=>v?0:1);
  for(const c of components(holes))if(c.count<Math.max(left.height,right.height)**2*.2&&c.bottom<foreheadBottom)for(const p of c.pixels)if(!refined.eyes[p]&&!refined.brows[p])hairMask[p]=1;
  if(profile){
    // Sideburns can lie inside the skin scanline envelope. Recover thick ink
    // connected to the hair, but keep thin ear/jaw outlines in the face layer.
    const solid=new Uint8Array(N),recovered=new Uint8Array(N);
    const darkAt=p=>p>=0&&p<N&&data[p*4+3]>8&&(data[p*4]+data[p*4+1]+data[p*4+2])/3<threshold*1.18;
    const limitY=Math.min(W-5,Math.round(face.bottom-face.height*.18)),limitX=Math.round(near.left-6);
    for(let y=Math.max(5,foreheadBottom);y<limitY;y++)for(let x=Math.max(5,face.left);x<limitX;x++){
      const p=y*W+x;if(!darkAt(p)||refined.eyes[p])continue;let support=0;
      for(let dy=-4;dy<=4;dy+=2)for(let dx=-4;dx<=4;dx+=2)if(darkAt(p+dy*W+dx))support++;
      if(support>=16)solid[p]=1;
    }
    read=0;write=0;for(let p=0;p<N;p++)if(hairMask[p]&&data[p*4+3])queue[write++]=p;
    while(read<write){const p=queue[read++],x=p%W,y=(p/W)|0;for(const q of [x>0?p-1:-1,x<W-1?p+1:-1,y>0?p-W:-1,y<W-1?p+W:-1])if(q>=0&&solid[q]&&!hairMask[q]){hairMask[q]=recovered[q]=1;queue[write++]=q;}}
    const edges=dilate(recovered,2);for(let p=0;p<N;p++)if(edges[p]&&darkAt(p)&&!refined.eyes[p])hairMask[p]=1;
  }
  // Anti-aliased jaw/neck outlines belong to the face. They can lie just
  // outside the bright-skin mask; do not leave a ghost face in the hair PNG.
  const faceEdgeZone=dilate(filled,4);
  let removedFaceEdgePixels=0;
  for(let y=profile?foreheadBottom:Math.ceil(Math.max(left.bottom,right.bottom)+Math.max(left.height,right.height)*.05);y<W;y++)for(let x=2;x<W-2;x++){
    const p=y*W+x;if(!hairMask[p]||!faceEdgeZone[p]||refined.eyes[p]||refined.brows[p]||!data[p*4+3])continue;
    const open=[p-2,p+2,p-W*2,p+W*2].some(q=>q<0||q>=N||data[q*4+3]<8);
    if(open){hairMask[p]=0;removedFaceEdgePixels++;}
  }
  for(let p=0;p<N;p++)if(refined.brows[p])hairMask[p]=1;
  const center=(left.cx+right.cx)/2;
  const nose={x:Math.round(center/W*1000),y:Math.round((Math.max(left.bottom,right.bottom)+face.height*.025)/W*1000)};
  const mouth={x:nose.x,y:Math.round((face.bottom-face.height*.12)/W*1000)};
  let neckY=face.bottom;for(let y=face.bottom;y<W*.95;y++){let width=0;for(let x=Math.round(center-face.width*.13);x<=center+face.width*.13;x++)if(faceMask[y*W+x])width++;if(width>face.width*.06)neckY=y;}
  return {skinMask:filled,hairMask,browMask:refined.brows,eyeMask:refined.eyes,requiredEyePixels:refined.required,requiredBrowPixels:refined.requiredBrows,landmarks:{nose,mouth,neck:{x:nose.x,y:Math.round((neckY-3)/W*1000)},leftEye:{x:Math.round(left.cx/W*1000),y:Math.round(left.cy/W*1000)},rightEye:{x:Math.round(right.cx/W*1000),y:Math.round(right.cy/W*1000)}},diagnostics:{threshold,recoveredHairPixels,removedFaceEdgePixels,face:{left:face.left,top:face.top,right:face.right,bottom:face.bottom},eyes:[{...left,pixels:undefined},{...right,pixels:undefined}],refinedEyes:refined.details}};
}
