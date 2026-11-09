import test from 'node:test';
import assert from 'node:assert/strict';
import sharp from 'sharp';
import {registerLayerPixels} from '../pixel-layer-registration.mjs';
async function pattern(){
  const pixels=Buffer.alloc(1024*1024*4);
  for(let y=300;y<700;y++)for(let x=270;x<750;x++)pixels.set([(x*7+y*3)%170+20,(x+y*5)%160+30,(x*3+y)%150+40,255],(y*1024+x)*4);
  return sharp(pixels,{raw:{width:1024,height:1024,channels:4}}).png().toBuffer();
}
test('registered identical image retains its original pixels',async()=>{
  const png=await pattern(),r=await registerLayerPixels(png,png);
  assert.equal(r.used,false);assert.ok(r.png.equals(png));
});
test('pixel matching recovers a shifted textured part without face-specific coordinates',async()=>{
  const png=await pattern();
  const crop=await sharp(png).extract({left:0,top:0,width:1024,height:984}).png().toBuffer();
  const shifted=await sharp({create:{width:1024,height:1024,channels:4,background:{r:0,g:0,b:0,alpha:0}}}).composite([{input:crop,left:0,top:40}]).png().toBuffer();
  const r=await registerLayerPixels(png,shifted);
  assert.equal(r.used,true);assert.ok(r.pixelRegistration.registeredLoss<r.pixelRegistration.initialLoss*.5);
  assert.ok(Math.abs(r.offsetY+40)<8);assert.ok(Math.abs(r.scaleX-1)<.03);assert.ok(Math.abs(r.scaleY-1)<.03);
});
test('empty extraction is never positioned using an invented match',async()=>{
  const png=await pattern(),empty=await sharp({create:{width:1024,height:1024,channels:4,background:{r:0,g:0,b:0,alpha:0}}}).png().toBuffer();
  const r=await registerLayerPixels(png,empty);assert.equal(r.used,false);assert.ok(r.png.equals(empty));
});
