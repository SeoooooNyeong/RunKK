import {field} from './fringe-studio-engine.mjs';
import {skinColor} from './skin-edge-matting.mjs';

// Final face-only cleanup: look for narrow vertical/diagonal ridges left by bangs.
// Use original artwork for the transferred hair, never draw synthetic strands.
export function recoverForeheadStrands(face,hair,eyes,source,masks,w,h){
  const n=w*h,snapshot=Buffer.from(face),hairBefore=Buffer.from(hair);
  const bounds=masks.diagnostics.skinBoundaryBounds||masks.diagnostics.face;
  const top=Math.max(0,Math.floor(bounds.top));
  const bottom=Math.min(h-1,Math.floor(Math.min(...masks.diagnostics.eyes.map(e=>e.top))));
  const left=Math.max(0,Math.floor(bounds.left)),right=Math.min(w-1,Math.ceil(bounds.right));
  const seeds=new Uint8Array(n),candidate=new Uint8Array(n),background=new Uint8ClampedArray(n*3),fractions=new Float32Array(n);
  for(let p=0;p<n;p++){
    const i=p*4;
    if(hairBefore[i+3]>235&&!masks.browMask[p]&&(hairBefore[i]+hairBefore[i+1]+hairBefore[i+2])/3<masks.diagnostics.threshold*.85)seeds[p]=1;
  }
  const nearest=field(seeds,w,h,true);
  const luma=(buffer,i)=>(buffer[i]+buffer[i+1]+buffer[i+2])/3;
  const usable=q=>q>=0&&q<n&&snapshot[q*4+3]>230&&!masks.eyeMask[q]&&!masks.browMask[q]&&skinColor(snapshot[q*4],snapshot[q*4+1],snapshot[q*4+2]);
  for(let y=top;y<=bottom;y++)for(let x=left;x<=right;x++){
    const p=y*w+x,i=p*4;
    if(snapshot[i+3]<200||!source[i+3]||eyes[i+3]||masks.browMask[p]||masks.eyelidCreaseMask?.[p]||nearest.nearest[p]<0||nearest.d[p]>80)continue;
    let best=0,bg=null;
    for(let d=3;d<=14;d++){
      if(x-d<left||x+d>right)continue;
      const a=p-d,b=p+d;if(!usable(a)||!usable(b))continue;
      const depth=Math.min(luma(snapshot,a*4),luma(snapshot,b*4))-luma(snapshot,i);
      if(depth>best){best=depth;bg=[0,1,2].map(k=>(snapshot[a*4+k]+snapshot[b*4+k])/2);}
    }
    if(best<4||!bg)continue;
    const f=nearest.nearest[p]*4;
    let dot=0,den=0;
    for(let k=0;k<3;k++){const v=hairBefore[f+k]-bg[k];dot+=(source[i+k]-bg[k])*v;den+=v*v;}
    if(den<1600)continue;
    const t=Math.max(0,Math.min(1,dot/den));let residual=0;
    for(let k=0;k<3;k++)residual+=(source[i+k]-(t*hairBefore[f+k]+(1-t)*bg[k]))**2;
    if(t<.06||residual>25*25)continue;
    candidate[p]=1;fractions[p]=t;
    for(let k=0;k<3;k++)background[p*3+k]=Math.round(bg[k]);
  }
  const seen=new Uint8Array(n),queue=new Int32Array(n);let changed=0;
  for(let start=0;start<n;start++){
    if(!candidate[start]||seen[start])continue;
    let read=0,size=1,minX=w,maxX=0,minY=h,maxY=0;queue[0]=start;seen[start]=1;
    while(read<size){const p=queue[read++],x=p%w,y=Math.floor(p/w);
      minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
      for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){
        const xx=x+dx,yy=y+dy,q=yy*w+xx;
        if(xx<0||xx>=w||yy<0||yy>=h||seen[q]||!candidate[q])continue;
        seen[q]=1;queue[size++]=q;
      }
    }
    if(size<12||maxY-minY<6||maxY-minY<(maxX-minX)*.35)continue;
    for(let j=0;j<size;j++){
      const p=queue[j],i=p*4,f=nearest.nearest[p]*4;
      for(let k=0;k<3;k++){hair[i+k]=hairBefore[f+k];face[i+k]=background[p*3+k];}
      hair[i+3]=Math.max(hairBefore[i+3],Math.round(source[i+3]*fractions[p]));
      // Keep existing skin coverage so thin forehead gaps cannot reopen.
      face[i+3]=snapshot[i+3];masks.hairMask[p]=1;masks.skinMask[p]=1;changed++;
    }
  }
  return changed;
}
