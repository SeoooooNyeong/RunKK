import test from 'node:test';
import assert from 'node:assert/strict';
import sharp from 'sharp';
import {alignAiParts} from '../ai-layer-align.mjs';

const box={left:400,top:400,right:499,bottom:499};
async function fixture(){
  const raw=Buffer.alloc(1024*1024*4);
  for(let y=400;y<500;y++)for(let x=400;x<500;x++)raw.set([150,80,60,255],(y*1024+x)*4);
  // Alpha outliers must not change semantic feature registration.
  raw.set([150,80,60,30],(250*1024+250)*4);
  const part=await sharp(raw,{raw:{width:1024,height:1024,channels:4}}).png().toBuffer();
  const master=await sharp({create:{width:1024,height:1024,channels:4,background:'#ffffff'}}).png().toBuffer();
  return {master,part,raw,parts:{face:part,eyes:part,hair:part}};
}
const all=value=>({face:value,eyes:value,hair:value});
test('matching semantic boxes preserve already registered pixels despite alpha outliers',async()=>{
  const f=await fixture();const result=await alignAiParts(f.master,f.parts,{sourceBoxes:all(box),layerBoxes:all(box)});
  for(const name of ['face','eyes','hair']){
    assert.ok((await sharp(result.parts[name]).raw().toBuffer()).equals(f.raw),'registered pixel buffer changed');
    assert.equal(result.metrics[name].scale,1);assert.equal(result.metrics[name].offsetX,0);assert.equal(result.metrics[name].offsetY,0);
  }
});
test('registration compares corresponding semantic boxes instead of total alpha bounds',async()=>{
  const f=await fixture();const target={...box,left:420,right:519};
  const result=await alignAiParts(f.master,f.parts,{sourceBoxes:all(target),layerBoxes:all(box)});
  const raw=await sharp(result.parts.eyes).raw().toBuffer();
  assert.equal(raw[(450*1024+420)*4+3],255);assert.equal(raw[(450*1024+400)*4+3],0);
  assert.equal(result.metrics.eyes.scale,1);assert.equal(result.metrics.eyes.offsetX,20);
});
test('missing paired layer box keeps shared canvas rather than guessing from alpha',async()=>{
  const f=await fixture();const result=await alignAiParts(f.master,f.parts,{sourceBoxes:all(box)});
  assert.ok((await sharp(result.parts.hair).raw().toBuffer()).equals(f.raw),'shared canvas pixel buffer changed');
});
