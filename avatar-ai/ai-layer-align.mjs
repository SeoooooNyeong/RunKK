import sharp from 'sharp';

const SIDE=256;
const clamp=(n,lo,hi)=>Math.max(lo,Math.min(hi,n));
async function pixels(png){return sharp(png).resize(SIDE,SIDE).ensureAlpha().raw().toBuffer();}

function samples(data,part,direction){
  const out=[];
  for(let y=5;y<SIDE-5;y+=2)for(let x=5;x<SIDE-5;x+=2){
    const i=(y*SIDE+x)*4,a=data[i+3],r=data[i],g=data[i+1],b=data[i+2];
    if(a<210)continue;
    if(part==='face'){
      // Mouth, nose, lower cheek contours: the AI must match these source details.
      if(direction==='right'?(x<SIDE*.24||x>SIDE*.91||y<SIDE*.30||y>SIDE*.84):(x<SIDE*.35||x>SIDE*.65||y<SIDE*.52||y>SIDE*.78))continue;
      if(r>225&&g>185&&b>160)continue;
    }else if(part==='eyes'){
      if(direction==='right'?(x<SIDE*.20||x>SIDE*.92||y<SIDE*.25||y>SIDE*.78):(x<SIDE*.22||x>SIDE*.78||y<SIDE*.28||y>SIDE*.72))continue;
      if(r>215&&g>185&&b>170)continue;
    }else{
      if(!(x<SIDE*.34||x>SIDE*.66||y<SIDE*.37))continue;
      const right=i+4,below=i+SIDE*4;
      if(Math.abs(r-data[right])+Math.abs(g-data[right+1])+Math.abs(b-data[right+2])+Math.abs(r-data[below])+Math.abs(g-data[below+1])+Math.abs(b-data[below+2])<50)continue;
    }
    out.push({x,y,r,g,b});
  }
  const stride=Math.max(1,Math.ceil(out.length/800));
  return out.filter((_,i)=>i%stride===0);
}

function score(source,points,scale,dx,dy){
  let sum=0,count=0;
  for(const p of points){
    const x=Math.round((p.x-SIDE/2)*scale+SIDE/2+dx),y=Math.round((p.y-SIDE/2)*scale+SIDE/2+dy);
    if(x<0||x>=SIDE||y<0||y>=SIDE){sum+=400;continue;}
    const i=(y*SIDE+x)*4;
    if(source[i+3]<180){sum+=400;continue;}
    sum+=(Math.abs(source[i]-p.r)+Math.abs(source[i+1]-p.g)+Math.abs(source[i+2]-p.b))/3;
    count++;
  }
  return count<points.length*.6?Infinity:sum/points.length;
}

async function transform(png,scale,dx,dy){
  const size=Math.round(1024*scale),left=Math.round((1024-size)/2+dx*4),top=Math.round((1024-size)/2+dy*4);
  const cropLeft=Math.max(0,-left),cropTop=Math.max(0,-top);
  const crop={left:cropLeft,top:cropTop,width:Math.min(size-cropLeft,1024-Math.max(0,left)),height:Math.min(size-cropTop,1024-Math.max(0,top))};
  if(crop.width<=0||crop.height<=0)throw new Error('Layer transform exceeds canvas');
  const resized=await sharp(png).resize(size,size).extract(crop).png().toBuffer();
  return sharp({create:{width:1024,height:1024,channels:4,background:{r:0,g:0,b:0,alpha:0}}})
    .composite([{input:resized,left:Math.max(0,left),top:Math.max(0,top)}]).png().toBuffer();
}

async function preserveSourcePixels(master,layer,part){
  const source=(await sharp(master).ensureAlpha().raw().toBuffer()).subarray(0);
  const generated=await sharp(layer).ensureAlpha().raw().toBuffer();
  const result=Buffer.alloc(generated.length);
  for(let i=0;i<generated.length;i+=4){
    const sourceAlpha=source[i+3],maskAlpha=generated[i+3];
    if(sourceAlpha<100||maskAlpha<105)continue;
    const difference=(Math.abs(source[i]-generated[i])+Math.abs(source[i+1]-generated[i+1])+Math.abs(source[i+2]-generated[i+2]))/3;
    // An eye/hair mask that landed on skin or background must not copy that area.
    if(difference>(part==='eyes'?85:95))continue;
    result[i]=source[i];result[i+1]=source[i+1];result[i+2]=source[i+2];
    result[i+3]=Math.min(sourceAlpha,Math.round((maskAlpha-105)*255/150));
  }
  return sharp(result,{raw:{width:1024,height:1024,channels:4}}).png().toBuffer();
}

export async function alignAiParts(master,parts,{direction='front'}={}){
  const source=await pixels(master),aligned={},metrics={};
  for(const part of ['face','eyes','hair']){
    const points=samples(await pixels(parts[part]),part,direction);
    if(points.length<30){
      if(part==='face'){
        // A smooth skin base may have no reliable dark pixels to color-match.
        // Its generated PNG already uses the shared source canvas; keep that registration.
        aligned[part]=parts[part];
        metrics[part]={scale:1,offsetX:0,offsetY:0,matchError:null,method:'shared-canvas-no-color-anchor'};
        continue;
      }
      throw Object.assign(new Error(`${part} 파츠에서 원본과 비교할 특징을 찾지 못했어요.`),{status:502,code:'HEAD_LAYER_ALIGNMENT_FAILED'});
    }
    let best={score:Infinity,objective:Infinity,scale:1,dx:0,dy:0};
    // Extraction should already share the source canvas. Large independent
    // transforms can make one layer match by color while moving its features.
    const objective=(error,scale,dx,dy)=>error+Math.abs(scale-1)*80+(Math.abs(dx)+Math.abs(dy))*.4;
    for(const scale of [.94,.97,1,1.03,1.06])for(let dy=-12;dy<=12;dy+=3)for(let dx=-12;dx<=12;dx+=3){
      const value=score(source,points,scale,dx,dy),rank=objective(value,scale,dx,dy);
      if(rank<best.objective)best={score:value,objective:rank,scale,dx,dy};
    }
    for(const scale of [best.scale-.015,best.scale,best.scale+.015])for(let dy=Math.max(-12,best.dy-3);dy<=Math.min(12,best.dy+3);dy++)for(let dx=Math.max(-12,best.dx-3);dx<=Math.min(12,best.dx+3);dx++){
      const value=score(source,points,scale,dx,dy),rank=objective(value,scale,dx,dy);
      if(rank<best.objective)best={score:value,objective:rank,scale,dx,dy};
    }
    // A different drawing cannot be fixed by shifting it. Reject it before display.
    if(!Number.isFinite(best.score)||best.score>(direction==='right'?125:100))throw Object.assign(new Error(`${part} 파츠가 원본과 달라 위치를 맞출 수 없어요.`),{status:502,code:'HEAD_LAYER_ALIGNMENT_FAILED'});
    const shifted=await transform(parts[part],clamp(best.scale,.925,1.075),best.dx,best.dy);
    aligned[part]=part==='face'?shifted:await preserveSourcePixels(master,shifted,part);
    metrics[part]={scale:Number(best.scale.toFixed(3)),offsetX:best.dx*4,offsetY:best.dy*4,matchError:Number(best.score.toFixed(1))};
  }
  return {parts:aligned,metrics};
}
