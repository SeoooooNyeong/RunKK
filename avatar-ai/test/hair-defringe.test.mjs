import test from 'node:test';
import assert from 'node:assert/strict';
import {defringeHair} from '../hair-defringe.mjs';

const W=90,H=60;
function put(buffer,x,y,color){buffer.set(color,(y*W+x)*4);}
test('face-edge skin halo is removed while hair highlights and nearby face stay intact',()=>{
  const face=Buffer.alloc(W*H*4),hair=Buffer.alloc(W*H*4);
  for(let y=8;y<55;y++)for(let x=12;x<78;x++)put(face,x,y,[245,207,188,255]);
  for(let y=10;y<42;y++)for(let x=15;x<75;x++)put(hair,x,y,[70,52,44,255]);
  for(let x=25;x<65;x++)put(hair,x,21,[170,143,120,255]);
  // A translucent, skin-colored fringe at the bottom edge of the hair.
  for(let x=20;x<70;x++)put(hair,x,41,[185,145,126,210]);
  const beforeFace=Buffer.from(face),highlight=hair.subarray((21*W+45)*4,(21*W+45)*4+4).toString('hex');
  const changed=defringeHair(hair,face,W,H,{left:12,top:8,right:77,bottom:54});
  assert.ok(changed>0);
  assert.ok(hair[(41*W+45)*4+3]<210);
  assert.equal(hair.subarray((21*W+45)*4,(21*W+45)*4+4).toString('hex'),highlight);
  assert.equal(face[(41*W+45)*4+3],255);
  assert.deepEqual(face.subarray((25*W+45)*4,(25*W+45)*4+4),beforeFace.subarray((25*W+45)*4,(25*W+45)*4+4));
});
