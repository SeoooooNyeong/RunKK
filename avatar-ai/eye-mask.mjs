// Local skin contrast + connected contours. All masks use source coordinates.
const W = 1024, N = W * W;
const clamp = v => Math.max(0, Math.min(W - 1, Math.round(v)));
const lum = (data, p) => (data[p * 4] + data[p * 4 + 1] + data[p * 4 + 2]) / 3;

function components(mask, box) {
  const seen = new Uint8Array(N), queue = new Int32Array(N), out = [];
  for (let y = box.top; y <= box.bottom; y++) for (let x = box.left; x <= box.right; x++) {
    const start = y * W + x;
    if (!mask[start] || seen[start]) continue;
    let read = 0, size = 1, left = x, right = x, top = y, bottom = y;
    seen[start] = 1; queue[0] = start;
    while (read < size) {
      const p = queue[read++], xx = p % W, yy = Math.floor(p / W);
      left = Math.min(left, xx); right = Math.max(right, xx);
      top = Math.min(top, yy); bottom = Math.max(bottom, yy);
      for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++) {
        const nx = xx + dx, ny = yy + dy, q = ny * W + nx;
        if (nx < box.left || nx > box.right || ny < box.top || ny > box.bottom || seen[q] || !mask[q]) continue;
        seen[q] = 1; queue[size++] = q;
      }
    }
    out.push({pixels: queue.slice(0, size), count: size, left, right, top, bottom, width: right-left+1, height: bottom-top+1});
  }
  return out;
}

function reserveBrows(data,eyes,brows,lid,box,wb,h,skinLuma) {
  const candidate=new Uint8Array(N),joined=new Uint8Array(N);
  // Group eyebrow hairs before the crease detector sees individual thin strokes.
  for(let x=box.left;x<=box.right;x++){
    const anchor=lid[Math.max(wb.left,Math.min(wb.right,x))];
    if(anchor<0)continue;
    const top=Math.max(box.top,Math.floor(anchor-h*1.05));
    const bottom=Math.min(box.bottom,Math.floor(anchor-h*.32));
    for(let y=top;y<=bottom;y++){
      const p=y*W+x;
      // Reconsider pixels previously captured by the eye component too.
      if(data[p*4+3]<100||lum(data,p)>skinLuma-12)continue;
      candidate[p]=1;
      for(let dy=-2;dy<=2;dy++)for(let dx=-2;dx<=2;dx++){
        const xx=x+dx,yy=y+dy;
        if(xx>=box.left&&xx<=box.right&&yy>=box.top&&yy<=box.bottom)joined[yy*W+xx]=1;
      }
    }
  }
  let count=0;
  for(const c of components(joined,box)){
    if(c.width<(wb.right-wb.left)*.22||c.height>h*.48||c.width<c.height*1.5)continue;
    if(Math.min(c.right,wb.right)-Math.max(c.left,wb.left)<(wb.right-wb.left)*.15)continue;
    for(const p of c.pixels)if(candidate[p]){brows[p]=1;count++;}
  }
  return count;
}

function extractCreases(data, eyes, brows, lid, box, wb, h, crease, background) {
  const weak=new Uint8Array(N), strong=new Uint8Array(N);
  const radius=Math.max(4,Math.min(10,Math.round(h*.07)));
  for(let x=wb.left;x<=wb.right;x++) {
    if(lid[x]<0)continue;
    let top=Math.max(box.top+radius,lid[x]-Math.round(h*.48));
    // A detected eyebrow forms an upper barrier; it must never move to the eye layer.
    for(let y=box.top;y<lid[x]-2;y++)for(let dx=-4;dx<=4;dx++){
      const xx=x+dx;if(xx>=box.left&&xx<=box.right&&brows[y*W+xx])top=Math.max(top,y+4);
    }
    for(let y=top;y<lid[x]-2;y++) {
      const p=y*W+x,i=p*4;
      if(eyes[p]||brows[p]||data[i+3]<100)continue;
      let best=0,bg=null;
      for(let d=3;d<=radius;d++) {
        const a=p-d*W,b=p+d*W;
        if(a<0||b>=N||eyes[a]||eyes[b]||brows[a]||brows[b]||data[a*4+3]<220||data[b*4+3]<220)continue;
        const contrast=Math.min(lum(data,a),lum(data,b))-lum(data,p);
        if(contrast>best){best=contrast;bg=[0,1,2].map(k=>(data[a*4+k]+data[b*4+k])/2);}
      }
      if(best<2.5||!bg)continue;
      weak[p]=1;if(best>=6)strong[p]=1;
      for(let k=0;k<3;k++)background[i+k]=Math.round(bg[k]);
      background[i+3]=255;
    }
  }
  let count=0;
  for(const c of components(weak,box)) {
    // Thin, horizontally extended strokes with strong cores; reject shadows and vertical hair.
    if(c.width<Math.max(5,h*.08)||c.count/c.width>Math.max(7,h*.09)||!c.pixels.some(p=>strong[p]))continue;
    const gaps=c.pixels.map(p=>lid[p%W]-Math.floor(p/W)).sort((a,b)=>a-b);
    if(gaps[Math.floor(gaps.length*.9)]-gaps[Math.floor(gaps.length*.1)]>h*.25)continue;
    for(const p of c.pixels){eyes[p]=1;crease[p]=1;count++;}
  }
  return count;
}

