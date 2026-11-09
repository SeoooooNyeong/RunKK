import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp, mkdir, writeFile, rm} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import path from 'node:path';
import sharp from 'sharp';
import {AVATAR_VIEW_VERSION, registerAvatarViews, sourceHash} from '../avatar-views.mjs';
import {userDirectory} from '../user-storage.mjs';

// Synthetic alpha PNG: these tests check orchestration, not AI visual quality.
const front = await sharp({create:{width:1024,height:1024,channels:4,background:'#00000000'}})
  .composite([{input:await sharp({create:{width:500,height:700,channels:4,background:'#805f45'}}).png().toBuffer(),left:260,top:100}]).png().toBuffer();
const accepted = {headOnly:true,completeHair:true,profile:true,direction:'right',sameHairAndStyle:true,windAppropriate:true};
async function fixture(t, validation=accepted) {
  const root=await mkdtemp(path.join(tmpdir(),'runkk-wind-'));
  t.after(()=>rm(root,{recursive:true,force:true}));
  const calls={image:0,vision:0,prompt:''};
  const client={
    images:{edit:async request=>{calls.image++;calls.prompt=request.prompt;return {data:[{b64_json:front.toString('base64')}]};}},
    chat:{completions:{create:async request=>{
      calls.vision++;
      const comparison=request.messages[1].content.filter(c=>c.type==='image_url').length===2;
      return {choices:[{message:{content:JSON.stringify(comparison?validation:{headOnly:true,frontFacing:true,hasFace:true})}}]};
    }}}
  };
  const api=registerAvatarViews({get(){},post(){}},{client,root,imageModel:'image-test',visionModel:'vision-test',quality:'high',ensureApiKey(){},isBusy:()=>false,setBusy(){},splitProfile:async png=>({rightFace:png,rightEyes:png,rightHair:png})});
  return {api,root,calls};
}

test('기존 정지 헤어 캐시는 무효화하고 바람 헤어를 한 번 생성한 뒤 재사용한다',async t=>{
  const {api,root,calls}=await fixture(t), hash=sourceHash(front);
  const dir=path.join(userDirectory(root,'alice'),'head-views');
  await mkdir(dir,{recursive:true});
  await writeFile(path.join(dir,hash+'-right.png'),front);
  await writeFile(path.join(dir,hash+'-right.json'),JSON.stringify({userId:'alice',scope:'head',sourceHash:hash,direction:'right',viewVersion:'runkk-head-profile-v2',imageModel:'image-test',visionModel:'vision-test',quality:'high'}));
  assert.equal(await api.cached('alice',hash),null);
  const result=await api.generate('alice',front);
  assert.equal(result.staticImage,true);
  assert.equal(result.viewVersion,AVATAR_VIEW_VERSION);
  assert.deepEqual(result.canvas,[1024,1024]);
  assert.equal(result.validation.windAppropriate,true);
  for(const part of ['Face','Eyes','Hair'])assert.match(result['right'+part+'Image'],/^data:image\/png;base64,/);
  assert.match(calls.prompt,/backward toward the LEFT/);
  assert.match(calls.prompt,/Absolutely NO torso/);
  const again=await api.generate('alice',front);
  assert.equal(again.cached,true);
  assert.equal(again.viewId,result.viewId);
  assert.equal(calls.image,1);
  assert.equal(calls.vision,2);
});

test('바람 표현 검증에 실패하면 저장·자동 재생성하지 않는다',async t=>{
  const {api,calls}=await fixture(t,{...accepted,windAppropriate:false});
  await assert.rejects(api.generate('alice',front),e=>e.code==='AVATAR_VIEW_INVALID');
  assert.equal(await api.cached('alice',sourceHash(front)),null);
  assert.equal(calls.image,1);
});

test('현재 버전 우측 캐시에 레이어가 없으면 이미지 생성 없이 분리하고 재사용한다',async t=>{
  const {api,root,calls}=await fixture(t),hash=sourceHash(front),dir=path.join(userDirectory(root,'alice'),'head-views');
  await mkdir(dir,{recursive:true});
  await writeFile(path.join(dir,hash+'-right.png'),front);
  await writeFile(path.join(dir,hash+'-right.json'),JSON.stringify({userId:'alice',scope:'head',sourceHash:hash,direction:'right',viewVersion:AVATAR_VIEW_VERSION,imageModel:'image-test',visionModel:'vision-test',quality:'high'}));
  const first=await api.cached('alice',hash),again=await api.cached('alice',hash);
  assert.equal(first.rightEyesImage,again.rightEyesImage);
  assert.match(first.rightEyesImage,/^data:image\/png;base64,/);
  assert.equal(calls.image,0);assert.equal(calls.vision,0);
});
