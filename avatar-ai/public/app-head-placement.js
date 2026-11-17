/* Android GeneratedAvatarLayers/HeadPlacement와 같은 정면 머리 정규화. */
(() => {
  'use strict';
  const SIZE=1024,MIN_SCALE=.5,MAX_SCALE=1.8,OPAQUE=128;
  const TARGETS={
    female:{centerX:534,chinY:787,crownY:64},
    male:{centerX:520,chinY:854,crownY:59},
  };
  const clamp=(value,min,max)=>Math.max(min,Math.min(max,value));
  const canvas=()=>{const c=document.createElement('canvas');c.width=SIZE;c.height=SIZE;return c;};
  function pixels(image){return image.getContext('2d',{willReadFrequently:true}).getImageData(0,0,SIZE,SIZE).data;}
  function crownY(data){
    for(let y=0;y<SIZE;y++)for(let x=0;x<SIZE;x++)if(data[(y*SIZE+x)*4+3]>=OPAQUE)return y;
    return null;
  }
  function centerAndChin(data,reported){
    if(Number.isFinite(reported?.centerX)&&Number.isFinite(reported?.chinY))return reported;
    const rows=[];
    for(let y=0;y<SIZE;y++){
      let left=SIZE,right=-1;
      for(let x=0;x<SIZE;x++)if(data[(y*SIZE+x)*4+3]>=200){left=Math.min(left,x);right=Math.max(right,x);}
      if(right>=left)rows.push({y,left,right,width:right-left+1});
    }
    if(!rows.length)return null;
    const bottom=rows[rows.length-1];
    const max=Math.max(...rows.map(row=>row.width));
    const neckRows=rows.filter(row=>row.y>bottom.y-max*.45&&row.width<max*.55);
    const neck=neckRows[0];
    if(!neck)return null;
    return {centerX:(neck.left+neck.right)/2,chinY:neck.y};
  }
  function transform(image,placement){
    const out=canvas(),g=out.getContext('2d');
    g.setTransform(placement.scale,0,0,placement.scale,placement.dx,placement.dy);
    g.drawImage(image,0,0);
    return out;
  }
  function prepare(face,eyes,hair,{gender='female',neck=null}={}){
    const target=TARGETS[gender]||TARGETS.female;
    const actual=centerAndChin(pixels(face),neck),crown=crownY(pixels(hair));
    if(!actual||crown===null||actual.chinY<=crown)throw new Error('앱과 같은 머리 정렬 기준을 찾지 못했어요.');
    const scale=clamp((target.chinY-target.crownY)/(actual.chinY-crown),MIN_SCALE,MAX_SCALE);
    const placement={
      scale,
      dx:target.centerX-actual.centerX*scale,
      dy:target.chinY-actual.chinY*scale,
      gender,
      actual,
      target,
    };
    return {face:transform(face,placement),eyes:transform(eyes,placement),hair:transform(hair,placement),placement};
  }
  window.RunKKAppHeadPlacement={prepare};
})();