function separateConnectedEye(ink, sclera, eye, box, wb) {
  // A connected dark component may include hair without obscuring the eye.
  // Compete between observed sclera/iris seeds and surrounding hair seeds.
  const cx=(wb.left+wb.right)/2,cy=(wb.top+wb.bottom)/2;
  const rx=Math.max(8,(wb.right-wb.left+1)*.65),ry=Math.max(8,(wb.bottom-wb.top+1)*.7);
  const radius=p=>((p%W-cx)/rx)**2+((Math.floor(p/W)-cy)/ry)**2;
  const eyeSeeds=sclera.slice(),hairSeeds=new Uint8Array(N);
  for(const p of eye.pixels)if(radius(p)<.72)eyeSeeds[p]=1;
  for(let y=box.top;y<=box.bottom;y++)for(let x=box.left;x<=box.right;x++){
    const p=y*W+x;
    if(!ink[p]||eyeSeeds[p])continue;
    if(radius(p)>2.25 || ((x===box.left||x===box.right||y===box.top||y===box.bottom)&&radius(p)>1.1))hairSeeds[p]=1;
  }
  const distance=seeds=>{
    const out=new Int32Array(N).fill(0x3fffffff),queue=new Int32Array(N);
    let read=0,write=0;
    for(let y=box.top;y<=box.bottom;y++)for(let x=box.left;x<=box.right;x++){
      const p=y*W+x;if(ink[p]&&seeds[p]){out[p]=0;queue[write++]=p;}
    }
    while(read<write){const p=queue[read++],x=p%W,y=Math.floor(p/W);
      for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){
        const xx=x+dx,yy=y+dy,q=yy*W+xx;
        if(xx<box.left||xx>box.right||yy<box.top||yy>box.bottom||!ink[q]||out[q]!==0x3fffffff)continue;
        out[q]=out[p]+1;queue[write++]=q;
      }
    }
    return out;
  };
  const fromEye=distance(eyeSeeds),fromHair=distance(hairSeeds),owned=new Uint8Array(N);
  for(let y=box.top;y<=box.bottom;y++)for(let x=box.left;x<=box.right;x++){
    const p=y*W+x;
    if(ink[p] && (eyeSeeds[p] || fromEye[p]<fromHair[p]))owned[p]=1;
  }
  return components(owned,box).filter(c=>c.pixels.some(p=>sclera[p]));
}

