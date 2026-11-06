import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import sharp from 'sharp';
import {fillFaceEyeHoles} from '../public/eye-skin-fill.mjs';

test('default face eye cutouts are filled while the eye sprite and nose stay intact',async()=>{
  const raw=async name=>sharp(await readFile(new URL('../public/assets/'+name,import.meta.url))).ensureAlpha().raw().toBuffer();
  const face=await raw('face-base-default.png'),eyes=await raw('eyes-default.png');
  const originalFace=Buffer.from(face),originalEyes=Buffer.from(eyes);
  const changed=fillFaceEyeHoles(face,eyes,1024,1024);
  assert.ok(changed>1000);
  for(let p=0;p<1024*1024;p++)if(eyes[p*4+3]>32)assert.equal(face[p*4+3],255);
  assert.deepEqual(eyes,originalEyes);
  assert.deepEqual(face.subarray(0,4096),originalFace.subarray(0,4096));
});
