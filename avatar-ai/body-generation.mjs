import sharp from 'sharp';
import {toFile} from 'openai';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {randomUUID} from 'node:crypto';
import path from 'node:path';
import {requireUserId,userDirectory} from './user-storage.mjs';
import {mobileBody} from './mobile-composition.mjs';

const bodyError=message=>Object.assign(new Error(message),{status:502});

async function normalizeBody(buffer){
  const {data,info}=await sharp(buffer,{limitInputPixels:24_000_000}).resize({width:1400,height:1800,fit:'inside',withoutEnlargement:true}).toColourspace('srgb').ensureAlpha().raw().toBuffer({resolveWithObject:true});
  let x0=info.width,y0=info.height,x1=-1,y1=-1,transparent=0;
  for(let y=0;y<info.height;y++)for(let x=0;x<info.width;x++){const a=data[(y*info.width+x)*4+3];if(a<16)transparent++;if(a>100){x0=Math.min(x0,x);x1=Math.max(x1,x);y0=Math.min(y0,y);y1=Math.max(y1,y);}}
  if(x1<0)throw bodyError('생성 결과에 몸 이미지가 없어요. 기존 몸은 유지돼요.');
  if(transparent/(info.width*info.height)<.02)throw bodyError('투명 배경이 아닌 몸 이미지예요. 기존 몸은 유지돼요.');
  const w=x1-x0+1,h=y1-y0+1;if(h<200||w/h<.25||w/h>1.1)throw bodyError('연결된 정면 몸 형태가 아니에요. 기존 몸은 유지돼요.');
  x0=Math.max(0,x0-8);y0=Math.max(0,y0-8);x1=Math.min(info.width-1,x1+8);y1=Math.min(info.height-1,y1+8);
  return sharp(data,{raw:{width:info.width,height:info.height,channels:4}}).extract({left:x0,top:y0,width:x1-x0+1,height:y1-y0+1}).resize({width:633,height:1110,fit:'fill'}).png().toBuffer();
}

async function inspectBody(client,visionModel,png){
  const response=await client.chat.completions.create({model:visionModel,temperature:0,max_tokens:1200,messages:[
    {role:'system',content:'Return only valid JSON with boolean has_head, boolean assembled, integer neck_x, integer neck_y, boolean confident, and skin_boxes array of {x,y,w,h}.'},
    {role:'user',content:[{type:'text',text:'Inspect this 2D cartoon body asset. has_head=true if any face, head, ears or hair is drawn. assembled=true only for ONE fully connected front-view body from a short neck stub to both shoes, never exploded parts or multiple bodies. Find the center of the neckline inside the collar near the base of the short skin neck stub, where a separate head neck stub should attach. Return neck_x/neck_y in 0..1000 whole image coordinates. skin_boxes: at most 8 tight rectangles {x,y,w,h} in 0..1000 around exposed skin only, excluding fabric and shoes. confident=false if neck location is hidden.'},{type:'image_url',image_url:{url:`data:image/png;base64,${png.toString('base64')}`,detail:'high'}}]}
  ]});
  const text=response.choices?.[0]?.message?.content||'{}',start=text.indexOf('{'),end=text.lastIndexOf('}');
  const v=JSON.parse(start>=0&&end>start?text.slice(start,end+1):text);
  if(v.has_head!==false||v.assembled!==true)throw bodyError('머리 없는 완성형 몸으로 생성되지 않았어요. 결과를 적용하지 않고 기존 얼굴과 몸을 유지해요.');
  const valid=v.confident&&Number.isFinite(v.neck_x)&&Number.isFinite(v.neck_y)&&v.neck_x>=300&&v.neck_x<=700&&v.neck_y>=0&&v.neck_y<=250;
  const skinZones=(Array.isArray(v.skin_boxes)?v.skin_boxes:[]).slice(0,8).filter(b=>[b.x,b.y,b.w,b.h].every(n=>Number.isFinite(n)&&n>=0&&n<=1000)).map(b=>({rect:[b.x/1000,b.y/1000,b.w/1000,b.h/1000]}));
  return {neck:valid?[v.neck_x/1000,v.neck_y/1000]:[.5,.083],skinZones,estimated:!valid};
}