function findSclera(data,box,eye,threshold,profile=false){
  // Illustrated sclera may be grey, blue, or warm ivory. Rough dark-component
  // height is not a reliable minimum sclera size (it can include nearby hair).
  const passes=[{floor:165,chroma:26},{floor:145,chroma:40},{floor:120,chroma:55}];
  for(let pass=0;pass<passes.length;pass++){
    const mask=new Uint8Array(N),rule=passes[pass];
    for(let y=box.top;y<=box.bottom;y++)for(let x=box.left;x<=box.right;x++){
      const p=y*W+x,i=p*4,r=data[i],g=data[i+1],b=data[i+2];
      if(data[i+3]<100||Math.min(r,g,b)<rule.floor)continue;
      if(Math.max(r,g,b)-Math.min(r,g,b)>rule.chroma)continue;
      // Do not let the relaxed pass admit the surrounding warm facial skin.
      if(pass>0 && r-g>12 && g-b>7)continue;
      mask[p]=1;
    }
    const candidates=components(mask,box).filter(c=>{
      if(c.count<12||c.height<4||c.width<2||c.width/c.height>8) return false;
      if(c.bottom<eye.top-eye.height*.2||c.top>eye.bottom+eye.height*.2)return false;
      const sideReach=profile?Math.max(eye.width*.3,eye.height*.95):eye.width*.3;
      if(c.right<eye.left-sideReach||c.left>eye.right+sideReach)return false;
      if(c.width>(profile?Math.max(eye.width*2.5,eye.height*1.5):eye.width*1.8)||c.height>eye.height*1.8)return false;
      // In profile the iris is narrow and its white reflection can be the only
      // bright component inside the old window. It is not the sclera anchor.
      if(profile&&c.left>eye.left&&c.right<eye.right&&c.top>eye.top&&c.bottom<eye.bottom&&c.count<eye.count*.25)return false;
      if(c.left===box.left||c.right===box.right||c.top===box.top||c.bottom===box.bottom)return false;
      // Require nearby dark iris/liner evidence, not isolated pale skin or hair shine.
      let adjacent=0;
      for(const p of c.pixels){const x=p%W,y=Math.floor(p/W);
        if([x>0?p-1:-1,x<W-1?p+1:-1,y>0?p-W:-1,y<W-1?p+W:-1].some(q=>q>=0&&data[q*4+3]>100&&lum(data,q)<threshold))adjacent++;
      }
      return adjacent>=3;
    });
    const score=c=>c.count/(1+((c.left+c.right)/2-eye.cx)**2/Math.max(64,eye.width**2)+((c.top+c.bottom)/2-eye.cy)**2/Math.max(64,eye.height**2));
    candidates.sort((a,b)=>score(b)-score(a));
    if(!candidates.length)continue;
    const lead=candidates[0],cy=(lead.top+lead.bottom)/2;
    const selected=candidates.filter(c=>c===lead || (c.count>=Math.max(12,lead.count*.08)&&
      Math.abs((c.top+c.bottom)/2-cy)<=Math.max(lead.height,c.height)*.65&&
      Math.max(c.right,lead.right)-Math.min(c.left,lead.left)<(profile?Math.max(eye.width*2.5,eye.height*1.6):eye.width*1.6)));
    return {selected,pass};
  }
  throw Object.assign(new Error('눈 영역을 추출하지 못했어요. 기존 아바타는 유지됩니다.'),{status:502,code:'EYE_REGION_EXTRACTION_FAILED'});
}

function pruneCreaseBranches(eyes,crease,background,rejectedHair,lid,box,wb,h){
  const offsets=new Float32Array(W).fill(-1),radius=Math.max(6,Math.round(h*.12));
  // Keep the lowest coherent fold above each lash, rather than every branch
  // connected to that fold. Hair streaks diverge upward from this path.
  for(let x=wb.left;x<=wb.right;x++){
    if(lid[x]<0)continue;
    const gaps=[];
    for(let y=Math.max(box.top,Math.floor(lid[x]-h*.34));y<lid[x]-2;y++)if(crease[y*W+x])gaps.push(lid[x]-y);
    if(gaps.length){gaps.sort((a,b)=>a-b);offsets[x]=gaps[Math.floor(gaps.length*.25)];}
  }
  const curve=new Float32Array(W).fill(-1);
  for(let x=wb.left;x<=wb.right;x++){
    const nearby=[];
    for(let xx=Math.max(wb.left,x-radius);xx<=Math.min(wb.right,x+radius);xx++)if(offsets[xx]>=0)nearby.push(offsets[xx]);
    if(nearby.length>=4){nearby.sort((a,b)=>a-b);curve[x]=nearby[Math.floor(nearby.length/2)];}
  }
  let count=0;
  for(let y=box.top;y<=box.bottom;y++)for(let x=box.left;x<=box.right;x++){
    const p=y*W+x;if(!crease[p]||!eyes[p]||lid[x]<0)continue;
    const gap=lid[x]-y,expected=curve[x];
    if(expected<0||gap<=2)continue;
    if(gap>expected+Math.max(3,h*.035)){
      eyes[p]=0;crease[p]=0;background[p*4+3]=0;rejectedHair[p]=1;count++;
    }
  }
  return count;
}

