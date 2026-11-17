(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const ASSETS = window.RUNKK_ASSETS;
  const STORAGE = 'runkk-avatar-ai-v3';
  const USER_KEY='runkk-avatar-demo-user';
  const demoUserId=localStorage.getItem(USER_KEY)||('demo-'+crypto.randomUUID());
  localStorage.setItem(USER_KEY,demoUserId);
  const RIG = 'runkk-front-v1';
  const LAYER_SIZE = 1024;
  const HEAD_LAYER_VERSION = 'runkk-coherent-head-v2';
  const HEAD_SEGMENTATION_VERSION = 'runkk-ai-source-pixels-v41';
  const DEFAULT_PIVOT={x:49.3,y:70.1};
  const builtinBodies=window.RUNKK_BODY_PACKS||[];
  const bodySets={};
  const imageCache=new Map();
  const presets = [
    {id:'daily',name:'데일리 러너',desc:'가볍게, 나의 페이스로',shirt:'#f5f3f1',shorts:'#7198da',shoes:'#7198da',mark:false},
    {id:'crew',name:'크루 에디션',desc:'같은 팀, 같은 러닝 룩',shirt:'#344a3e',shorts:'#303a36',shoes:'#a4cd7c',mark:true},
    {id:'sunrise',name:'선라이즈 러너',desc:'기분까지 산뜻한 출발',shirt:'#eed8b1',shorts:'#718e7c',shoes:'#dbaf79',mark:false},
    ...builtinBodies.filter(p=>p.id!=='daily').map(p=>({id:p.id,assetId:p.id,name:p.name,desc:p.description,...p.palette,preserveArt:p.preserveArt,mark:false}))
  ];
  const skinColors = ['#f5cfbc','#e8b99d','#cf9775','#a66e4d','#754c39'];
  const defaults = () => ({version:8,rigVersion:RIG,neckJoin:true,neckGap:10,customBodyPack:null,headData:null,faceData:null,eyesData:null,hairData:null,headName:'기본 머리',headPivotX:50,headPivotY:98.5,traits:null,generationId:null,hairLength:'auto',similarity:60,torsoData:null,bodyPackId:'daily',shirt:'#f5f3f1',shorts:'#7198da',shoes:'#7198da',mark:false,skin:0,headScale:100,headX:0,headY:0,motion:'idle',speed:100,transparent:true});
  let state = defaults(), headSprite = null, faceLayer = null, eyeLayer = null, hairLayer = null, joinedFace = null, bodyTexture = null;
  const bodyMesh = new window.RunKKBodyMesh();
  let playing = !matchMedia('(prefers-reduced-motion: reduce)').matches;
  let time = 0, lastTime = null, isReady = false, savingTimer = null, toastTimer = null, tintFrame = null;
  let selfieUrl = null, selfieFile = null, apiReady = false, aiBusy = false;
  let selfieQuality = null, selfieQualityError = null, testMode = false;
  let avatarViews = null;
  let blinkRig=null,blinkTime=0,eyePose='auto';
  const canvas = $('avatarCanvas'), ctx = canvas.getContext('2d');
  const motionNames = {idle:'대기 중',run:'달리는 중',wave:'인사하는 중'};

  const clamp = (n,lo,hi) => Math.min(hi,Math.max(lo,n));
  const radians = n => n*Math.PI/180;
  const rgb = hex => [1,3,5].map(i=>parseInt(hex.slice(i,i+2),16));
  function surface(w,h) {const c=document.createElement('canvas');c.width=w;c.height=h;return c;}
  function loadImage(src) {return new Promise((resolve,reject)=>{const im=new Image();im.onload=()=>resolve(im);im.onerror=()=>reject(new Error('이미지 파일을 열지 못했어요.'));im.src=src;});}
  function notify(message) {$('toast').textContent=message;$('toast').classList.add('visible');clearTimeout(toastTimer);toastTimer=setTimeout(()=>$('toast').classList.remove('visible'),3200);}
  function status(message) {$('status').textContent=message;}
  function download(name,blob) {const u=URL.createObjectURL(blob);const a=document.createElement('a');a.href=u;a.download=name;document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(u),30000);}
  function saveCanvas(c,name) {c.toBlob(blob=>blob?download(name,blob):notify('이미지를 저장하지 못했어요.'),'image/png');}

  function trimAlpha(im) {
    const maximum=1200,s=Math.min(1,maximum/Math.max(im.width,im.height));
    const c=surface(Math.max(1,Math.round(im.width*s)),Math.max(1,Math.round(im.height*s))),g=c.getContext('2d',{willReadFrequently:true});g.drawImage(im,0,0,c.width,c.height);
    const pixels=g.getImageData(0,0,c.width,c.height);let x0=c.width,y0=c.height,x1=-1,y1=-1,transparent=0;
    for(let y=0;y<c.height;y++)for(let x=0;x<c.width;x++){const a=pixels.data[(y*c.width+x)*4+3];if(a<24)transparent++;if(a>24){x0=Math.min(x0,x);y0=Math.min(y0,y);x1=Math.max(x1,x);y1=Math.max(y1,y);}}
    if(x1<0)throw new Error('이미지가 모두 투명해요. 다른 PNG를 골라 주세요.');
    const pad=3;x0=Math.max(0,x0-pad);y0=Math.max(0,y0-pad);x1=Math.min(c.width-1,x1+pad);y1=Math.min(c.height-1,y1+pad);
    const out=surface(x1-x0+1,y1-y0+1);out.getContext('2d').drawImage(c,x0,y0,out.width,out.height,0,0,out.width,out.height);
    return {canvas:out,hasAlpha:transparent/(c.width*c.height)>.015,crop:{left:x0,top:y0,width:out.width,height:out.height,sourceWidth:c.width,sourceHeight:c.height}};
  }
  function recolorBody(source,palette,pack) {
    const c=surface(source.width,source.height),g=c.getContext('2d',{willReadFrequently:true});g.drawImage(source,0,0);
    const pixels=g.getImageData(0,0,c.width,c.height),data=pixels.data;
    const skin=rgb(skinColors[palette.skin]),shirt=rgb(palette.shirt),shorts=rgb(palette.shorts),shoe=rgb(palette.shoes);
    for(let y=0;y<c.height;y++)for(let x=0;x<c.width;x++){
      const i=(y*c.width+x)*4;if(data[i+3]<8)continue;
      const u=x/c.width,v=y/c.height,r=data[i],gr=data[i+1],b=data[i+2];
      const inSkin=(pack.skinZones||[]).some(z=>{const [a,d,w,h]=z.rect;return u>=a&&u<=a+w&&v>=d&&v<=d+h;});
      const skinPixel=inSkin&&r>gr+7&&gr>b+3&&r>b+15&&r-gr<80&&gr-b<75;
      const blue=b>r+16&&b>gr+5,white=Math.min(r,gr,b)>125&&Math.max(r,gr,b)-Math.min(r,gr,b)<48;
      let color=null,shade=1;
      if(skinPixel){if(palette.skin!==0){color=skin;shade=clamp((r*.50+gr*.34+b*.16)/214,.47,1.14);}}
      else if(!palette.preserveArt&&v<.455&&white){color=shirt;shade=clamp((r+gr+b)/3/241,.42,1.08);}
      else if(!palette.preserveArt&&blue){color=v>.80?shoe:shorts;shade=clamp((r*.2+gr*.3+b*.5)/160,.45,1.24);}
      if(color)for(let k=0;k<3;k++)data[i+k]=clamp(Math.round(color[k]*shade),0,255);
    }
    g.putImageData(pixels,0,0);return c;
  }
  function rebuildParts(){
    if(!isReady)return;
    const preset=activeOutfit(),set=bodySets[preset.assetId||'daily']||bodySets.daily;
    joinedFace=state.neckJoin&&faceLayer?window.RunKKNeckJoin.prepare(faceLayer.canvas,state.headPivotX/100,state.headPivotY/100):null;
    if(headSprite&&eyeLayer&&hairLayer){const g=headSprite.canvas.getContext('2d');g.clearRect(0,0,LAYER_SIZE,LAYER_SIZE);g.drawImage(joinedFace?.confident?joinedFace.canvas:faceLayer.canvas,0,0);g.drawImage(eyeLayer.canvas,0,0);g.drawImage(hairLayer.canvas,0,0);}
    bodyTexture=recolorBody(set.image,{...state,preserveArt:Boolean(preset.preserveArt)},set.pack);
    if(state.neckJoin&&joinedFace?.confident)bodyTexture=window.RunKKNeckJoin.matchBodyNeck(bodyTexture,joinedFace,{...set.pack,neck:set.neckFit.neck});
    bodyMesh.setImage(bodyTexture);
  }
  function scheduleTint() {if(tintFrame!==null)cancelAnimationFrame(tintFrame);tintFrame=requestAnimationFrame(()=>{tintFrame=null;rebuildParts();scheduleSave();});}

  function poseAt(t,motion){
    if(motion==='run')return {bob:-5-Math.abs(Math.cos(t*8.8))*7,tilt:Math.sin(t*8.8)*1.1,headTilt:-Math.sin(t*8.8)*.7};
    if(motion==='wave')return {bob:Math.sin(t*2.2),tilt:Math.sin(t*2.4)*1.2,headTilt:2+Math.sin(t*2.4)*1.5};
    return {bob:Math.sin(t*2.2)*1.2,tilt:Math.sin(t*1.6)*.35,headTilt:Math.sin(t*1.3)*.4};
  }
  function drawSized(g,im,x,y,w,h,pivotX=.5,pivotY=.5,rotation=0) {g.save();g.translate(x,y);g.rotate(radians(rotation));g.drawImage(im,-w*pivotX,-h*pivotY,w,h);g.restore();}
  function drawRig(g,x,y,w,h,neck){
    const at=(u,v)=>[x+w*u,y+h*v];
    g.save();g.strokeStyle='#5caa70';g.fillStyle='#fff';g.lineWidth=2;
    const joints=[[.5,neck],[.27,.15],[.13,.34],[.08,.52],[.73,.15],[.87,.34],[.92,.52],[.34,.54],[.33,.72],[.32,.91],[.66,.54],[.67,.72],[.68,.91]];
    const lines=[[0,1],[1,2],[2,3],[0,4],[4,5],[5,6],[7,8],[8,9],[10,11],[11,12],[7,10]];
    for(const [a,b]of lines){g.beginPath();g.moveTo(...at(...joints[a]));g.lineTo(...at(...joints[b]));g.stroke();}
    for(const [u,v]of joints){g.beginPath();g.arc(...at(u,v),4,0,Math.PI*2);g.fill();g.stroke();}g.restore();
  }
  function render(g,width,height,t,{background=false,rig=false,thumbnail=false,bodyOnly=false,animate=false,motion=state.motion,view=avatarViews?.active||'front'}={}){
    g.setTransform(1,0,0,1,0,0);g.clearRect(0,0,width,height);
    if(background){g.fillStyle='#eef4e7';g.fillRect(0,0,width,height);}
    const factor=Math.min(width,height)/1024;g.save();g.translate((width-1024*factor)/2,(height-1024*factor)/2);g.scale(factor,factor);
    if(!thumbnail&&!bodyOnly&&view!=='front'&&avatarViews?.entries[view]){g.drawImage(avatarViews.entries[view].image,0,0,1024,1024);g.restore();return;}
    if(!thumbnail&&!bodyOnly&&view==='front'){
      drawFrontSource(g,animate?(eyePose!=='auto'?({open:0,half:.5,closed:1}[eyePose]):playing&&state.blinkEnabled!==false?window.RunKKEyeBlink.closureAt(blinkTime):0):0);
      g.restore();return;
    }
    g.translate(512,512);g.scale(.93,.93);g.translate(-512,-512);
    if(!headSprite||!bodyTexture||!isReady){g.restore();return;}
    const p=poseAt(t,motion),set=bodySets[activeOutfit().assetId||'daily']||bodySets.daily;
    if(!thumbnail&&!bodyOnly){g.save();g.globalAlpha=.10;g.fillStyle='#5c7252';g.beginPath();g.ellipse(512,982,93-Math.abs(p.bob)*.8,8,0,0,Math.PI*2);g.fill();g.restore();}
    g.translate(512,650+p.bob);g.rotate(radians(p.tilt));g.translate(-512,-650);
    const bodyNeck=state.neckJoin?set.neckFit.neck:set.pack.neck;
    const h=570,w=h*bodyTexture.width/bodyTexture.height,x=512-w*bodyNeck[0],y=450-h*bodyNeck[1];
    bodyMesh.draw(g,x,y,w,h,t,motion);
    if(state.mark){g.save();g.fillStyle=state.bodyPackId==='crew'?'#c5efad':'#496044';g.font='bold 14px system-ui';g.textAlign='center';g.fillText('RunKK',550,533);g.restore();}
    const pivotY=clamp(state.headPivotY/100,.3,.995),headH=444*(state.headScale/100)/pivotY,aspect=headSprite.canvas.width/headSprite.canvas.height,headW=headH*aspect;
    const connection=state.neckJoin?window.RunKKNeckJoin.placement(joinedFace,pivotY,headH,state.neckGap,state.headY,set.neckFit,h):{pivotY,y:450+state.headY};
    if(!bodyOnly){
      drawSized(g,eyeLayer?headSprite.canvas:(joinedFace?.confident?joinedFace.canvas:headSprite.canvas),512+state.headX,connection.y,headW,headH,connection.pivotX??state.headPivotX/100,connection.pivotY,p.headTilt);
    }
    if(rig)drawRig(g,x,y,w,h,bodyNeck[1]);g.restore();
  }
  function frame(now) {
    if(lastTime!==null&&playing&&!document.hidden){const delta=Math.min(.05,(now-lastTime)/1000);time+=delta*(state.speed/100);if(blinkRig&&state.blinkEnabled!==false&&(!avatarViews||avatarViews.active==='front'))blinkTime+=delta;}
    lastTime=now;if(isReady)render(ctx,canvas.width,canvas.height,time,{rig:$('showRig').checked,animate:true});requestAnimationFrame(frame);
  }
  function scheduleSave() {clearTimeout(savingTimer);savingTimer=setTimeout(persist,260);}
  function persist() {
    try{localStorage.setItem(STORAGE,JSON.stringify(state));$('autoSaveLabel').textContent='이 브라우저에 자동 저장';}
    catch{$('autoSaveLabel').textContent='파일로 설정을 저장해 주세요';}
  }
  function cleanState(candidate) {
    if(!candidate||typeof candidate!=='object'||![2,3,4,5,6,7,8].includes(candidate.version)||candidate.rigVersion!==RIG)throw new Error('이 로컬 스튜디오의 설정 파일이 아니에요.');
    const s=defaults();s.blinkEnabled=candidate.blinkEnabled!==false;
    s.neckJoin=candidate.neckJoin!==false;if(Number.isFinite(candidate.neckGap))s.neckGap=clamp(candidate.neckGap,0,26);
    for(const k of ['shirt','shorts','shoes'])if(/^#[0-9a-f]{6}$/i.test(candidate[k]||''))s[k]=candidate[k];
    if(presets.some(p=>p.id===candidate.bodyPackId))s.bodyPackId=candidate.bodyPackId;if(candidate.customBodyPack?.version===2){s.customBodyPack=validateBodyPack(candidate.customBodyPack);if(candidate.bodyPackId==='custom')s.bodyPackId='custom';}
    for(const k of ['headScale','headX','headY','speed','skin','headPivotX','headPivotY','similarity']){const v=Number(candidate[k]);if(Number.isFinite(v))s[k]=v;}
    s.headPivotX=clamp(s.headPivotX,20,80);s.headPivotY=clamp(s.headPivotY,30,99.5);s.similarity=clamp(s.similarity,10,100);s.headScale=clamp(s.headScale,65,135);s.headX=clamp(s.headX,-100,100);s.headY=clamp(s.headY,-100,100);s.speed=clamp(s.speed,50,180);s.skin=clamp(Math.round(s.skin),0,skinColors.length-1);
    s.traits=safeTraits(candidate.traits);if(['auto','short','medium','long','extra_long'].includes(candidate.hairLength))s.hairLength=candidate.hairLength;if(typeof candidate.generationId==='string'&&/^[a-f0-9-]{36}$/.test(candidate.generationId))s.generationId=candidate.generationId;s.mark=Boolean(candidate.mark);s.transparent=candidate.transparent!==false;
    if(['idle','run','wave'].includes(candidate.motion))s.motion=candidate.motion;
    for(const k of ['headData','faceData','eyesData','hairData'])if(typeof candidate[k]==='string'&&candidate[k].length<12_000_000&&/^data:image\/(png|jpeg|webp);base64,/.test(candidate[k]))s[k]=candidate[k];
    s.layerVersion=candidate.layerVersion===HEAD_LAYER_VERSION?HEAD_LAYER_VERSION:null;
    s.segmentationVersion=candidate.segmentationVersion===HEAD_SEGMENTATION_VERSION?HEAD_SEGMENTATION_VERSION:null;
    if(s.faceData&&!s.layerVersion){s.faceData=null;s.eyesData=null;s.hairData=null;s.headData=null;s.generationId=null;}
    if(s.headData||s.faceData||s.eyesData||s.hairData)s.headName=String(candidate.headName||'불러온 머리').slice(0,70);
    return s;
  }
  async function setLayers(faceSrc,eyesSrc,hairSrc,name='기본 머리') {
    const [face,eyes,hair]=await Promise.all([loadImage(faceSrc),loadImage(eyesSrc),loadImage(hairSrc)]);
    if([face,eyes,hair].some(im=>im.width*im.height>30_000_000))throw new Error('이미지가 너무 커요. 30MP 이하 이미지를 골라 주세요.');
    if([eyes,hair].some(im=>im.width!==face.width||im.height!==face.height))throw new Error('세 레이어의 캔버스가 달라요. 다시 생성해 주세요.');
    const sharedScale=Math.min(LAYER_SIZE/face.width,LAYER_SIZE/face.height),w=face.width*sharedScale,h=face.height*sharedScale;
    const fitCanvas=(im)=>{const out=surface(LAYER_SIZE,LAYER_SIZE);out.getContext('2d').drawImage(im,(LAYER_SIZE-w)/2,(LAYER_SIZE-h)/2,w,h);return out;};
    const faceCanvas=fitCanvas(face),eyeCanvas=fitCanvas(eyes),hairCanvas=fitCanvas(hair);
    const composite=surface(LAYER_SIZE,LAYER_SIZE),cg=composite.getContext('2d');cg.drawImage(faceCanvas,0,0);cg.drawImage(eyeCanvas,0,0);cg.drawImage(hairCanvas,0,0);
    faceLayer={canvas:faceCanvas};
    eyeLayer={canvas:eyeCanvas};
    hairLayer={canvas:hairCanvas};
    let templates=null;
    try{templates=await window.RunKKEyeBlink.loadTemplates(loadImage);}catch(error){console.warn('눈 프레임 로드 실패',error);notify('눈 프레임을 불러오지 못했어요. 새로고침해 주세요.');}
    blinkRig=window.RunKKEyeBlink.prepare(faceCanvas,eyeCanvas,templates);blinkTime=0;eyePose='auto';
    cg.clearRect(0,0,LAYER_SIZE,LAYER_SIZE);cg.drawImage(faceCanvas,0,0);cg.drawImage(eyeCanvas,0,0);cg.drawImage(hairCanvas,0,0);
    $('eyeFramePreviews').hidden=!blinkRig;
    if(blinkRig)for(const name of ['open','half','closed'])$('eyeFrame-'+name).src=blinkRig.frames[name].toDataURL('image/png');
    $('layerPreviewBlock').hidden=false;$('layerPreviews').hidden=false;for(const [id,c]of [['faceLayerPreview',faceCanvas],['eyesLayerPreview',eyeCanvas],['hairLayerPreview',hairCanvas]])$(id).src=c.toDataURL('image/png');
    const merged={canvas:composite,hasAlpha:true,crop:{left:0,top:0,width:LAYER_SIZE,height:LAYER_SIZE,sourceWidth:LAYER_SIZE,sourceHeight:LAYER_SIZE}};
    headSprite=merged;$('headPreview').src=(name.startsWith('AI 생성')&&state.headData)||composite.toDataURL('image/png');$('headBadge').textContent=name;$('headTag').textContent='얼굴 + 눈 + 머리카락 분리 레이어';
    return {canvas:composite,trimmed:merged};
  }
  async function setHead(src,name='기본 머리') {
    $('layerPreviewBlock').hidden=true;$('layerPreviews').hidden=true;$('eyeFramePreviews').hidden=true;blinkRig=null;blinkTime=0;eyePose='auto';
    const im=await loadImage(src);if(im.width*im.height>30_000_000)throw new Error('이미지가 너무 커요. 30MP 이하 이미지를 골라 주세요.');
    const result=trimAlpha(im);headSprite=result;faceLayer={canvas:result.canvas};eyeLayer=null;hairLayer=null;$('headPreview').src=result.canvas.toDataURL('image/png');$('headBadge').textContent=name;$('headTag').textContent='같은 머리 · 공통 동작';
    return result;
  }
  function syncControls() {
    $('blinkEnabled').checked=state.blinkEnabled!==false;$('eyePose').value=eyePose;
    for(const k of ['headScale','headX','headY','speed','headPivotX','headPivotY','similarity'])$(k).value=state[k];
    $('headScaleValue').textContent=state.headScale+'%';$('headXValue').textContent=state.headX;$('headYValue').textContent=state.headY;
    $('neckJoin').checked=state.neckJoin;$('neckGap').value=state.neckGap;$('neckGapValue').textContent=state.neckGap+'px';
    $('neckJoinHint').textContent=state.neckJoin?(joinedFace?.confident?'실제 목·옷깃 자동 정렬 · 0px에서도 기본 목 길이 유지':'목을 확실히 찾지 못해 원본 연결을 유지해요.'):'원본 목 연결';
    $('alignLabel').textContent=state.headScale===100&&state.headX===0&&state.headY===0?'기본값':'사용자 설정';
    $('headPivotXValue').textContent=state.headPivotX.toFixed(1)+'%';$('headPivotYValue').textContent=state.headPivotY.toFixed(1)+'%';$('similarityValue').textContent=state.similarity;$('hairLength').value=state.hairLength;$('speedValue').textContent=(state.speed/100).toFixed(1)+'×';$('transparentExport').checked=state.transparent;
    $('shirtColor').value=state.shirt;$('shortColor').value=state.shorts;$('shoeColor').value=state.shoes;$('crewMark').checked=state.mark;
    document.querySelectorAll('[data-motion]').forEach(b=>{b.classList.toggle('active',b.dataset.motion===state.motion);b.setAttribute('aria-pressed',String(b.dataset.motion===state.motion));});
    document.querySelectorAll('[data-outfit]').forEach(b=>{const active=b.dataset.outfit===state.bodyPackId;b.classList.toggle('active',active);b.setAttribute('aria-pressed',String(active));b.querySelector('.outfit-check').textContent=active?'✓':'';});
    document.querySelectorAll('[data-skin]').forEach(b=>{const active=Number(b.dataset.skin)===state.skin;b.classList.toggle('active',active);b.setAttribute('aria-pressed',String(active));});
    const outfit=activeOutfit();$('outfitTag').textContent=outfit.name;for(const id of ['shirtColor','shortColor','shoeColor'])$(id).disabled=Boolean(outfit.preserveArt);$('paletteHint').textContent=outfit.preserveArt?'파츠의 원본 디자인과 색상을 유지하는 의상이에요.':'이 러닝복은 상의·하의·신발 색상을 바꿀 수 있어요.';$('removeBodyPack').hidden=!state.customBodyPack;
    $('poseLabel').textContent=motionNames[state.motion];$('playPause').textContent=playing?'Ⅱ':'▶';$('playPause').setAttribute('aria-label',playing?'애니메이션 일시정지':'애니메이션 재생');$('playState').textContent=playing?'재생 중':'일시정지';
    syncViewControls();
  }
  function allOutfits(){return state.customBodyPack?[...presets,{id:'custom',assetId:'custom',name:state.customBodyPack.name,desc:'내 몸 세트 · '+state.customBodyPack.description,...state.customBodyPack.palette,preserveArt:state.customBodyPack.preserveArt,mark:false}]:presets;}
  function activeOutfit(){return allOutfits().find(p=>p.id===state.bodyPackId)||presets[0];}
  function chooseOutfit(id){const p=allOutfits().find(p=>p.id===id);if(!p)return;Object.assign(state,{bodyPackId:p.id,shirt:p.shirt,shorts:p.shorts,shoes:p.shoes,mark:p.mark});rebuildParts();syncControls();scheduleSave();status(p.name+' 적용 · 머리 이미지와 목 기준점은 유지됐어요.');}
  function makePresetCards() {
    $('outfitList').replaceChildren();
    for(const p of allOutfits()){const b=document.createElement('button');b.type='button';b.className='outfit-card';b.dataset.outfit=p.id;
      b.innerHTML='<span class="outfit-icon"><canvas width="160" height="174"></canvas></span><span><span class="outfit-name"></span><span class="outfit-desc"></span><span class="outfit-colors"><i></i><i></i><i></i></span></span><span class="outfit-check"></span>';
      b.querySelector('.outfit-name').textContent=p.name;b.querySelector('.outfit-desc').textContent=p.desc;
      b.querySelectorAll('.outfit-colors i').forEach((el,i)=>el.style.background=[p.shirt,p.shorts,p.shoes][i]);b.onclick=()=>chooseOutfit(p.id);$('outfitList').append(b);
    }
    if(!$('skinPalette').childElementCount)skinColors.forEach((color,index)=>{const b=document.createElement('button');b.type='button';b.dataset.skin=index;b.style.background=color;b.setAttribute('aria-label','피부 톤 '+(index+1));b.onclick=()=>{state.skin=index;rebuildParts();syncControls();scheduleSave();};$('skinPalette').append(b);});
    drawPresetThumbnails();
  }
  function drawPresetThumbnails(){
    document.querySelectorAll('[data-outfit]').forEach(button=>{
      const preset=allOutfits().find(p=>p.id===button.dataset.outfit),set=bodySets[preset.assetId||'daily'];if(!set)return;
      const c=button.querySelector('canvas'),g=c.getContext('2d'),im=recolorBody(set.image,{...state,...preset,skin:0},set.pack);
      const h=158,w=h*im.width/im.height;g.clearRect(0,0,c.width,c.height);g.drawImage(im,(c.width-w)/2,8,w,h);
    });
  }
  async function fileData(file) {
    if(!file)throw new Error('파일을 선택해 주세요.');if(file.size>10*1024*1024)throw new Error('이미지는 10MB 이하로 골라 주세요.');
    return new Promise((resolve,reject)=>{const r=new FileReader();r.onload=()=>resolve(r.result);r.onerror=()=>reject(new Error('파일을 읽지 못했어요.'));r.readAsDataURL(file);});
  }
  function bind() {
    $('headFile').onchange=async e=>{const file=e.target.files[0];if(!file)return;try{const result=await setHead(await fileData(file),'내 머리 PNG');state.headData=result.canvas.toDataURL('image/png');state.faceData=null;state.eyesData=null;state.hairData=null;state.headName=file.name;state.headPivotX=50;state.headPivotY=98.5;state.generationId=null;rebuildParts();syncControls();scheduleSave();status('새 머리를 연결했어요. 크기·좌우·위아래를 조절해 목 위치를 맞춰 주세요.');if(!result.hasAlpha)notify('배경이 있는 이미지예요. 투명한 머리 PNG를 사용하면 자연스러워요.');}catch(err){notify(err.message);}finally{e.target.value='';}};
    $('defaultHead').onclick=async()=>{try{await setLayers('/assets/face-base-default.png','/assets/eyes-default.png','/assets/hair-default.png');state.headData=null;state.faceData=null;state.eyesData=null;state.hairData=null;state.headName='기본 머리';state.headPivotX=DEFAULT_PIVOT.x;state.headPivotY=DEFAULT_PIVOT.y;state.generationId=null;state.headScale=100;state.headX=0;state.headY=0;rebuildParts();syncControls();scheduleSave();status('기본 얼굴·눈·헤어를 같은 좌표로 연결했어요.');}catch(err){notify(err.message);}};
    $('exportHead').onclick=async()=>{try{const blob=await captureFront();download('RunKK_head_with_neck.png',blob);}catch(error){notify(error.message);}};
    for(const [id,layer]of [['exportFace',()=>faceLayer],['exportEyes',()=>eyeLayer],['exportHair',()=>hairLayer]])$(id).onclick=()=>{const item=layer();if(item)saveCanvas(item.canvas,`RunKK_${id.slice(6).toLowerCase()}.png`);else notify('셀카로 세 레이어를 먼저 생성해 주세요.');};
    $('selfieFile').onchange=async e=>{try{const file=e.target.files[0];if(!file)return;if(file.size>8*1024*1024)throw new Error('셀카는 8MB 이하로 골라 주세요.');if(!['image/png','image/jpeg','image/webp'].includes(file.type))throw new Error('JPG, PNG, WEBP 사진을 골라 주세요.');selfieFile=file;selfieQuality=null;selfieQualityError=null;state.traits=null;renderTraits(null);syncAiButtons();if(selfieUrl)URL.revokeObjectURL(selfieUrl);selfieUrl=URL.createObjectURL(file);$('selfiePreview').src=selfieUrl;$('selfiePreview').hidden=false;$('removeSelfie').hidden=false;status('셀카를 선택했어요. 먼저 눈·코·입과 얼굴 밝기를 확인해요.');$('aiMessage').textContent='분석·생성 버튼을 누르면 셀카가 GMS를 통해 AI에 전송돼요. 사진 품질을 확인한 뒤에만 이미지를 생성해요.';}catch(err){notify(err.message);}finally{e.target.value='';}};
    $('removeSelfie').onclick=()=>{if(selfieUrl)URL.revokeObjectURL(selfieUrl);selfieUrl=null;selfieFile=null;selfieQuality=null;selfieQualityError=null;syncAiButtons();$('selfiePreview').removeAttribute('src');$('selfiePreview').hidden=true;$('removeSelfie').hidden=true;};
    for(const k of ['headScale','headX','headY','speed','headPivotX','headPivotY','similarity'])$(k).oninput=e=>{state[k]=Number(e.target.value);if(k==='headPivotX'||k==='headPivotY')rebuildParts();syncControls();scheduleSave();};
    $('neckJoin').onchange=e=>{state.neckJoin=e.target.checked;rebuildParts();syncControls();scheduleSave();};
    $('neckGap').oninput=e=>{state.neckGap=Number(e.target.value);syncControls();scheduleSave();};
    $('resetAlignment').onclick=()=>{Object.assign(state,{headScale:100,headX:0,headY:0,neckJoin:true,neckGap:10});rebuildParts();syncControls();scheduleSave();};
    document.querySelectorAll('[data-motion]').forEach(b=>b.onclick=()=>{state.motion=b.dataset.motion;time=0;syncControls();scheduleSave();status(motionNames[state.motion]+' · 연결된 몸 이미지 전체가 부드럽게 움직여요.');});
    $('playPause').onclick=()=>{playing=!playing;blinkTime=0;syncControls();};
    $('blinkEnabled').onchange=()=>{state.blinkEnabled=$('blinkEnabled').checked;blinkTime=0;eyePose='auto';syncControls();scheduleSave();};
    $('eyePose').onchange=()=>{eyePose=$('eyePose').value;blinkTime=0;};
    for(const name of ['open','half','closed'])$('saveEyeFrame-'+name).onclick=()=>{if(blinkRig)saveCanvas(blinkRig.frames[name],`RunKK_eyes_${name}.png`);};
    $('transparentExport').onchange=e=>{state.transparent=e.target.checked;scheduleSave();};
    for(const [id,key]of [['shirtColor','shirt'],['shortColor','shorts'],['shoeColor','shoes']])$(id).oninput=e=>{state[key]=e.target.value;scheduleTint();};
    $('crewMark').onchange=e=>{state.mark=e.target.checked;scheduleSave();};
    $('savePng').onclick=()=>{if(!isReady)return;const out=surface(1024,1024);render(out.getContext('2d'),1024,1024,time,{background:!state.transparent});const view=avatarViews?.active||'front';saveCanvas(out,'RunKK_avatar_'+(view==='front'?state.motion:view)+'.png');notify(view==='front'?'현재 동작의 한 장면을 PNG로 저장해요.':'현재 옆모습을 PNG로 저장해요.');};
    $('saveProject').onclick=()=>{download('RunKK_avatar_settings.json',new Blob([JSON.stringify(state,null,2)],{type:'application/json'}));notify('머리·몸 세트와 설정을 JSON으로 저장해요.');};
    $('loadProject').onclick=()=>$('projectFile').click();
    $('projectFile').onchange=async e=>{const file=e.target.files[0];if(!file)return;try{if(file.size>24*1024*1024)throw new Error('설정 파일은 24MB 이하로 골라 주세요.');const next=cleanState(JSON.parse(await file.text()));const prepared=next.customBodyPack?await prepareBodyPack(next.customBodyPack):null;state=next;if(prepared)bodySets.custom=prepared;else delete bodySets.custom;if(state.faceData&&state.eyesData&&state.hairData)await setLayers(state.faceData,state.eyesData,state.hairData,state.headName);else await setHead(state.headData||ASSETS.head,state.generationId?'AI 생성 머리':state.headData?'내 머리 PNG':'기본 머리');makePresetCards();renderTraits(state.traits);time=0;rebuildParts();syncControls();scheduleSave();notify('저장한 아바타 설정을 불러왔어요.');}catch(err){notify('불러오기 실패: '+err.message);}finally{e.target.value='';}};
    $('resetAll').onclick=async()=>{state=defaults();state.headPivotX=DEFAULT_PIVOT.x;state.headPivotY=DEFAULT_PIVOT.y;delete bodySets.custom;makePresetCards();await setLayers('/assets/face-base-default.png','/assets/eyes-default.png','/assets/hair-default.png');time=0;playing=true;$('showRig').checked=false;$('removeSelfie').click();rebuildParts();syncControls();scheduleSave();renderTraits(null);notify('기본 아바타로 초기화했어요.');};
    const help=()=>{if(!$('helpDialog').open)$('helpDialog').showModal();};$('helpButton').onclick=help;$('closeHelp').onclick=()=>$('helpDialog').close();$('helpDone').onclick=()=>$('helpDialog').close();$('helpDialog').onclick=e=>{if(e.target===$('helpDialog')){const r=e.target.getBoundingClientRect();if(e.clientX<r.left||e.clientX>r.right||e.clientY<r.top||e.clientY>r.bottom)e.target.close();}};
  }
  const TRAIT_LABELS={hair:'헤어',eyes:'눈',eyebrows:'눈썹',nose:'코',mouth:'입',face:'얼굴형',skin_tone:'피부 톤',accessories:'안경 / 액세서리'};
  function safeTraits(value) {
    if(!value||typeof value!=='object'||Array.isArray(value))return null;
    const t=Object.fromEntries(Object.keys(TRAIT_LABELS).map(k=>[k,String(value[k]||'').slice(0,180)]));
    t.hair_length=['short','medium','long','extra_long','unknown'].includes(value.hair_length)?value.hair_length:'unknown';return t;
  }
  function renderTraits(traits) {
    const box=$('traitsFields');box.replaceChildren();
    for(const [key,label]of Object.entries(TRAIT_LABELS)){const l=document.createElement('label');l.textContent=label;const input=document.createElement('textarea');input.dataset.trait=key;input.maxLength=180;input.value=traits?.[key]||'';input.placeholder='분석 후 표시돼요';input.oninput=()=>{state.traits=readTraits();scheduleSave();};l.append(input);box.append(l);}
  }
  function readTraits(){const t={};document.querySelectorAll('[data-trait]').forEach(el=>t[el.dataset.trait]=el.value);t.hair_length=state.traits?.hair_length||'unknown';return safeTraits(t);}
  function syncAiButtons(){for(const id of ['analyzeButton','generateButton'])$(id).disabled=aiBusy||!apiReady||!selfieFile;$('generateButton').disabled=$('generateButton').disabled||selfieQuality?.canGenerate===false;$('generateButton').title=selfieQuality?.canGenerate===false?'사진 품질을 확인하기 어려워요. 새 셀카를 선택해 주세요.':'';$('selfieFile').disabled=aiBusy;$('headFile').disabled=aiBusy;$('removeSelfie').disabled=aiBusy;$('defaultHead').disabled=aiBusy;$('resetAll').disabled=aiBusy;$('loadProject').disabled=aiBusy;$('loadLatestHead').disabled=aiBusy;$('resplitLatestHead').hidden=!testMode;$('resplitLatestHead').disabled=aiBusy||!testMode;$('generateBody').disabled=aiBusy||!apiReady;for(const id of ['topPart','bottomPart','shoesPart'])$(id).disabled=aiBusy||!apiReady;$('bodyPackFile').disabled=aiBusy;window.RunKKSelfieQuality.render($('selfieQuality'),{quality:selfieQuality,selected:Boolean(selfieFile),busy:aiBusy&&Boolean(selfieFile)&&!selfieQuality,error:selfieQualityError});syncViewControls();}
  async function apiRequest(url,options={}){
    if(location.protocol==='file:')throw new Error('npm start로 서버를 실행하고 http://localhost:3000에서 열어 주세요.');
    const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),url==='/api/generate'?900000:360000);
    try{const res=await fetch(url,{...options,signal:controller.signal,cache:'no-store'});let data;try{data=await res.json();}catch{throw new Error('서버 응답을 읽지 못했어요. 새 server.mjs로 서버를 다시 실행해 주세요.');}if(!res.ok)throw Object.assign(new Error(data.message||data.error||'요청 실패'),{quality:data.quality,code:data.code,status:res.status});return data;}catch(error){if(error.name==='AbortError')throw new Error('응답 대기 시간이 초과됐어요. 자동 재시도하지 않았어요. 최근 생성 불러오기를 확인해 주세요.');throw error;}finally{clearTimeout(timer);}
  }
  function frontSignature(){return JSON.stringify([state.generationId,state.headData,state.faceData,state.eyesData,state.hairData,HEAD_LAYER_VERSION,HEAD_SEGMENTATION_VERSION]);}
  function syncViewControls(){
    if(!avatarViews)return;avatarViews.setSource(frontSignature());const view=avatarViews.active,side=view!=='front';
    $('blinkEnabled').disabled=side||!blinkRig;$('eyePose').disabled=side||!blinkRig;
    for(const name of ['open','half','closed'])$('saveEyeFrame-'+name).disabled=!blinkRig;
    $('blinkEnabled').title=!blinkRig?'분리된 두 눈 레이어가 필요해요.':'정면 미리보기에서 눈을 깜빡여요.';
    document.querySelectorAll('[data-avatar-view]').forEach(b=>{const direction=b.dataset.avatarView;b.disabled=!isReady||aiBusy||avatarViews.busy||(direction!=='front'&&!avatarViews.entries[direction]);b.setAttribute('aria-pressed',String(direction===view));});
    $('generateViewRight').disabled=!isReady||!apiReady||aiBusy||avatarViews.busy;
    $('restoreViews').disabled=!isReady||aiBusy||avatarViews.busy;
    $('saveFrontFace').disabled=!isReady||!headSprite;
    $('saveRightFace').disabled=!avatarViews.entries.right;
    const right=avatarViews.entries.right;
    $('rightLayerPreviews').hidden=!side||!right?.rightFaceImage;
    for(const part of ['Face','Eyes','Hair']){const src=right?.['right'+part+'Image'];const preview=$('right'+part+'Preview');if(src&&preview.src!==src)preview.src=src;$('saveRight'+part+'Layer').disabled=!src;}
    document.querySelectorAll('[data-motion]').forEach(b=>b.disabled=side);for(const id of ['playPause','speed','showRig'])$(id).disabled=side;
    if(side){$('poseLabel').textContent='우측 얼굴';$('playState').textContent='정적 머리 PNG';$('headTag').textContent='정면 머리 기준 · 몸 미포함';}
    else{$('headTag').textContent=eyeLayer?'얼굴 + 눈 + 머리카락 분리 레이어':'같은 머리 · 공통 동작';}
  }
  function drawFrontSource(g,closure=0){if(eyeLayer&&hairLayer){if(blinkRig&&closure>0)window.RunKKEyeBlink.draw(g,blinkRig,closure);else{g.drawImage(faceLayer.canvas,0,0,1024,1024);g.drawImage(eyeLayer.canvas,0,0,1024,1024);}g.drawImage(hairLayer.canvas,0,0,1024,1024);}else if(headSprite)g.drawImage(headSprite.canvas,0,0,1024,1024);}
  function captureFront(){const out=surface(1024,1024),g=out.getContext('2d');drawFrontSource(g);return new Promise((resolve,reject)=>out.toBlob(blob=>blob?resolve(blob):reject(new Error('정면 머리 이미지를 만들지 못했어요.')),'image/png'));}
  async function restoreAvatarViews(announce=true){
    if(!avatarViews||aiBusy||!isReady)return;aiBusy=true;syncAiButtons();
    try{const found=await avatarViews.restore();if(announce)$('avatarViewMessage').textContent=found.length?'현재 머리에 저장된 우측 얼굴을 불러왔어요. AI는 호출하지 않았어요.':'현재 머리에 저장된 우측 얼굴이 없어요. 우측 얼굴 생성 버튼을 눌러 주세요.';}
    catch(error){if(announce){$('avatarViewMessage').textContent=error.message;notify(error.message);}}finally{aiBusy=false;syncAiButtons();syncControls();}
  }
  function bindAvatarViews(){
    avatarViews=new window.RunKKAvatarViews({capture:captureFront,request:apiRequest,decode:loadImage,signature:frontSignature,update:()=>{syncAiButtons();syncControls();}});
    document.querySelectorAll('[data-avatar-view]').forEach(b=>b.onclick=()=>{if(avatarViews.select(b.dataset.avatarView)){syncControls();status(avatarViews.active==='front'?'정면 아바타로 돌아왔어요.': 'AI가 추정한 옆모습이에요. 정면 파츠와 코디는 그대로 유지돼요.');}});
    for(const direction of ['right'])$('generateViewRight').onclick=async()=>{
      if(aiBusy||!isReady)return;aiBusy=true;syncAiButtons();$('avatarViewMessage').textContent='정면 머리를 기준으로 우측 얼굴·헤어만 생성하고 있어요. 몸과 코디는 보내지 않아요.';
      try{const result=await avatarViews.generate(direction);$('avatarViewMessage').textContent=result.warning||(result.cached?'저장된 우측 머리를 재사용했어요. AI를 다시 호출하지 않았어요.':'우측 얼굴을 만들었어요. 현재 모습 PNG 저장으로 보관할 수 있어요.');status('우측 얼굴·헤어 · 몸 없는 정적 PNG');}
      catch(error){$('avatarViewMessage').textContent=error.message;notify(error.message);}finally{aiBusy=false;syncAiButtons();syncControls();}
    };
    $('restoreViews').onclick=()=>restoreAvatarViews(true);
    $('saveFrontFace').onclick=async()=>{try{const blob=await captureFront();download('RunKK_front_face_with_neck.png',blob);}catch(error){notify(error.message);}};
    $('saveRightFace').onclick=()=>{const image=avatarViews.entries.right?.image;if(!image)return;const out=surface(1024,1024);out.getContext('2d').drawImage(image,0,0,1024,1024);saveCanvas(out,'RunKK_right_face.png');};
    for(const part of ['Face','Eyes','Hair'])$('saveRight'+part+'Layer').onclick=async()=>{try{const src=avatarViews.entries.right?.['right'+part+'Image'];if(!src)return;const image=await loadImage(src),out=surface(1024,1024);out.getContext('2d').drawImage(image,0,0);saveCanvas(out,'RunKK_right_'+part.toLowerCase()+'_layer.png');}catch(error){notify(error.message);}};
  }
  async function refreshApi(){
    try{const h=await apiRequest('/api/health');testMode=h.testMode===true;apiReady=Boolean(h.ok&&h.configured&&h.mode==='local-server-ai');$('apiState').textContent=apiReady?'GMS 로컬 서버 준비 · API 키 설정됨':'.env에 GMS_KEY를 설정하고 서버를 다시 실행해 주세요.';$('apiState').classList.toggle('error',!apiReady);$('apiState').title='키 설정 여부만 확인해요. 실제 모델 접근은 코디 생성 요청 시 확인돼요.';}catch(error){apiReady=false;testMode=false;$('apiState').textContent=error.message;$('apiState').classList.add('error');}syncAiButtons();
  }
  function selfieForm(){if(!selfieFile)throw new Error('셀카 사진을 먼저 선택해 주세요.');const form=new FormData();form.append('selfie',selfieFile,selfieFile.name);form.append('userId',demoUserId);return form;}
  async function applyAiHead(data){
    const hasLayers=data.faceImage&&data.eyesImage&&data.hairImage;
    if(hasLayers&&data.styleVersion!==HEAD_LAYER_VERSION)throw new Error('이전 생성 결과는 코·입과 정렬 정보가 없어요. 셀카로 다시 생성해 주세요.');
    const glassesPresent=data.glassesPresent===true||data.regions?.features?.glassesPresent===true||data.rightProfile?.glassesPresent===true;
    $('hairLayerCaption').textContent=glassesPresent?'헤어 · 눈썹 · 안경':'헤어 · 눈썹';$('rightHairLayerCaption').textContent=glassesPresent?'우측 헤어 · 눈썹 · 안경':'우측 헤어 · 눈썹';$('saveRightHairLayer').textContent=glassesPresent?'우측 헤어 · 눈썹 · 안경 PNG ↓':'우측 헤어 · 눈썹 PNG ↓';
    if(hasLayers)state.headData=data.frontImage||null;const normalized=hasLayers?await setLayers(data.faceImage,data.eyesImage,data.hairImage,'AI 생성 얼굴 + 눈 + 머리카락'):await setHead(data.frontImage||data.image,'AI 생성 머리');const crop=normalized.trimmed?.crop||normalized.crop;const anchor=data.alignment||{};
    const px=Number.isFinite(anchor.pivotX)?anchor.pivotX:.5,py=Number.isFinite(anchor.pivotY)?anchor.pivotY:.985;
    state.headData=hasLayers?(data.frontImage||null):normalized.canvas.toDataURL('image/png');state.faceData=hasLayers?data.faceImage:null;state.eyesData=hasLayers?data.eyesImage:null;state.hairData=hasLayers?data.hairImage:null;state.headName=hasLayers?'AI 생성 얼굴 + 눈 + 머리카락':'AI 생성 머리';state.headScale=100;state.headX=0;state.headY=0;
    state.headPivotX=clamp((px*crop.sourceWidth-crop.left)/crop.width*100,20,80);state.headPivotY=clamp((py*crop.sourceHeight-crop.top)/crop.height*100,30,99.5);
    state.layerVersion=hasLayers?HEAD_LAYER_VERSION:null;state.segmentationVersion=hasLayers?data.segmentationVersion:null;state.generationId=data.headId||data.id||null;state.traits=safeTraits(data.traits);if(Number.isInteger(anchor.skin))state.skin=clamp(anchor.skin,0,skinColors.length-1);
    if(avatarViews&&data.rightImage){const image=await loadImage(data.rightImage);avatarViews.setSource(frontSignature());avatarViews.entries.right={image,sourceHash:data.rightProfile?.sourceHash,viewId:data.rightProfile?.viewId,rightFaceImage:data.rightFaceImage,rightEyesImage:data.rightEyesImage,rightHairImage:data.rightHairImage};}
    renderTraits(state.traits);rebuildParts();syncControls();persist();
  }
  function bindAi(){
    window.RunKKSelfieQuality.render($('selfieQuality'));
    $('refreshApi').onclick=refreshApi;$('hairLength').onchange=e=>{state.hairLength=e.target.value;scheduleSave();};
    $('analyzeButton').onclick=async()=>{try{const form=selfieForm();aiBusy=true;selfieQuality=null;selfieQualityError=null;syncAiButtons();$('analyzeButton').textContent='분석 중…';$('aiMessage').textContent='눈·코·입 가시성과 얼굴 밝기·선명도를 함께 확인하고 있어요.';const data=await apiRequest('/api/analyze',{method:'POST',body:form});if(!data.quality)throw new Error('품질 검사 서버를 새로 실행해 주세요.');selfieQuality=data.quality;state.traits=safeTraits(data.traits);renderTraits(state.traits);scheduleSave();$('aiMessage').textContent=data.quality.message;if(!data.quality.canGenerate)notify('사진 품질 확인 · 새 셀카를 선택해 주세요.');}catch(error){selfieQualityError=error.message;$('aiMessage').textContent=error.message;notify(error.message);}finally{aiBusy=false;$('analyzeButton').textContent='특징 분석';syncAiButtons();}};
    $('generateButton').onclick=async()=>{
      let previewTimer=null,previewShown=false;
      try{const form=selfieForm(),traits=readTraits();if(traits?.hair.trim()&&traits?.eyes.trim())form.append('traits',JSON.stringify(traits));form.append('hairLength',state.hairLength);form.append('similarity',String(state.similarity));
        aiBusy=true;selfieQualityError=null;syncAiButtons();$('generateButton').classList.add('busy');$('generateButton').textContent='품질 확인 · 생성 중…';$('aiMessage').textContent='셀카 특징 분석 → 완성 정면 생성 → 우측 얼굴 생성 → 정면 파츠 분리 → 우측 파츠 분리 순서로 진행해요.';status('사진 품질을 확인하고 있어요. 특징이 보이지 않으면 이미지를 생성하지 않아요.');
        previewTimer=setInterval(async()=>{if(previewShown)return;try{const response=await fetch('/api/generate-preview?userId='+encodeURIComponent(demoUserId),{cache:'no-store'});if(response.status!==200)return;const preview=await response.json();if(preview.frontImage?.startsWith('data:image/png;base64,')){previewShown=true;$('headPreview').src=preview.frontImage;$('headBadge').textContent='AI 생성 원본';$('aiMessage').textContent='완성 머리를 만들었어요. AI가 얼굴·눈·헤어 파츠를 생성하는 중이에요.';}}catch{}},1000);
        const data=await apiRequest('/api/generate',{method:'POST',body:form});selfieQuality=data.quality||null;await applyAiHead(data);$('aiMessage').textContent=data.warning||'얼굴·눈·헤어 레이어를 만들었어요. 옷이나 동작을 바꿔도 각각의 레이어가 유지돼요.';status('AI 얼굴·눈·헤어 연결 완료 · 의상과 동작을 바꿔 보세요.');notify('내 얼굴·눈·헤어를 각각 연결했어요.');
      }catch(error){if(previewShown)$('headBadge').textContent='AI 생성 원본 · 파츠 확인 필요';if(error.quality)selfieQuality=error.quality;if(error.code==='SELFIE_QUALITY_UNAVAILABLE')selfieQualityError=error.message;$('aiMessage').textContent=error.message;status(error.code==='SELFIE_QUALITY_REJECTED'?'사진 품질 확인이 필요해요. 이미지를 생성하지 않았고 기존 아바타는 유지돼요.':'파츠 연결에 실패했어요. 생성 원본은 위에서 확인할 수 있어요.');notify(error.message);}finally{if(previewTimer)clearInterval(previewTimer);aiBusy=false;$('generateButton').classList.remove('busy');$('generateButton').textContent='AI 머리 생성 ↗';syncAiButtons();}
    };
    $('loadLatestHead').onclick=async()=>{try{const data=await apiRequest('/api/heads/latest?userId='+encodeURIComponent(demoUserId));await applyAiHead(data);$('aiMessage').textContent='이 사용자의 최근 머리를 불러왔어요. AI를 다시 호출하지 않았어요.';notify('최근 AI 머리를 연결했어요.');}catch(error){notify(error.message);}};
    $('resplitLatestHead').onclick=async()=>{if(!testMode||aiBusy)return;aiBusy=true;syncAiButtons();try{const data=await apiRequest('/api/heads/latest?userId='+encodeURIComponent(demoUserId)+'&resplit=true');await applyAiHead(data);$('aiMessage').textContent='저장된 완성 얼굴은 재사용하고, 정면·우측 파츠만 다시 추출했어요.';status('저장된 완성 얼굴 재사용 · 파츠 추출 중');notify('저장된 완성 얼굴에서 파츠를 다시 추출했어요.');}catch(error){notify(error.message);}finally{aiBusy=false;syncAiButtons();}};
  }
  function validateBodyPack(value){
    if(value?.version===1)throw new Error('이전 분리 파츠 몸은 사용할 수 없어요. 새 ZIP의 body-packs 폴더에서 v2 몸 세트를 선택해 주세요.');
    if(!value||value.format!=='runkk-body-pack'||value.version!==2||value.rigVersion!==RIG||value.renderer!=='seamless-mesh-v1')throw new Error('새 RunKK 몸 세트 JSON 또는 머리 없는 몸 PNG를 선택해 주세요.');
    if(typeof value.image!=='string'||value.image.length>16_000_000||!/^data:image\/(png|webp);base64,/.test(value.image))throw new Error('몸 세트에 투명 PNG가 필요해요.');
    const n=value.neck;if(!Array.isArray(n)||n.length!==2||!n.every(Number.isFinite)||n[0]<.3||n[0]>.7||n[1]<0||n[1]>.25)throw new Error('몸의 목 기준 좌표를 확인해 주세요.');
    const palette={shirt:'#f5f3f1',shorts:'#7198da',shoes:'#7198da'};
    for(const k of Object.keys(palette))if(/^#[a-f0-9]{6}$/i.test(value.palette?.[k]||''))palette[k]=value.palette[k];
    const skinZones=(Array.isArray(value.skinZones)?value.skinZones:[]).slice(0,12).filter(z=>Array.isArray(z?.rect)&&z.rect.length===4&&z.rect.every(n=>Number.isFinite(n)&&n>=0&&n<=1)).map(z=>({rect:[...z.rect]}));
    return {format:'runkk-body-pack',version:2,rigVersion:RIG,renderer:'seamless-mesh-v1',id:String(value.id||'custom').replace(/[^a-z0-9_-]/gi,'').slice(0,45)||'custom',name:String(value.name||'나의 러닝 룩').slice(0,40),description:String(value.description||'').slice(0,80),image:value.image,neck:[...n],skinZones,palette,preserveArt:value.preserveArt!==false};
  }
  async function packImage(src){
    if(!imageCache.has(src))imageCache.set(src,loadImage(src));
    const im=await imageCache.get(src);if(im.width>4096||im.height>4096||im.width*im.height>16_777_216)throw new Error('몸 이미지는 4096px 이하를 사용해 주세요.');return im;
  }
  async function prepareBodyPack(raw){
    const pack=validateBodyPack(raw),im=await packImage(pack.image);
    const image=surface(im.width,im.height),g=image.getContext('2d',{willReadFrequently:true});g.drawImage(im,0,0);
    const a=g.getImageData(0,0,image.width,image.height).data;let empty=0;for(let i=3;i<a.length;i+=4)if(a[i]<24)empty++;
    if(empty/(image.width*image.height)<.01)throw new Error('몸 이미지는 투명 배경 PNG를 사용해 주세요.');
    const neckFit={...window.RunKKNeckJoin.inspectBody(image,pack),imageHeight:image.height};
    return {pack,image,neckFit};
  }
  async function installBodyPack(raw){const prepared=await prepareBodyPack(raw);bodySets.custom=prepared;state.customBodyPack=prepared.pack;makePresetCards();chooseOutfit('custom');}
  function bindWardrobe(){
    $('saveBodyPng').onclick=()=>{if(bodyTexture)saveCanvas(bodyTexture,'RunKK_body_only.png');};
    $('saveBodyPack').onclick=()=>{
      const preset=activeOutfit(),set=bodySets[preset.assetId||'daily'];if(!set)return;
      const pack={...set.pack,id:preset.id,name:preset.name,description:preset.desc,palette:{shirt:state.shirt,shorts:state.shorts,shoes:state.shoes},preserveArt:Boolean(preset.preserveArt)};
      download('RunKK_body_set_v2.json',new Blob([JSON.stringify(pack)],{type:'application/json'}));notify('머리 없는 몸 이미지와 연결 기준을 저장해요.');
    };
    $('loadBodyPack').onclick=()=>$('bodyPackFile').click();
    $('bodyPackFile').onchange=async e=>{
      const file=e.target.files[0];if(!file)return;
      try{
        if(file.size>24*1024*1024)throw new Error('몸 세트 파일은 24MB 이하로 골라 주세요.');
        let pack;
        if(/\.(png|webp)$/i.test(file.name)){
          const body=trimAlpha(await loadImage(await fileData(file)));if(!body.hasAlpha)throw new Error('배경 없는 몸 PNG를 선택해 주세요.');
          pack={...bodySets.daily.pack,id:'custom',name:file.name.slice(0,40),description:'내가 준비한 몸 PNG',image:body.canvas.toDataURL('image/png'),neck:[.5,.083],skinZones:[],preserveArt:true};
        }else pack=JSON.parse(await file.text());
        await installBodyPack(pack);notify('몸만 바꿨어요. 얼굴은 그대로예요.');
      }catch(error){notify('몸 불러오기 실패: '+error.message);}finally{e.target.value='';}
    };
    $('removeBodyPack').onclick=()=>{const wasCustom=state.bodyPackId==='custom';state.customBodyPack=null;delete bodySets.custom;makePresetCards();if(wasCustom)chooseOutfit('daily');else{syncControls();scheduleSave();}};
    $('generateBody').onclick=async()=>{
      const prompt=$('bodyPrompt').value.trim()||'선택한 상의·하의·신발';
      try{aiBusy=true;syncAiButtons();$('generateBody').textContent='몸 이미지 생성 중…';$('bodyAiMessage').textContent='얼굴 없이 하나로 연결된 몸 이미지를 만들고 있어요. 새 결과는 내 몸 세트로 추가돼요.';
        const partIds={top:$('topPart').value,bottom:$('bottomPart').value,shoes:$('shoesPart').value};
        const result=await apiRequest('/api/generate-body',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({userId:demoUserId,description:prompt,partIds})});await installBodyPack(result.pack);$('bodyAiMessage').textContent=result.warning||'선택한 파츠를 하나의 완성형 몸으로 만들었어요. 머리는 그대로 유지돼요.';notify('선택한 코디를 자연스럽게 입혔어요.');
      }catch(error){$('bodyAiMessage').textContent=error.message;notify(error.message);}finally{aiBusy=false;$('generateBody').textContent='이 코디로 AI 입히기 ↗';syncAiButtons();}
    };
  }

  async function init() {
    try{
      if(!ASSETS)throw new Error('assets.bundle.js 파일이 필요해요. ZIP 전체를 압축 해제해 주세요.');
      try{const saved=localStorage.getItem(STORAGE);if(saved)state=cleanState(JSON.parse(saved));}catch{/* storage may be disabled for file:// */}
      if(state.generationId&&state.faceData&&state.segmentationVersion!==HEAD_SEGMENTATION_VERSION){
        try{const repaired=await apiRequest('/api/heads/latest?userId='+encodeURIComponent(demoUserId));if(repaired.warning)notify(repaired.warning);if(repaired.id===state.generationId&&repaired.segmentationVersion===HEAD_SEGMENTATION_VERSION){state.headData=repaired.frontImage;state.faceData=repaired.faceImage;state.eyesData=repaired.eyesImage;state.hairData=repaired.hairImage;state.segmentationVersion=HEAD_SEGMENTATION_VERSION;scheduleSave();}}catch{/* Keep the stored head if the local server is unavailable. */}
      }
      for(const pack of builtinBodies)bodySets[pack.id]=await prepareBodyPack(pack);
      if(!bodySets.daily)throw new Error('새 몸 의상 파일이 필요해요. ZIP 전체를 다시 압축 해제해 주세요.');
      if(state.customBodyPack){try{bodySets.custom=await prepareBodyPack(state.customBodyPack);}catch{state.customBodyPack=null;state.bodyPackId='daily';}}
      try{if(state.faceData&&state.eyesData&&state.hairData)await setLayers(state.faceData,state.eyesData,state.hairData,state.generationId?'AI 생성 얼굴 + 눈 + 머리카락':'기본 머리');else if(state.headData)await setHead(state.headData,state.generationId?'AI 생성 머리':'내 머리 PNG');else{state.headPivotX=DEFAULT_PIVOT.x;state.headPivotY=DEFAULT_PIVOT.y;await setLayers('/assets/face-base-default.png','/assets/eyes-default.png','/assets/hair-default.png');}}catch{state.headData=null;state.faceData=null;state.eyesData=null;state.hairData=null;state.headPivotX=DEFAULT_PIVOT.x;state.headPivotY=DEFAULT_PIVOT.y;await setLayers('/assets/face-base-default.png','/assets/eyes-default.png','/assets/hair-default.png');}
      makePresetCards();bind();bindAi();bindWardrobe();bindAvatarViews();renderTraits(state.traits);isReady=true;rebuildParts();syncControls();drawPresetThumbnails();$('loading').hidden=true;
      status('새 몸 의상을 적용했어요. 기존 얼굴은 유지하며 몸 전체가 연결되어 움직여요.');refreshApi();
      restoreAvatarViews(false);
      window.RUNKK_LOCAL_DOCUMENT={rigVersion:RIG,renderer:'seamless-mesh-v1',canvas:[1024,1024],neck:[512,450]};
    }catch(err){$('loading').textContent='준비 실패: '+err.message;status(err.message);}
    requestAnimationFrame(frame);
  }
  init();
})();
