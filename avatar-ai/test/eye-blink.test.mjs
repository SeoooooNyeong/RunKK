import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { createRequire } from 'node:module';
const root=process.env.BLINK_APP_ROOT ? new URL('file:///'+process.env.BLINK_APP_ROOT.replaceAll('\\','/')+'/') : new URL('../',import.meta.url);
const require=createRequire(new URL('package.json',root));
const sharp=require('sharp');
const context={window:{}};
vm.runInNewContext(readFileSync(new URL('../public/eye-blink.js',import.meta.url),'utf8'),context);
const {closureAt,frameAt,fitFrameRect,splitBounds,inspect,draw}=context.window.RunKKEyeBlink;

test('blink plays open, half, closed, half, open sprites',()=>{
  assert.equal(closureAt(0),0);assert.equal(closureAt(3),0);
  assert.equal(frameAt(3.14),'half');
  assert.equal(frameAt(3.20),'closed');
  assert.equal(frameAt(3.31),'half');
  assert.equal(closureAt(3.36),0);
  for(let t=0;t<50;t+=.01)assert.ok([0,.5,1].includes(closureAt(t)));
});

test('both supplied transparent sprites share the same per-eye transform',async()=>{
  const load=async name=>sharp(readFileSync(new URL('../public/assets/'+name,import.meta.url))).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  const half=await load('eyes-half-template.png'),closed=await load('eyes-closed-template.png');
  const halfBoxes=splitBounds(half.data,half.info.width,half.info.height),closedBoxes=splitBounds(closed.data,closed.info.width,closed.info.height);
  assert.equal(halfBoxes.length,2);assert.equal(closedBoxes.length,2);
  const target={left:250,top:500,width:180,height:140};
  const h=fitFrameRect(halfBoxes[0],halfBoxes[0],target),c=fitFrameRect(halfBoxes[0],closedBoxes[0],target);
  assert.equal(h.left,target.left);assert.equal(h.width,target.width);
  assert.ok(Math.abs(h.top+h.height-(target.top+target.height))<.001);
  assert.ok(c.height<h.height*.6&&c.width<h.width);
  assert.ok(c.top>h.top&&c.top+c.height<h.top+h.height);
});

test('real default eye holes are filled without changing source layers',async()=>{
  const load=async name=>sharp(readFileSync(new URL('public/assets/'+name,root))).ensureAlpha().raw().toBuffer({resolveWithObject:true});
  const face=await load('face-base-default.png'),eyes=await load('eyes-default.png');
  const beforeFace=Buffer.from(face.data),beforeEyes=Buffer.from(eyes.data);
  const rig=inspect(face.data,eyes.data,face.info.width,face.info.height);
  assert.ok(rig,'default front eyes must be detected');assert.equal(rig.boxes.length,2);
  assert.ok(rig.boxes.every(box=>box.eyeRect.width>20));
  assert.deepEqual(face.data,beforeFace);assert.deepEqual(eyes.data,beforeEyes);
  assert.equal(rig.patch,undefined,'blink renderer has no extra skin paint layer');
});

test('missing eye layers do not enable a malformed blink',()=>{
  assert.equal(inspect(new Uint8Array(40000),new Uint8Array(40000),100,100),null);
  assert.equal(inspect(new Uint8Array(4),new Uint8Array(8),1,1),null);
});

test('open-eye rendering uses original layers without a skin patch or clipping',()=>{
  const calls=[],face={},eyes={};
  draw({drawImage:(...args)=>calls.push(args)}, {face,eyes},0);
  assert.deepEqual(calls,[[face,0,0],[eyes,0,0]]);
});

test('closed-eye rendering uses only the prepared face and eye sprite',()=>{
  const calls=[],face={},eyes={},closed={};
  draw({drawImage:(...args)=>calls.push(args)}, {face,eyes,frames:{closed}},1);
  assert.deepEqual(calls,[[face,0,0],[closed,0,0]]);
});
