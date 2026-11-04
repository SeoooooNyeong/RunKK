/* One visible neck: retain the body's neck and hide only the head's neck stub. */
(() => {
  'use strict';
  const cap=(v,a,b)=>Math.max(a,Math.min(b,v));
  const median=list=>{const a=[...list].sort((x,y)=>x-y);return a[Math.floor(a.length/2)];};
  const skin=(r,g,b,a)=>a>90&&r>g+3&&g>b-2&&r>b+8&&r>55;
  const canvas=(w,h)=>{const c=document.createElement('canvas');c.width=w;c.height=h;return c;};
  function prepare(source,pivotX=.5,pivotY=.985){
    const w=source.width,h=source.height,g=source.getContext('2d',{willReadFrequently:true});
    const pixels=g.getImageData(0,0,w,h),data=pixels.data,cx=cap(Math.round(w*pivotX),0,w-1),base=cap(Math.round(h*pivotY),0,h-1);
    const fallback={canvas:source,confident:false,chinY:base,skinColor:null};
    function run(y){
      if(y<0||y>=h)return null;
      const good=x=>{const i=(y*w+x)*4;return skin(data[i],data[i+1],data[i+2],data[i+3]);};
      if(!good(cx))return null;let left=cx,right=cx;
      while(left>0&&good(left-1))left--;while(right<w-1&&good(right+1))right++;
      return {left,right,width:right-left+1};
    }
    const rows=[];
    for(let y=Math.round(base-h*.04);y<=base-h*.015;y+=2){const r=run(y);if(r)rows.push(r);}
    if(rows.length<4)return fallback;
    const width=median(rows.map(r=>r.width));
    const widths=rows.map(r=>r.width).sort((a,b)=>a-b);
    if(width<w*.035||width>w*.25||(widths[Math.floor(widths.length*.8)]-widths[Math.floor(widths.length*.2)])/width>.30)return fallback;
    let chin=-1;
    for(let y=Math.round(base-h*.025);y>=Math.max(0,base-h*.22);y--){
      const r=run(y),above=run(y-2);
      if(r&&above&&r.width>width*1.25&&above.width>width*1.25){chin=y+1;break;}
    }
    if(chin<0||base-chin<h*.012||base-chin>h*.16)return fallback;
    const center=median(rows.map(r=>(r.left+r.right)/2)),half=width*.79;
    const output=canvas(w,h),og=output.getContext('2d');og.drawImage(source,0,0);
    const edited=og.getImageData(0,0,w,h),a=edited.data;
    const bottom=Math.min(h,Math.ceil(base+h*.035));
    for(let y=Math.max(0,Math.floor(chin-width*.03-2));y<bottom;y++)for(let x=Math.max(0,Math.floor(center-half-2));x<Math.min(w,Math.ceil(center+half+2));x++){
      const u=(x-center)/half,edge=chin-width*.025*u*u;
      const vertical=cap((y-edge+1.2)/2.4,0,1),horizontal=cap((half+1.5-Math.abs(x-center))/2.5,0,1);
      const i=(y*w+x)*4;a[i+3]=Math.round(a[i+3]*(1-vertical*horizontal));
    }
    og.putImageData(edited,0,0);
    const colors=[[],[],[]];
    for(let y=Math.max(0,Math.round(chin-h*.04));y<chin-h*.016;y+=2)for(let x=Math.max(0,Math.round(center-width*.45));x<Math.min(w,center+width*.45);x+=2){
      const i=(y*w+x)*4;if(skin(data[i],data[i+1],data[i+2],data[i+3]))for(let k=0;k<3;k++)colors[k].push(data[i+k]);
    }
    return {canvas:output,confident:true,chinY:chin,neckCenter:center,neckWidth:width,skinColor:colors[0].length>8?colors.map(median):null};
  }
  function matchBodyNeck(source,head,pack){
    if(!head.confident||!head.skinColor)return source;
    const w=source.width,h=source.height,c=canvas(w,h),g=c.getContext('2d',{willReadFrequently:true});g.drawImage(source,0,0);
    const left=cap(Math.floor((pack.neck[0]-.14)*w),0,w-1),top=0,rw=Math.min(w-left,Math.ceil(w*.28)),rh=Math.min(h,Math.ceil((pack.neck[1]+.015)*h));
    const block=g.getImageData(left,top,rw,rh),a=block.data,values=[[],[],[]];
    for(let i=0;i<a.length;i+=4)if(skin(a[i],a[i+1],a[i+2],a[i+3]))for(let k=0;k<3;k++)values[k].push(a[i+k]);
    if(values[0].length<20)return source;
    const delta=values.map((v,k)=>cap(head.skinColor[k]-median(v),-48,48));
    for(let i=0;i<a.length;i+=4)if(skin(a[i],a[i+1],a[i+2],a[i+3]))for(let k=0;k<3;k++)a[i+k]=cap(Math.round(a[i+k]+delta[k]),0,255);
    g.putImageData(block,left,top);return c;
  }
  // AI metadata sometimes points into the chest. Use the actual exposed neck,
  // not that estimate, for both the body anchor and the visible neck length.
  function inspectBody(source,pack){
    const w=source.width,h=source.height;
    const a=source.getContext('2d',{willReadFrequently:true}).getImageData(0,0,w,h).data;
    const cx=cap(Math.round(pack.neck[0]*w),Math.round(w*.3),Math.round(w*.7));
    const fallback={confident:false,neck:pack.neck};
    const at=(x,y)=>(y*w+x)*4;
    let sample=null;
    function run(y){
      const good=x=>{const i=at(x,y);return skin(a[i],a[i+1],a[i+2],a[i+3])&&(!sample||Math.hypot(a[i]-sample[0],a[i+1]-sample[1],a[i+2]-sample[2])<78);};
      if(!good(cx))return null;
      let left=cx,right=cx;
      while(left>0&&good(left-1))left--;
      while(right<w-1&&good(right+1))right++;
      return {left,right,width:right-left+1,y};
    }
    let top=-1,bottom=-1,missing=0;
    const rows=[];
    for(let y=0;y<Math.ceil(h*.28);y++){
      const r=run(y);
      if(top<0){
        if(!r||r.width<w*.035||r.width>w*.30)continue;
        // Require several solid rows so an isolated outline is not a neck.
        if(![1,2,3].every(d=>run(y+d)?.width>=w*.035))continue;
        const i=at(cx,Math.min(h-1,y+Math.max(5,Math.round(h*.01)))),j=at(cx,y);
        if(Math.hypot(a[i]-a[j],a[i+1]-a[j+1],a[i+2]-a[j+2])>60)continue;
        top=y;sample=[a[i],a[i+1],a[i+2]];
      }
      if(r&&r.width>=w*.012&&r.width<w*.38){rows.push(r);bottom=y;missing=0;}
      else if(++missing>3)break;
    }
    if(top<0||bottom-top<h*.012||rows.length<6)return fallback;
    const upper=rows.filter(r=>r.y<=top+(bottom-top)*.4);
    const center=median(upper.map(r=>(r.left+r.right)/2));
    return {confident:true,neck:[center/w,(bottom+1)/h],top,base:bottom+1,
      width:median(upper.map(r=>r.width)),height:bottom+1-top};
  }
  function placement(head,pivotY,headHeight,gap=10,offset=0,body=null,bodyHeight=570){
    if(!head?.confident)return {pivotY,y:450+offset};
    if(body?.confident){
      const scale=bodyHeight/body.imageHeight;
      const available=Math.max(0,body.height*scale-2);
      const natural=Math.min(available,Math.max(10,body.width*scale*.4));
      // Even at the maximum slider value, the chin must overlap real skin.
      const visible=Math.min(available,natural+gap);
      return {pivotX:head.neckCenter/head.canvas.width,pivotY:head.chinY/head.canvas.height,
        y:450-visible+offset,visibleNeck:visible};
    }
    return {pivotY:head.chinY/head.canvas.height,y:450-gap+offset};
  }
  window.RunKKNeckJoin={prepare,inspectBody,matchBodyNeck,placement};
})();
