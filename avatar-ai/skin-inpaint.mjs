// Harmonic skin interpolation with fixed, observed skin boundaries.
// Work in floating point until the final write; rounding every relaxation step
// freezes visible bands. Missing hair/transparent neighbors are not black samples.
export function smoothSkinBacking(face, originalSkin, source, masks, initial, w, h) {
  const n=w*h, fill=new Uint8Array(n), owner=new Int32Array(n).fill(-1);
  const queue=new Int32Array(n), index=new Int32Array(n).fill(-1);
  const around=p=>{
    const x=p%w,y=Math.floor(p/w);
    return [x>0?p-1:-1,x<w-1?p+1:-1,y>0?p-w:-1,y<h-1?p+w:-1];
  };
  for(const p of initial)fill[p]=1;
  // Cover the whole removed eye, including centers farther than 48 px from skin.
  for(let p=0;p<n;p++)if(source[p*4+3]&&(masks.eyeMask[p]||masks.browMask[p]))fill[p]=1;
  const eyeEdge=new Uint8Array(n);
  for(let p=0;p<n;p++)if(masks.eyeMask[p]){
    const x=p%w,y=Math.floor(p/w);
    for(let dy=-5;dy<=5;dy++)for(let dx=-5;dx<=5;dx++){
      const xx=x+dx,yy=y+dy;if(xx<0||xx>=w||yy<0||yy>=h||dx*dx+dy*dy>25)continue;
      const q=yy*w+xx;
      if(!masks.hairMask[q]&&originalSkin[q*4+3]>220)eyeEdge[q]=1;
    }
  }
  for(let p=0;p<n;p++)if(eyeEdge[p])fill[p]=1;
  const known=p=>p>=0&&!fill[p]&&!masks.hairMask[p]&&!masks.eyeMask[p]&&!masks.browMask[p]&&
    originalSkin[p*4+3]>220&&(originalSkin[p*4]+originalSkin[p*4+1]+originalSkin[p*4+2])/3>masks.diagnostics.threshold;
  let read=0,write=0;
  for(let p=0;p<n;p++)if(fill[p]){
    const boundary=around(p).find(known);
    if(boundary!==undefined){owner[p]=boundary;queue[write++]=p;}
  }
  // Seed only components that have genuine skin on their boundary.
  while(read<write){const p=queue[read++];
    for(const q of around(p))if(q>=0&&fill[q]&&owner[q]<0){owner[q]=owner[p];queue[write++]=q;}
  }
  const count=write;if(!count)return 0;
  for(let j=0;j<count;j++)index[queue[j]]=j;
  const links=new Int32Array(count*4).fill(-1),diagonal=new Float64Array(count),rhs=new Float64Array(count*3);
  for(let j=0;j<count;j++){
    let slot=0;
    for(const q of around(queue[j])){
      if(q<0)continue;
      if(index[q]>=0){links[j*4+slot++]=index[q];diagonal[j]++;}
      else if(known(q)){
        diagonal[j]++;
        for(let k=0;k<3;k++)rhs[j*3+k]+=originalSkin[q*4+k];
      }
    }
  }
  const multiply=(v,out)=>{
    for(let j=0;j<count;j++){
      let value=diagonal[j]*v[j];
      for(let slot=0;slot<4;slot++){const q=links[j*4+slot];if(q>=0)value-=v[q];}
      out[j]=value;
    }
  };
  for(let k=0;k<3;k++){
    const x=new Float64Array(count),r=new Float64Array(count),direction=new Float64Array(count),ap=new Float64Array(count);
    for(let j=0;j<count;j++)x[j]=originalSkin[owner[queue[j]]*4+k];
    multiply(x,ap);let rz=0;
    for(let j=0;j<count;j++){r[j]=rhs[j*3+k]-ap[j];direction[j]=r[j]/diagonal[j];rz+=r[j]*direction[j];}
    // Preconditioned conjugate gradient propagates smooth shading across the
    // entire hole, instead of stopping after a few local averaging passes.
    for(let iteration=0;iteration<600&&rz>1e-12;iteration++){
      multiply(direction,ap);let denominator=0;
      for(let j=0;j<count;j++)denominator+=direction[j]*ap[j];
      if(denominator<=1e-15)break;
      const step=rz/denominator;let next=0,error=0;
      for(let j=0;j<count;j++){x[j]+=step*direction[j];r[j]-=step*ap[j];next+=r[j]*r[j]/diagonal[j];error+=r[j]*r[j];}
      if(error/count<.0001)break;
      const beta=next/rz;
      for(let j=0;j<count;j++)direction[j]=r[j]/diagonal[j]+beta*direction[j];
      rz=next;
    }
    for(let j=0;j<count;j++)face[queue[j]*4+k]=Math.round(Math.max(0,Math.min(255,x[j])));
  }
  for(let j=0;j<count;j++)face[queue[j]*4+3]=source[queue[j]*4+3];
  return count;
}
