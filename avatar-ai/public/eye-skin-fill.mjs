// Fill the eye cutouts in the face layer with nearby skin pixels.
// The eye sprites themselves remain transparent and unmodified.
const median=values=>values.sort((a,b)=>a-b)[values.length>>1];
const clamp=(x,a,b)=>Math.min(b,Math.max(a,x));
export function fillFaceEyeHoles(face,eyes,width,height){
  if(face.length!==width*height*4||eyes.length!==face.length)throw new Error('눈과 얼굴 레이어 크기가 달라요.');
  const boxes=[0,1].map(()=>({left:width,right:-1,top:height,bottom:-1}));
  for(let y=0;y<height;y++)for(let x=0;x<width;x++)if(eyes[(y*width+x)*4+3]>32){
    const b=boxes[x<width/2?0:1];b.left=Math.min(b.left,x);b.right=Math.max(b.right,x);b.top=Math.min(b.top,y);b.bottom=Math.max(b.bottom,y);
  }
  let filled=0;
  for(const b of boxes){
    if(b.right-b.left<width*.035||b.bottom-b.top<height*.02)continue;
    const cx=(b.left+b.right)/2,cy=(b.top+b.bottom)/2,pad=Math.max(5,Math.round(width*.006)),ring=Math.max(16,Math.round(width*.03));
    const all=[[],[],[]],quadrants=Array.from({length:4},()=>[[],[],[]]);
    for(let y=Math.max(0,b.top-ring);y<=Math.min(height-1,b.bottom+ring);y+=2)for(let x=Math.max(0,b.left-ring);x<=Math.min(width-1,b.right+ring);x+=2){
      if(x>=b.left-pad&&x<=b.right+pad&&y>=b.top-pad&&y<=b.bottom+pad)continue;
      const i=(y*width+x)*4,r=face[i],g=face[i+1],blue=face[i+2];
      if(face[i+3]<220||r<g+3||g<blue+2||r<blue+8)continue;
      const q=(y>=cy?2:0)+(x>=cx?1:0);
      for(let k=0;k<3;k++){all[k].push(face[i+k]);quadrants[q][k].push(face[i+k]);}
    }
    if(all[0].length<12)continue;
    const base=all.map(median),colors=quadrants.map(q=>q[0].length>=5?q.map(median):base);
    const marked=new Uint8Array(width*height);
    for(let y=b.top;y<=b.bottom;y++)for(let x=b.left;x<=b.right;x++)if(eyes[(y*width+x)*4+3]>8){
      for(let dy=-pad;dy<=pad;dy++)for(let dx=-pad;dx<=pad;dx++)if(dx*dx+dy*dy<=pad*pad){
        const xx=x+dx,yy=y+dy;if(xx>=0&&xx<width&&yy>=0&&yy<height)marked[yy*width+xx]=1;
      }
    }
    const unknown=[];
    for(let y=Math.max(0,b.top-pad);y<=Math.min(height-1,b.bottom+pad);y++)for(let x=Math.max(0,b.left-pad);x<=Math.min(width-1,b.right+pad);x++){
      if(!marked[y*width+x])continue;
      const i=(y*width+x)*4,u=clamp((x-b.left)/(b.right-b.left),0,1),v=clamp((y-b.top)/(b.bottom-b.top),0,1);
      if(face[i+3]>=245)continue;
      for(let k=0;k<3;k++)face[i+k]=Math.round((colors[0][k]*(1-u)+colors[1][k]*u)*(1-v)+(colors[2][k]*(1-u)+colors[3][k]*u)*v);
      face[i+3]=255;unknown.push(y*width+x);filled++;
    }
    // Diffuse existing skin from the cutout edge inward. Opaque source art is fixed.
    for(let pass=0;pass<70;pass++)for(const p of unknown){
      const x=p%width,y=(p/width)|0,i=p*4;let count=0,r=0,g=0,blue=0;
      for(const q of [x?p-1:-1,x<width-1?p+1:-1,y?p-width:-1,y<height-1?p+width:-1]){
        if(q<0||face[q*4+3]<245)continue;
        r+=face[q*4];g+=face[q*4+1];blue+=face[q*4+2];count++;
      }
      if(count){face[i]=Math.round(r/count);face[i+1]=Math.round(g/count);face[i+2]=Math.round(blue/count);}
    }
  }
  return filled;
}