function recoverLashContours(data, eyes, brows, crease, rejectedHair, sclera, interior, lid, box, wb, skinLuma) {
  const h=wb.bottom-wb.top+1, reach=Math.max(5,Math.round(h*.42));
  const belt={left:Math.max(box.left,wb.left-reach),right:Math.min(box.right,wb.right+reach),
    top:Math.max(box.top,wb.top-Math.round(h*.30)),bottom:Math.min(box.bottom,wb.bottom+Math.round(h*.08))};
  const distance=new Uint16Array(N).fill(65535),queue=new Int32Array(N);
  let read=0,write=0;
  // Distance to observed white, rather than an ellipse around the rough iris.
  for(let y=wb.top;y<=wb.bottom;y++)for(let x=wb.left;x<=wb.right;x++){
    const p=y*W+x;if(sclera[p]){distance[p]=0;queue[write++]=p;}
  }
  while(read<write){const p=queue[read++],x=p%W,y=Math.floor(p/W);
    if(distance[p]>=reach)continue;
    for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){
      const xx=x+dx,yy=y+dy,q=yy*W+xx;
      if(xx<belt.left||xx>belt.right||yy<belt.top||yy>belt.bottom||distance[q]!==65535)continue;
      distance[q]=distance[p]+1;queue[write++]=q;
    }
  }
  const ink=p=>data[p*4+3]>32&&!brows[p]&&lum(data,p)<skinLuma-22;
  // Above-only reachability leaks down a side lock into attached lash tips.
  // Reserve the strand that continues BOTH above and below the eye instead;
  // a lash protruding from its side must not inherit the whole hair barrier.
  const fromAbove=new Uint8Array(N),start=Math.max(box.top,Math.floor(wb.top-h*.75));
  for(let y=start;y<=belt.bottom;y++)for(let x=belt.left;x<=belt.right;x++){
    const p=y*W+x;
    if(!ink(p)||eyes[p]||crease[p])continue;
    const drift=(y-start)%3===0;
    if(y===start||fromAbove[p-W]||(drift&&x>belt.left&&fromAbove[p-W-1])||(drift&&x<belt.right&&fromAbove[p-W+1]))fromAbove[p]=1;
  }
  const fromBelow=new Uint8Array(N),end=Math.min(box.bottom,Math.ceil(wb.bottom+h*.32));
  for(let y=end;y>=belt.top;y--)for(let x=belt.left;x<=belt.right;x++){
    const p=y*W+x;
    if(!ink(p)||eyes[p]||crease[p])continue;
    const drift=(end-y)%3===0;
    if(y===end||fromBelow[p+W]||(drift&&x>belt.left&&fromBelow[p+W-1])||(drift&&x<belt.right&&fromBelow[p+W+1]))fromBelow[p]=1;
  }
  const accepted=new Uint8Array(N),steps=new Uint16Array(N);
  read=0;write=0;
  for(let y=belt.top;y<=belt.bottom;y++)for(let x=belt.left;x<=belt.right;x++){
    const p=y*W+x;
    // A previously misclassified hair island is not an eye recovery seed.
    if(interior[p]&&eyes[p]&&!crease[p]&&!brows[p]){accepted[p]=1;queue[write++]=p;}
  }
  let restored=0;
  while(read<write){const p=queue[read++],x=p%W,y=Math.floor(p/W);
    if(steps[p]>=reach)continue;
    for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){
      const xx=x+dx,yy=y+dy,q=yy*W+xx;
      if(xx<belt.left||xx>belt.right||yy<belt.top||yy>belt.bottom||accepted[q]||distance[q]>reach||!ink(q))continue;
      const anchor=lid[Math.max(wb.left,Math.min(wb.right,xx))];
      if(anchor>=0&&yy<anchor-Math.max(3,h*.10)&&!crease[q])continue;
      // Keep descending bangs above the globe and continuous side locks out.
      // At the outer corner, allow an attached short branch into the eye mask.
      if(fromAbove[q]&&(fromBelow[q]||yy<wb.top-h*.12))continue;
      accepted[q]=1;steps[q]=steps[p]+1;queue[write++]=q;
      if(!eyes[q]){eyes[q]=1;rejectedHair[q]=0;restored++;}
    }
  }
  return restored;
}