export function registerBodyGeneration(app,{client,root,imageModel,visionModel,quality,ensureApiKey,isBusy,setBusy}){
  app.post('/api/generate-body',async(req,res,next)=>{let owns=false;try{
    const userId=requireUserId(req.body?.userId);
    ensureApiKey();const description=String(req.body?.description||'선택한 상의·하의·신발').trim().slice(0,500);const requested=req.body?.partIds||{};
    const allowed={top:'generated-top',bottom:'generated-bottom',shoes:'generated-shoes'},files={'generated-top':'top-mint-hoodie.png','generated-bottom':'bottom-navy-jogger.png','generated-shoes':'shoes-cream-mint.png'},kinds=['top','bottom','shoes'];
    const selected=kinds.map(kind=>String(requested[kind]||''));if(selected.some((id,index)=>id!==allowed[kinds[index]]))return res.status(400).json({error:'상의·하의·신발을 모두 선택해 주세요.'});
    if(isBusy())return res.status(409).json({error:'다른 이미지를 생성 중이에요. 완료 후 다시 시도해 주세요.'});setBusy(true);owns=true;
    const reference=await readFile(path.join(root,'public/assets/reference-character.png'));const partBuffers=await Promise.all(selected.map(id=>readFile(path.join(root,'public/wardrobe-generated',files[id]))));const partFiles=await Promise.all(partBuffers.map((buffer,index)=>toFile(buffer,`runkk-selected-${kinds[index]}.png`,{type:'image/png'})));
    const prompt=`Create ONE finished RunKK chibi body by dressing the body in IMAGE 1 with the clothing in IMAGES 2-4. IMAGE 1 is the immutable camera, pose, body proportion, neck, hands, legs, ankles and shoe-baseline guide. IMAGE 2 is the selected TOP, IMAGE 3 is the selected BOTTOM, IMAGE 4 is the selected SHOES. Keep the exact front-facing framing and connect every garment naturally at shoulders, waist, cuffs, ankles and feet. Clothing description: ${description}. Output exactly one connected headless body from a short exposed neck stub through both shoes. No head, face, hair, ears, floating pieces, exploded parts, duplicate limbs, gaps, seams, circular joint caps, floor, shadow, text or watermark. Preserve small hands, arms straight down, slightly separated vertical legs, socks and both shoes. The selected top must end naturally at the waist and the selected bottom must begin at the waist without any hidden top strip. Shoes must contain shoes only and sit directly on the ankles. Use a transparent alpha background with the entire body in frame. This is an assembled clothing result, never a collage or sprite sheet.`;
    const result=await client.images.edit({model:imageModel,image:[await toFile(reference,'runkk-body-reference.png',{type:'image/png'}),...partFiles],prompt,size:'1024x1536',quality,background:'transparent',output_format:'png'});const encoded=result.data?.[0]?.b64_json;if(!encoded)throw bodyError('몸 생성 응답에 PNG가 없어요.');
    const png=await normalizeBody(Buffer.from(encoded,'base64'));let alignment;try{alignment=await inspectBody(client,visionModel,png);}catch(error){if(error.status===502)throw error;throw bodyError('몸 형태와 목 위치를 확인하지 못했어요. 기존 몸을 유지해요.');}
    const pack={format:'runkk-body-pack',version:2,rigVersion:'runkk-front-v1',renderer:'seamless-mesh-v1',id:'ai-'+randomUUID(),name:'AI 코디 완성 룩',description:description.slice(0,80),image:`data:image/png;base64,${png.toString('base64')}`,neck:alignment.neck,skinZones:alignment.skinZones,palette:{shirt:'#a6bda0',shorts:'#29345f',shoes:'#e8e4d9'},preserveArt:true};
    pack.mobile=await mobileBody(png,pack);
    let saved=true;try{const directory=path.join(userDirectory(root,userId),'bodies');await mkdir(directory,{recursive:true});await writeFile(path.join(directory,pack.id+'.png'),png);await writeFile(path.join(directory,pack.id+'.json'),JSON.stringify({...pack,userId}));}catch{saved=false;}
    res.json({userId,pack,saved,warning:[alignment.estimated?'목 위치는 기본값으로 연결했어요. 머리 위치에서 조절할 수 있어요.':'선택한 파츠를 하나의 자연스러운 몸 이미지로 합쳐 얼굴에 연결했어요.',saved?'몸 세트 저장으로 보관해 주세요.':'서버에 저장하지 못했어요. 몸 세트 저장으로 보관해 주세요.'].join(' ')});
  }catch(error){next(error);}finally{if(owns)setBusy(false);}});
}
