/* Front preview only. Source PNGs and their shared coordinates are immutable. */
(() => {
  'use strict';
  const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
  function frameAt(seconds){
    const t=((seconds%25)+25)%25;
    for(const start of [3.1,7.5,12.8,17.0,21.6]){
      const phase=t-start;
      if(phase<0||phase>=.25)continue;
      return phase<.07||phase>=.16?'half':'closed';
    }
    return 'open';
  }
  function closureAt(seconds){return {open:0,half:.5,closed:1}[frameAt(seconds)];}
  function splitBounds(pixels,width,height){
    const boxes=[0,1].map(()=>({left:width,top:height,right:-1,bottom:-1}));
    for(let y=0;y<height;y++)for(let x=0;x<width;x++)if(pixels[(y*width+x)*4+3]>32){
      const b=boxes[x<width/2?0:1];b.left=Math.min(b.left,x);b.top=Math.min(b.top,y);b.right=Math.max(b.right,x);b.bottom=Math.max(b.bottom,y);
    }
    return boxes.filter(b=>b.right>=b.left).map(b=>({...b,width:b.right-b.left+1,height:b.bottom-b.top+1}));
  }
  function fitFrameRect(halfBox,sourceBox,target){
    const sx=target.width/halfBox.width,sy=Math.min(sx,target.height*.62/halfBox.height);
    const x=target.left-halfBox.left*sx,y=target.top+target.height-(halfBox.top+halfBox.height)*sy;
    return {left:x+sourceBox.left*sx,top:y+sourceBox.top*sy,width:sourceBox.width*sx,height:sourceBox.height*sy};
  }
  let templatePromise=null;
  function loadTemplates(loadImage){
    if(!templatePromise)templatePromise=Promise.all(['half','closed'].map(async name=>{
      const image=await loadImage(`assets/eyes-${name}-template.png`),canvas=document.createElement('canvas');
      canvas.width=image.width;canvas.height=image.height;const g=canvas.getContext('2d');g.drawImage(image,0,0);
      const boxes=splitBounds(g.getImageData(0,0,canvas.width,canvas.height).data,canvas.width,canvas.height);
      if(boxes.length!==2)throw new Error('눈 프레임 이미지에서 두 눈을 찾지 못했어요.');
      return [name,{canvas,boxes}];
    })).then(entries=>Object.fromEntries(entries)).catch(error=>{templatePromise=null;throw error;});
    return templatePromise;
  }
  function createFrames(rig,templates){
    const frames={open:rig.eyes};
    for(const name of ['half','closed']){
      const canvas=document.createElement('canvas');canvas.width=rig.width;canvas.height=rig.height;
      const g=canvas.getContext('2d'),source=templates[name];
      for(let i=0;i<2;i++){
        const box=source.boxes[i],crop={left:Math.max(0,box.left-3),top:Math.max(0,box.top-3),width:box.width+6,height:box.height+6};
        const dest=fitFrameRect(templates.half.boxes[i],crop,rig.boxes[i].eyeRect);
        g.drawImage(source.canvas,crop.left,crop.top,crop.width,crop.height,dest.left,dest.top,dest.width,dest.height);
      }
      frames[name]=canvas;
    }
    return frames;
  }
  function inspect(face,eyes,width,height,eyeBoxes=null){
    const total=width*height;
    if(face.length!==total*4||eyes.length!==total*4)return null;
    if(eyeBoxes){
      const boxes=['leftEye','rightEye'].map(key=>{
        const b=eyeBoxes[key];
        if(!b||![b.left,b.top,b.right,b.bottom].every(Number.isFinite))return null;
        const left=Math.max(0,Math.round(b.left*width/1024)),top=Math.max(0,Math.round(b.top*height/1024));
        const right=Math.min(width-1,Math.round(b.right*width/1024)),bottom=Math.min(height-1,Math.round(b.bottom*height/1024));
        if(right-left<width*.025||bottom-top<height*.018)return null;
        return {left,right,top,bottom,width:right-left+1,height:bottom-top+1};
      });
      if(boxes.some(box=>!box))return null;
      boxes.sort((a,b)=>a.left-b.left);
      if(boxes[1].left-boxes[0].right<width*.06||Math.abs(boxes[0].top+boxes[0].bottom-boxes[1].top-boxes[1].bottom)>height*.16)return null;
      for(const box of boxes)box.eyeRect={left:box.left,top:box.top,width:box.width,height:box.height};
      return {boxes,width,height,method:'vision-eye-boxes'};
    }
    const seen=new Uint8Array(total),queue=new Int32Array(total),components=[];
    for(let start=0;start<total;start++){
      if(seen[start]||eyes[start*4+3]<32)continue;
      let read=0,count=1,left=width,right=0,top=height,bottom=0;queue[0]=start;seen[start]=1;
      while(read<count){const p=queue[read++],x=p%width,y=(p/width)|0;
        left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
        for(const q of [x?p-1:-1,x<width-1?p+1:-1,y?p-width:-1,y<height-1?p+width:-1])if(q>=0&&!seen[q]&&eyes[q*4+3]>=32){seen[q]=1;queue[count++]=q;}
      }
      if(count>total*.0001)components.push({left,right,top,bottom,count});
    }
    const boxes=components.sort((a,b)=>b.count-a.count).slice(0,2).sort((a,b)=>a.left-b.left);
    if(boxes.length!==2)return null;
    for(const b of boxes){b.width=b.right-b.left+1;b.height=b.bottom-b.top+1;b.cx=(b.left+b.right)/2;b.cy=(b.top+b.bottom)/2;}
    if(boxes[1].cx-boxes[0].cx<width*.1||Math.abs(boxes[0].cy-boxes[1].cy)>height*.12||boxes.some(b=>b.width<width*.035||b.height<height*.025)||Math.max(...boxes.map(b=>b.count))/Math.min(...boxes.map(b=>b.count))>3)return null;
    for(const box of boxes)box.eyeRect={left:box.left,top:box.top,width:box.width,height:box.height};
    return {boxes,width,height};
  }
  function prepare(faceCanvas,eyesCanvas,templates,eyeBoxes=null){
    const width=faceCanvas.width,height=faceCanvas.height;
    if(eyesCanvas.width!==width||eyesCanvas.height!==height)return null;
    const rig=inspect(faceCanvas.getContext('2d').getImageData(0,0,width,height).data,eyesCanvas.getContext('2d').getImageData(0,0,width,height).data,width,height,eyeBoxes);
    if(!rig)return null;
    if(!templates)return null;
    const prepared={...rig,face:faceCanvas,eyes:eyesCanvas};
    prepared.frames=createFrames(prepared,templates);return prepared;
  }
  function draw(g,rig,closure){
    const name=closure>=.75?'closed':closure>0?'half':'open';
    g.drawImage(rig.face,0,0);
    if(name!=='open'&&rig.frames)g.drawImage(rig.frames[name],0,0);
    else g.drawImage(rig.eyes,0,0);
  }
  window.RunKKEyeBlink={closureAt,frameAt,fitFrameRect,splitBounds,loadTemplates,inspect,prepare,draw};
})();