function removeHairFromEyes(eyes,interior,crease,background,rejectedHair,lid,box,wb,h) {
  let moved=0;
  const transfer=p=>{
    eyes[p]=0;crease[p]=0;background[p*4+3]=0;rejectedHair[p]=1;moved++;
  };
  // Bound non-crease branches by the observed lid at each column, not the
  // global top of the eye. A corner cannot grow a rectangular hair patch.
  const curve=new Float32Array(W).fill(-1),radius=Math.max(3,Math.round(h*.04));
  for(let x=wb.left;x<=wb.right;x++){
    const samples=[];
    for(let xx=Math.max(wb.left,x-radius);xx<=Math.min(wb.right,x+radius);xx++)if(lid[xx]>=0)samples.push(lid[xx]);
    if(samples.length){samples.sort((a,b)=>a-b);curve[x]=samples[Math.floor(samples.length/2)];}
  }
  for(let y=box.top;y<=box.bottom;y++)for(let x=box.left;x<=box.right;x++){
    const p=y*W+x;
    if(!eyes[p]||interior[p]||crease[p])continue;
    const anchor=curve[Math.max(wb.left,Math.min(wb.right,x))];
    if(anchor>=0&&y<anchor-Math.max(4,h*.12))transfer(p);
  }
  // Keep detached eyelid creases, but move isolated bangs/eyebrow fragments
  // even when no lid column exists for them (the former pass skipped those).
  for(const c of components(eyes,box)){
    if(c.pixels.some(p=>interior[p]))continue;
    const folds=c.pixels.filter(p=>crease[p]);
    if(!folds.length){for(const p of c.pixels)transfer(p);continue;}
    const nearFold=new Set();
    for(const p of folds){const x=p%W,y=Math.floor(p/W);
      for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){
        const xx=x+dx,yy=y+dy;if(xx>=box.left&&xx<=box.right&&yy>=box.top&&yy<=box.bottom)nearFold.add(yy*W+xx);
      }
    }
    for(const p of c.pixels)if(!nearFold.has(p))transfer(p);
  }
  return moved;
}

