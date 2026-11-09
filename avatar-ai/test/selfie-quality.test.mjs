import test from 'node:test';
import assert from 'node:assert/strict';
import sharp from 'sharp';
import {assessSelfie} from '../selfie-quality.mjs';

const pixels=Buffer.alloc(64*64*3);
for(let y=0;y<64;y++)for(let x=0;x<64;x++){
  const value=50+((x+y)%32)*5;
  pixels.fill(value,(y*64+x)*3,(y*64+x+1)*3);
}
const selfie=await sharp(pixels,{raw:{width:64,height:64,channels:3}}).png().toBuffer();
function response(hairLength='short',quality={}){
  return {
    traits:{hair:'검은 머리',eyes:'둥근 눈',eyebrows:'일자 눈썹',nose:'곧은 코',mouth:'다문 입',face:'둥근 얼굴',skin_tone:'밝은 피부',accessories:'안경',hair_length:hairLength},
    quality:{faceCount:1,faceBox:[200,200,600,600],visibility:{eyes:'clear',nose:'clear',mouth:'clear'},lighting:'adequate',sharpness:'sharp',pose:'front',clipped:false,...quality},
  };
}

test('localized short hair normalizes without changing the input or quality',async()=>{
  const input=response('짧음');
  const result=await assessSelfie(selfie,input);
  assert.equal(result.traits.hair_length,'short');
  assert.equal(result.quality.canGenerate,true);
  assert.equal(input.traits.hair_length,'짧음');
});

test('all documented English hair lengths remain valid',async()=>{
  for(const value of ['short','medium','long','extra_long','unknown']){
    const result=await assessSelfie(selfie,response(value));
    assert.equal(result.traits.hair_length,value);
    assert.equal(result.quality.canGenerate,true);
  }
});

test('unsupported or malformed hair lengths are not guessed',async()=>{
  for(const value of ['조금 짧은 편','SHORT','',null,{},42]){
    await assert.rejects(assessSelfie(selfie,response(value)),{code:'SELFIE_QUALITY_UNAVAILABLE'});
  }
});

test('normalizing hair length never bypasses pose or sharpness rejection',async()=>{
  for(const [quality,expected] of [[{pose:'turned'},'FACE_POSE'],[{sharpness:'blurry'},'FACE_BLUR'],[{pose:'unknown'},'FACE_POSE']]){
    const result=await assessSelfie(selfie,response('짧음',quality));
    assert.equal(result.quality.canGenerate,false);
    assert.ok(result.quality.issues.some(issue=>issue.code===expected));
  }
});

test('localized quality enums remain invalid',async()=>{
  await assert.rejects(assessSelfie(selfie,response('짧음',{pose:'정면'})),{code:'SELFIE_QUALITY_UNAVAILABLE'});
});
