import sharp from 'sharp';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';

// Execute the same pixel rules as the web renderer; no second approximation.
class PixelCanvas{
  constructor(w=0,h=0,pixels){this.width=w;this.height=h;this.pixels=pixels;}
  getContext(){const c=this,allocate=()=>c.pixels??=new Uint8ClampedArray(c.width*c.height*4);return {
    drawImage(source){allocate();c.pixels.set(source.pixels);},
    getImageData(x,y,w,h){allocate();const data=new Uint8ClampedArray(w*h*4);for(let row=0;row<h;row++)data.set(c.pixels.subarray(((y+row)*c.width+x)*4,((y+row)*c.width+x+w)*4),row*w*4);return {data,width:w,height:h};},
    putImageData(block,x,y){allocate();for(let row=0;row<block.height;row++)c.pixels.set(block.data.subarray(row*block.width*4,(row+1)*block.width*4),((y+row)*c.width+x)*4);}
  };}
}
const context={window:{},document:{createElement:()=>new PixelCanvas()}};
vm.runInNewContext(await readFile(new URL('./public/neck-join.js',import.meta.url),'utf8'),context);
const join=context.window.RunKKNeckJoin;
async function canvas(buffer){const {data,info}=await sharp(buffer).ensureAlpha().raw().toBuffer({resolveWithObject:true});return new PixelCanvas(info.width,info.height,new Uint8ClampedArray(data));}
const version='runkk-mobile-compose-v2';
export async function mobileHead(face,alignment){
  const source=await canvas(face),head=join.prepare(source,alignment.pivotX,alignment.pivotY);
  return {version,canvas:[source.width,source.height],anchor:[head.confident?head.neckCenter/source.width:alignment.pivotX,head.confident?head.chinY/source.height:alignment.pivotY],displayHeight:444/Math.max(.3,Math.min(.995,alignment.pivotY)),neckRemoved:head.confident,skinColor:head.skinColor||null,neckMask:head.confident?{mode:'remove-neck',chinY:head.chinY,centerX:head.neckCenter,width:head.neckWidth,baseY:Math.max(0,Math.min(source.height-1,Math.round(source.height*alignment.pivotY)))}:null,scene:{canvas:[1024,1024],connection:[512,450],bodyHeight:570,neckGap:10,outerScale:.93,outerCenter:[512,512],drawOrder:['body','faceImage (neckMask applied)','eyesImage','hairImage']}};
}
export async function mobileBody(buffer,pack){
  const source=await canvas(buffer),fit=join.inspectBody(source,pack);
  return {version,canvas:[source.width,source.height],neck:[...fit.neck],neckDetected:fit.confident,neckWidth:fit.confident?fit.width:null,neckHeight:fit.confident?fit.height:null,neckTop:fit.confident?fit.top:null};
}