function separate(data, rough, threshold, center, profile) {
  const eyes = new Uint8Array(N), brows = new Uint8Array(N), eyeInterior = new Uint8Array(N), details = [];
  const crease=new Uint8Array(N), creaseBackground=new Uint8Array(N*4),rejectedHair=new Uint8Array(N);
  for (const eye of rough) {
    const box = {left: clamp(eye.left-eye.width*.5), right: clamp(eye.right+eye.width*.5),
      top: clamp(eye.top-eye.height*1.15), bottom: clamp(eye.bottom+eye.height*.35)};
    if(profile){
      // A right-facing eye exposes most sclera behind (left of) a narrow iris.
      // Search by eye height so the iris width cannot crop away the white/lashes.
      box.left=clamp(eye.left-Math.max(eye.width*.65,eye.height*1.15));
      box.right=clamp(eye.right+Math.max(eye.width*.5,eye.height*.40));
    }
    // Keep the opposite eye out of the search window.
    if (!profile) { if (eye.cx < center) box.right = Math.min(box.right, Math.floor(center-2));
      else box.left = Math.max(box.left, Math.ceil(center+2)); }
    const white = new Uint8Array(N), skinSamples = [];
    for (let y=box.top; y<=box.bottom; y++) for (let x=box.left; x<=box.right; x++) {
      const p=y*W+x, i=p*4, r=data[i], g=data[i+1], b=data[i+2];
      if (data[i+3]<100) continue;
      if (Math.min(r,g,b)>165 && Math.max(r,g,b)-Math.min(r,g,b)<26) white[p]=1;
      if (r>g+4 && g>b+2 && lum(data,p)>threshold*1.12) skinSamples.push(lum(data,p));
    }
    skinSamples.sort((a,b)=>a-b);
    const skinLuma=skinSamples.length ? skinSamples[Math.floor(skinSamples.length*.6)] : threshold*1.35;
    const scleraResult=findSclera(data,box,eye,threshold,profile),sclera=scleraResult.selected;
    const wb={left:Math.min(...sclera.map(c=>c.left)), right:Math.max(...sclera.map(c=>c.right)),
      top:Math.min(...sclera.map(c=>c.top)), bottom:Math.max(...sclera.map(c=>c.bottom))};
    if(profile){
      // The visible globe is asymmetric: include both iris and observed white.
      wb.left=Math.min(wb.left,eye.left);wb.right=Math.max(wb.right,eye.right);
      wb.top=Math.min(wb.top,eye.top);wb.bottom=Math.max(wb.bottom,eye.bottom);
    }
    const h=wb.bottom-wb.top+1;
    const scleraSeed=new Uint8Array(N);
    for(const c of sclera)for(const p of c.pixels){scleraSeed[p]=1;eyeInterior[p]=1;}
    if(profile)for(const p of eye.pixels){scleraSeed[p]=1;eyeInterior[p]=1;}
    // A skin-relative threshold keeps pale folds; connected components keep their full contour.
    const ink=new Uint8Array(N);
    for(let y=box.top;y<=box.bottom;y++) for(let x=box.left;x<=box.right;x++) {
      const p=y*W+x;
      if(data[p*4+3]>32 && (scleraSeed[p] || lum(data,p)<skinLuma-22)) ink[p]=1;
    }
    const cs=components(ink,box);
    let seeded=cs.filter(c=>c.pixels.some(p=>scleraSeed[p]));
    let main=seeded.sort((a,b)=>b.count-a.count)[0];
    const needsSeparation=main && (main.top<=box.top || main.left<=box.left || main.right>=box.right || main.height>h*1.65);
    if(needsSeparation){
      seeded=separateConnectedEye(ink,scleraSeed,eye,box,wb);
      main=seeded.sort((a,b)=>b.count-a.count)[0];
    }
    if(!main)throw Object.assign(new Error('눈의 흰자 기준점을 찾지 못했어요.'),{status:502});
    for(const c of seeded) if(c===main || (c.top>=main.top-h*.12 && c.bottom<=wb.bottom+h*.18))
      for(const p of c.pixels) eyes[p]=1;
    // Only holes inside the globe may be filled. Skin enclosed by lash spikes stays empty.
    const bounds={left:clamp(main.left-1),right:clamp(main.right+1),top:clamp(main.top-1),bottom:clamp(main.bottom+1)};
    const inv=new Uint8Array(N);
    for(let y=bounds.top;y<=bounds.bottom;y++)for(let x=bounds.left;x<=bounds.right;x++) if(!eyes[y*W+x])inv[y*W+x]=1;
    for(const c of components(inv,bounds)) if(c.left>bounds.left && c.right<bounds.right && c.top>bounds.top && c.bottom<bounds.bottom)
      for(const p of c.pixels){const x=p%W,y=Math.floor(p/W);if(x>=wb.left && x<=wb.right && y>=wb.top && y<=wb.bottom)eyes[p]=1;}
    // Protect the globe from skin-color cleanup. Follow its actual contour per column.
    for(let x=wb.left;x<=wb.right;x++){
      let first=-1,last=-1;
      for(let y=wb.top;y<=wb.bottom;y++)if(eyes[y*W+x]){if(first<0)first=y;last=y;}
      if(first>=0)for(let y=first;y<=last;y++)if(eyes[y*W+x])eyeInterior[y*W+x]=1;
    }
    const lid=new Int32Array(W).fill(-1);
    // Anchor at the globe/lash band, not an accidental eyebrow above it.
    for(let x=main.left;x<=main.right;x++)for(let y=Math.max(main.top,Math.floor(wb.top-h*.22));y<=main.bottom;y++)if(eyes[y*W+x]){lid[x]=y;break;}
    let folds=0, browCount=reserveBrows(data,eyes,brows,lid,box,wb,h,skinLuma);
    for(let y=box.top;y<=box.bottom;y++)for(let x=box.left;x<=box.right;x++)if(brows[y*W+x])eyes[y*W+x]=0;
    for(const c of cs) {
      if(c===main || c.pixels.some(p=>eyes[p]) || c.count<6)continue;
      const overlap=Math.min(c.right,wb.right)-Math.max(c.left,wb.left);
      if(overlap<=0)continue;
      const gaps=[];
      for(const p of c.pixels){const x=p%W,y=Math.floor(p/W);if(lid[x]>=0)gaps.push(lid[x]-y);}
      if(!gaps.length)continue;
      gaps.sort((a,b)=>a-b);const gap=gaps[Math.floor(gaps.length/2)];
      const touches=c.top<=box.top || c.left<=box.left || c.right>=box.right;
      // Eyebrow ownership wins before testing the thin-stroke/crease condition.
      if(c.pixels.some(p=>brows[p]) || (gap>=h*.34 && c.bottom<wb.top-h*.15 && c.width>h*.22)) {
        for(const p of c.pixels)brows[p]=1; browCount+=c.count;
      } else if(!touches && gap>1 && gap<h*.34 && c.height<h*.35 && c.width>h*.10 &&
        c.count/c.width<Math.max(6,h*.07) && gaps[Math.floor(gaps.length*.9)]-gaps[Math.floor(gaps.length*.1)]<h*.25) {
        for(const p of c.pixels){eyes[p]=1;crease[p]=1;} folds+=c.count;
      }
    }
    folds+=extractCreases(data,eyes,brows,lid,box,wb,h,crease,creaseBackground);
    const recoveredLashPixels=recoverLashContours(data,eyes,brows,crease,rejectedHair,scleraSeed,eyeInterior,lid,box,wb,skinLuma);
    // Capture antialiasing only adjacent to an accepted contour, not the entire ROI.
    for(const mask of [eyes,brows]) {
      const snap=mask.slice();
      for(let y=Math.max(1,box.top);y<=Math.min(W-2,box.bottom);y++)for(let x=Math.max(1,box.left);x<=Math.min(W-2,box.right);x++) {
        const p=y*W+x;
        if(mask===eyes && brows[p])continue;
        if(mask===brows && eyeInterior[p])continue;
        if(mask[p] || data[p*4+3]<16 || lum(data,p)>=skinLuma-8)continue;
        if(snap[p-1]||snap[p+1]||snap[p-W]||snap[p+W]){
          mask[p]=1;
          if(mask===eyes && (crease[p-1]||crease[p+1]||crease[p-W]||crease[p+W]))crease[p]=1;
        }
      }
    }
    const reassignedHairPixels=pruneCreaseBranches(eyes,crease,creaseBackground,rejectedHair,lid,box,wb,h);
    // Final ownership pass: detached upper strokes must not survive as eye sprites.
    // Creases stay close to the observed lash contour; upper eyebrow islands move whole.
    let reassignedBrowPixels=0;
    for(const c of components(eyes,box)){
      if(c.pixels.some(p=>eyeInterior[p]))continue;
      const gaps=[];
      for(const p of c.pixels){const x=p%W;if(lid[x]>=0)gaps.push(lid[x]-Math.floor(p/W));}
      if(!gaps.length)continue;
      gaps.sort((a,b)=>a-b);
      if(gaps[Math.floor(gaps.length*.5)]<h*.34 || c.bottom>=wb.top-h*.18)continue;
      for(const p of c.pixels){brows[p]=1;eyes[p]=0;crease[p]=0;creaseBackground[p*4+3]=0;reassignedBrowPixels++;}
    }
    const removedHairPixels=removeHairFromEyes(eyes,eyeInterior,crease,creaseBackground,rejectedHair,lid,box,wb,h);
    details.push({box,whiteBox:wb,scleraDetectionPass:scleraResult.pass,foldPixels:folds,browPixels:browCount,reassignedBrowPixels,reassignedHairPixels,recoveredLashPixels,removedHairPixels,skinLuma,connectedRegionSeparated:Boolean(needsSeparation)});
  }
  // A crease candidate must never override an already identified eyebrow.
  for(let p=0;p<N;p++)if(brows[p]){eyes[p]=0;crease[p]=0;creaseBackground[p*4+3]=0;}
  return {eyes,brows,eyeInterior,crease,creaseBackground,rejectedHair,required:eyes.slice(),requiredBrows:brows.slice(),details};
}

export const refineEyes=(data,rough,threshold,center=512)=>separate(data,rough,threshold,center,false);
export const refineProfileEye=(data,eye,threshold)=>separate(data,[eye],threshold,eye.cx,true);
