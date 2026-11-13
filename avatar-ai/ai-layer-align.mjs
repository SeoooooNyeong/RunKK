import sharp from 'sharp';
import {registerLayerPixels} from './pixel-layer-registration.mjs';

function validBox(box){
  if(!box)return null;
  const values=['left','top','right','bottom'].map(key=>Number(box[key]));
  if(values.some(value=>!Number.isFinite(value)||value<0||value>1023))return null;
  const [left,top,right,bottom]=values.map(Math.round);
  if(right-left<8||bottom-top<8)return null;
  return{left,top,right,bottom,width:right-left+1,height:bottom-top+1,cx:(left+right)/2,cy:(top+bottom)/2};
}
async function alphaBounds(png){
  const {data,info}=await sharp(png).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  let left=info.width,top=info.height,right=-1,bottom=-1;
  for(let y=0;y<info.height;y++)for(let x=0;x<info.width;x++)if(data[(y*info.width+x)*4+3]>20){left=Math.min(left,x);top=Math.min(top,y);right=Math.max(right,x);bottom=Math.max(bottom,y);}
  if(right-left<8||bottom-top<8)return null;
  return{left,top,right,bottom,width:right-left+1,height:bottom-top+1,cx:(left+right)/2,cy:(top+bottom)/2};
}
async function registerToBox(png,from,to){
  const sx=to.width/from.width,sy=to.height/from.height;
  // Uniform scaling keeps the generated facial and hair proportions intact.
  const scale=Math.sqrt(sx*sy);
  if(!Number.isFinite(scale)||scale<.35||scale>2.5)return{png,scale:1,offsetX:0,offsetY:0,used:false};
  const size=Math.max(1,Math.round(1024*scale));
  const resized=await sharp(png).resize(size,size,{fit:'fill'}).png().toBuffer();
  const left=Math.round(to.cx-from.cx*scale),top=Math.round(to.cy-from.cy*scale);
  const cropLeft=Math.max(0,-left),cropTop=Math.max(0,-top);
  const cropWidth=Math.min(size-cropLeft,1024-Math.max(0,left)),cropHeight=Math.min(size-cropTop,1024-Math.max(0,top));
  if(cropWidth<=0||cropHeight<=0)return{png,scale:1,offsetX:0,offsetY:0,used:false};
  const crop=await sharp(resized).extract({left:cropLeft,top:cropTop,width:cropWidth,height:cropHeight}).png().toBuffer();
  const output=await sharp({create:{width:1024,height:1024,channels:4,background:{r:0,g:0,b:0,alpha:0}}})
    .composite([{input:crop,left:Math.max(0,left),top:Math.max(0,top)}]).png().toBuffer();
  return{png:output,scale,offsetX:left,offsetY:top,used:true};
}

export async function alignAiParts(master,parts,{sourceBoxes=null,layerBoxes=null}={}){
  const source=await sharp(master).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  const aligned={},metrics={};
  for(const part of ['face','eyes','hair']){
    const sourceBox=validBox(sourceBoxes?.[part]),reportedLayerBox=validBox(layerBoxes?.[part]);
    let placed={png:parts[part],scale:1,offsetX:0,offsetY:0,used:false};
    // Compare the same semantic feature in both images. Total alpha bounds may
    // include fine strands or residual pixels and are not that feature's box.
    // Without a paired box, preserve the shared canvas required by extraction.
    const sameBox=sourceBox&&reportedLayerBox&&['left','top','right','bottom'].every(key=>sourceBox[key]===reportedLayerBox[key]);
    if(sourceBox&&reportedLayerBox&&!sameBox)placed=await registerToBox(parts[part],reportedLayerBox,sourceBox);
    // Hair/eyes retain source texture, allowing pixel registration to correct
    // extraction drift even when the vision model reports identical boxes.
    const pixel=(part==='hair'||part==='eyes')?await registerLayerPixels(master,parts[part]):null;
    if(pixel?.used)placed=pixel;
    const layer=await sharp(placed.png).ensureAlpha().raw().toBuffer({resolveWithObject:true});
    if(layer.info.width!==source.info.width||layer.info.height!==source.info.height)throw new Error('AI 파츠 캔버스가 원본과 달라요.');
    for(let i=0;i<layer.data.length;i+=4)layer.data[i+3]=Math.min(layer.data[i+3],source.data[i+3]);
    aligned[part]=await sharp(layer.data,{raw:{width:layer.info.width,height:layer.info.height,channels:4}}).png().toBuffer();
    metrics[part]={scale:Number(placed.scale.toFixed(3)),...(placed.scaleX?{scaleX:placed.scaleX,scaleY:placed.scaleY}:{}),offsetX:placed.offsetX,offsetY:placed.offsetY,method:pixel?.used?'source-pixel-registration':placed.used?'vision-semantic-box-uniform-scale':'shared-canvas-fallback',...(pixel?.pixelRegistration?{pixelRegistration:pixel.pixelRegistration}:{}),sourceBox:sourceBox?{left:sourceBox.left,top:sourceBox.top,right:sourceBox.right,bottom:sourceBox.bottom}:null,reportedLayerBox:reportedLayerBox?{left:reportedLayerBox.left,top:reportedLayerBox.top,right:reportedLayerBox.right,bottom:reportedLayerBox.bottom}:null};
  }
  return{parts:aligned,metrics};
}
