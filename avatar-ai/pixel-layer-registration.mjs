import sharp from 'sharp';

// Register an extracted part against its source pixels, without personal or
// template landmarks. Only confidently improved, bounded matches are applied.
export async function registerLayerPixels(master,png){
  const unchanged={png,scale:1,scaleX:1,scaleY:1,offsetX:0,offsetY:0,used:false};
  const size=256;
  const raw=async input=>sharp(input).resize(size,size,{fit:'fill'}).ensureAlpha().raw().toBuffer();
  const source=await raw(master),layer=await raw(png),samples=[];
  for(let y=2;y<size-2;y+=2)for(let x=2;x<size-2;x+=2){
    const i=(y*size+x)*4;if(layer[i+3]<245)continue;
    const gx=Math.abs(layer[i+8]-layer[i-8])+Math.abs(layer[i+9]-layer[i-7])+Math.abs(layer[i+10]-layer[i-6]);
    const gy=Math.abs(layer[i+size*8]-layer[i-size*8])+Math.abs(layer[i+size*8+1]-layer[i-size*8+1])+Math.abs(layer[i+size*8+2]-layer[i-size*8+2]);
    samples.push({x:x-128,y:y-128,r:layer[i],g:layer[i+1],b:layer[i+2],w:1+Math.min(5,(gx+gy)/45)});
  }
  if(samples.length<40)return unchanged;
  function loss(p){
    const [sx,sy,tx,ty]=p;
    if(sx<.65||sx>1.35||sy<.65||sy>1.35||sx/sy<.75||sx/sy>1.333||Math.abs(tx)>40||Math.abs(ty)>48)return 1e12;
    let sum=0,weights=0;
    for(const a of samples){
      const x=Math.round(a.x*sx+128+tx),y=Math.round(a.y*sy+128+ty);let error=765;
      if(x>=0&&x<size&&y>=0&&y<size){const i=(y*size+x)*4;error=Math.abs(a.r-source[i])+Math.abs(a.g-source[i+1])+Math.abs(a.b-source[i+2]);if(source[i+3]<200)error+=200;}
      sum+=a.w*error;weights+=a.w;
    }
    return sum/weights;
  }
  let best={p:[1,1,0,0],score:loss([1,1,0,0])};
  const initial=best.score;
  if(initial<4)return unchanged;
  for(const sx of [.9,1,1.1])for(const sy of [.7,.85,1,1.15])for(const ty of [-20,0,20]){
    let p=[sx,sy,0,ty],score=loss(p);
    for(const step of [[.08,.08,8,8],[.04,.04,4,4],[.02,.02,2,2],[.01,.01,1,1],[.004,.004,.4,.4]]){
      for(let iter=0;iter<18;iter++){let changed=false;
        for(let k=0;k<4;k++)for(const sign of [-1,1]){const q=p.slice();q[k]+=step[k]*sign;const v=loss(q);if(v<score){score=v;p=q;changed=true;}}
        if(!changed)break;
      }
    }
    if(score<best.score)best={p,score};
  }
  const pixelRegistration={initialLoss:initial,registeredLoss:best.score,samples:samples.length};
  if(best.score>=initial*.85||best.score>170)return {...unchanged,pixelRegistration};
  const [sx,sy,tx,ty]=best.p;
  const left=Math.round(512*(1-sx)+tx*4),top=Math.round(512*(1-sy)+ty*4);
  const width=Math.round(1024*sx),height=Math.round(1024*sy);
  const resized=await sharp(png).resize(width,height,{fit:'fill'}).png().toBuffer();
  const x=Math.max(0,-left),y=Math.max(0,-top),w=Math.min(width-x,1024-Math.max(0,left)),h=Math.min(height-y,1024-Math.max(0,top));
  if(w<=0||h<=0)return {...unchanged,pixelRegistration};
  const cropped=await sharp(resized).extract({left:x,top:y,width:w,height:h}).png().toBuffer();
  const placed=await sharp({create:{width:1024,height:1024,channels:4,background:{r:0,g:0,b:0,alpha:0}}}).composite([{input:cropped,left:Math.max(0,left),top:Math.max(0,top)}]).png().toBuffer();
  return {png:placed,scale:Math.sqrt(sx*sy),scaleX:sx,scaleY:sy,offsetX:left,offsetY:top,used:true,pixelRegistration};
}
