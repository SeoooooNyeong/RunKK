import test from 'node:test';
import assert from 'node:assert/strict';
import sharp from 'sharp';
import {splitRightHead} from '../right-head-layers.mjs';
import {profile} from './profile-fixture.mjs';
test('우측 레이어는 원본 좌표와 RGBA를 보존하고 눈·눈썹·코·입·목을 분리한다',async()=>{
 const result=await splitRightHead(profile);
 const source=await sharp(profile).ensureAlpha().raw().toBuffer();
 const parts=await Promise.all(['rightFace','rightEyes','rightHair'].map(async k=>{const meta=await sharp(result[k]).metadata();assert.equal(meta.width,1024);assert.equal(meta.height,1024);return sharp(result[k]).ensureAlpha().raw().toBuffer();}));
 for(let p=0;p<1024*1024;p++){const present=parts.filter(a=>a[p*4+3]>0);assert.equal(present.length,source[p*4+3]?1:0);if(present.length)assert.deepEqual(present[0].subarray(p*4,p*4+4),source.subarray(p*4,p*4+4));}
 const belongs=(x,y,k)=>{assert.ok(parts[k][(y*1024+x)*4+3]>0,`${x},${y} belongs to ${k}`);};
 belongs(785,550,1);belongs(760,404,2);belongs(905,625,0);belongs(835,746,0);belongs(650,875,0);
 assert.equal(result.rightLayerLayout.independentTransforms,false);
});
test('분리할 눈이 없는 이미지는 성공으로 반환하지 않는다',async()=>{
 const png=await sharp({create:{width:1024,height:1024,channels:4,background:'#00000000'}}).png().toBuffer();
 await assert.rejects(splitRightHead(png),e=>e.code==='RIGHT_LAYER_EXTRACTION_FAILED');
});
