const W=1024;
function islands(mask,box){
  const visited=new Uint8Array(W*W),queue=new Int32Array(W*W),result=[];
  for(let y=box.top;y<=box.bottom;y++)for(let x=box.left;x<=box.right;x++){
    const start=y*W+x;if(!mask[start]||visited[start])continue;
    let read=0,length=1,left=W,right=0,top=W,bottom=0;queue[0]=start;visited[start]=1;
    while(read<length){const p=queue[read++],xx=p%W,yy=(p/W)|0;left=Math.min(left,xx);right=Math.max(right,xx);top=Math.min(top,yy);bottom=Math.max(bottom,yy);
      for(const q of [xx>box.left?p-1:-1,xx<box.right?p+1:-1,yy>box.top?p-W:-1,yy<box.bottom?p+W:-1])if(q>=0&&mask[q]&&!visited[q]){visited[q]=1;queue[length++]=q;}
    }
    result.push({left,right,top,bottom,width:right-left+1,height:bottom-top+1,count:length,pixels:queue.slice(0,length)});
  }
  return result;
}
export function refineEyes(data,rough,threshold){
  const maxWidth=Math.max(...rough.map(c=>c.width)),maxHeight=Math.max(...rough.map(c=>c.height));
  const eyes=new Uint8Array(W*W),browsMask=new Uint8Array(W*W),required=new Uint8Array(W*W),requiredBrows=new Uint8Array(W*W),details=[];
  for(const eye of rough){
    const box={left:Math.max(0,Math.floor(eye.cx-maxWidth*.85)),right:Math.min(W-1,Math.ceil(eye.cx+maxWidth*.85)),top:Math.max(0,Math.floor(eye.bottom-maxHeight*1.28)),bottom:Math.min(W-1,eye.bottom+10)};
    const whites=new Uint8Array(W*W);
    for(let y=box.top;y<=box.bottom;y++)for(let x=box.left;x<=box.right;x++){
      const p=y*W+x,i=p*4,r=data[i],g=data[i+1],b=data[i+2];
      if(data[i+3]>100&&Math.min(r,g,b)>165&&Math.max(r,g,b)-Math.min(r,g,b)<24)whites[p]=1;
    }
    const components=islands(whites,box).filter(c=>c.count>75&&c.height>maxHeight*.20&&c.width>4);
    if(components.length<1)throw Object.assign(new Error('흰자 경계를 확인하지 못해 눈 분리를 적용하지 않았어요.'),{status:502});
    const whiteBox={left:Math.min(...components.map(c=>c.left)),right:Math.max(...components.map(c=>c.right)),top:Math.min(...components.map(c=>c.top)),bottom:Math.max(...components.map(c=>c.bottom))};
    const cx=(whiteBox.left+whiteBox.right)/2,cy=(whiteBox.top+whiteBox.bottom)/2;
    const rx=(whiteBox.right-whiteBox.left)*.73,ry=Math.max(maxHeight*.68,(whiteBox.bottom-whiteBox.top)*.78);
    const ink=new Uint8Array(W*W);
    for(let y=box.top;y<=box.bottom;y++)for(let x=box.left;x<=box.right;x++){
      const p=y*W+x,i=p*4,ell=Math.abs((x-cx)/rx)**2.4+Math.abs((y-cy)/ry)**2.4;
      if(ell<=1&&data[i+3]>60&&(whites[p]||(data[i]+data[i+1]+data[i+2])/3<threshold*1.08))ink[p]=1;
    }
    const selected=islands(ink,box).filter(c=>c.pixels.some(p=>whites[p])||c.count>eye.count*.2&&Math.abs((c.left+c.right)/2-cx)<maxWidth*.4);
    const eyePixels=[];for(const c of selected)for(const p of c.pixels){eyes[p]=required[p]=1;eyePixels.push(p);}
    for(const c of components)for(const p of c.pixels)eyes[p]=required[p]=1;
    // Fill the interior of the complete white/iris/lash silhouette, not an
    // independently cropped face patch. Holes surrounded by ink are highlights.
    for(let y=box.top;y<=box.bottom;y++){let left=W,right=-1;for(let x=box.left;x<=box.right;x++)if(eyes[y*W+x]){left=Math.min(left,x);right=Math.max(right,x);}for(let x=left;x<=right;x++)eyes[y*W+x]=1;}
    const browTop=Math.max(0,Math.round(whiteBox.top-maxHeight*.98)),browBottom=Math.round(whiteBox.top-maxHeight*.23);
    const coreLeft=Math.round(cx-maxWidth*.30),coreRight=Math.round(cx+maxWidth*.30);
    let peakY=browTop,peak=0;
    const isInk=(x,y)=>{if(x<0||x>=W||y<0||y>=W)return false;const i=(y*W+x)*4;return data[i+3]>90&&(data[i]+data[i+1]+data[i+2])/3<threshold*1.05;};
    for(let y=browTop;y<=browBottom;y++){let count=0;for(let x=coreLeft;x<=coreRight;x++)if(isInk(x,y))count++;if(count>peak){peak=count;peakY=y;}}
    const browPixels=[],thinInk=new Uint8Array(W*W);
    const browBox={left:Math.max(0,Math.floor(cx-maxWidth*.9)),right:Math.min(W-1,Math.ceil(cx+maxWidth*.9)),top:browTop,bottom:browBottom};
    const lightSkin=(x,y)=>{if(y<0||y>=W)return false;const i=(y*W+x)*4,r=data[i],g=data[i+1],b=data[i+2];return data[i+3]>90&&r>g+2&&r>b+8&&(r+g+b)/3>threshold*1.1;};
    for(let x=browBox.left;x<=browBox.right;x++){
      let start=-1;
      for(let y=browTop;y<=browBottom+1;y++){
        if(y<=browBottom&&isInk(x,y)){if(start<0)start=y;}
        else if(start>=0){const bottom=y-1,height=bottom-start+1;
          if(height>1&&height<maxHeight*.32&&start>browTop&&bottom<browBottom&&[2,4,6].some(d=>lightSkin(x,start-d))&&[2,4,6].some(d=>lightSkin(x,bottom+d)))for(let yy=start;yy<=bottom;yy++)thinInk[yy*W+x]=1;
          start=-1;
        }
      }
    }
    const brows=islands(thinInk,browBox).filter(c=>c.width>maxWidth*.18&&c.width>c.height*1.7).sort((a,b)=>b.width*b.count/(b.height+1)-a.width*a.count/(a.height+1));
    if(brows[0])for(const p of brows[0].pixels){const x=p%W,y=(p/W)|0;for(let dy=-2;dy<=2;dy++)for(let dx=-1;dx<=1;dx++){const xx=x+dx,yy=y+dy;if(xx>=0&&xx<W&&yy>=browTop&&yy<=browBottom){const q=yy*W+xx,i=q*4;if(!eyes[q]&&data[i+3]&&(data[i]+data[i+1]+data[i+2])/3<threshold*1.5){browsMask[q]=requiredBrows[q]=1;browPixels.push(q);}}}}
    details.push({box,whiteBox,eyePixels:eyePixels.length,browPixels:browPixels.length,browPeak:{x:Math.round(cx),y:peakY},whiteComponents:components.map(c=>({...c,pixels:undefined})),rough:{...eye,pixels:undefined}});
  }
  return {eyes,brows:browsMask,required,requiredBrows,details};
}
