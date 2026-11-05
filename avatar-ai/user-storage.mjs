import {createHash,randomUUID} from 'node:crypto';
import {mkdir,readFile,writeFile,rename,rm,stat} from 'node:fs/promises';
import path from 'node:path';
import {RIGHT_LAYER_VERSION} from './right-head-layers.mjs';

export function requireUserId(value){
  if(typeof value!=='string'||!/^[A-Za-z0-9_-]{1,128}$/.test(value))throw Object.assign(new Error('userId는 영문·숫자·밑줄·하이픈 1~128자로 보내 주세요.'),{status:400,code:'INVALID_USER_ID'});
  return value;
}
export function userDirectory(root,userId){return path.join(root,'data/users',createHash('sha256').update(requireUserId(userId)).digest('hex'));}
export async function atomicJson(file,value){
  await mkdir(path.dirname(file),{recursive:true});const temporary=file+'.'+randomUUID()+'.tmp';
  await writeFile(temporary,JSON.stringify(value,null,2));
  // Windows may briefly hold a just-read JSON file open; retry rename only,
  // never retry an AI request or expose partially written metadata.
  for(let attempt=0;;attempt++){try{await rename(temporary,file);break;}catch(e){if(attempt>=9||!['EPERM','EBUSY','EACCES'].includes(e.code))throw e;await new Promise(resolve=>setTimeout(resolve,20*(attempt+1)));}}
}
export async function saveUserHead(root,userId,layers,metadata){
  const dir=userDirectory(root,userId),heads=path.join(dir,'heads');await mkdir(heads,{recursive:true});
  for(const [suffix,image]of [['',layers.composite],['-face',layers.face],['-eyes',layers.eyes],['-hair',layers.hair]])await writeFile(path.join(heads,metadata.id+suffix+'.png'),image);
  if(layers.right)await writeFile(path.join(heads,metadata.id+'-right.png'),layers.right);
  if(['Face','Eyes','Hair'].every(part=>layers['right'+part]))await saveUserRightLayers(root,userId,metadata.id,layers);
  await atomicJson(path.join(heads,metadata.id+'.json'),{...metadata,userId});
  await atomicJson(path.join(dir,'latest.json'),{id:metadata.id});return {...metadata,userId};
}
export async function latestUserHead(root,userId){
  const dir=userDirectory(root,userId),pointer=JSON.parse(await readFile(path.join(dir,'latest.json'),'utf8'));
  return readUserHead(root,userId,pointer.id);
}
export async function readUserHead(root,userId,id){
  const dir=userDirectory(root,userId);if(!/^[a-f0-9-]{36}$/.test(id))throw new Error('Invalid head pointer');
  const base=path.join(dir,'heads',id),metadata=JSON.parse(await readFile(base+'.json','utf8'));
  if(metadata.userId!==userId)throw new Error('Head owner mismatch');
  const [composite,face,eyes,hair]=await Promise.all(['','-face','-eyes','-hair'].map(s=>readFile(base+s+'.png')));
  let right=null;try{right=await readFile(base+'-right.png');}catch(e){if(e.code!=='ENOENT')throw e;}
  const layers={composite,face,eyes,hair,right};
  try{const manifest=JSON.parse(await readFile(base+'-right-layers.json','utf8'));if(manifest.version===RIGHT_LAYER_VERSION)for(const part of ['Face','Eyes','Hair'])layers['right'+part]=await readFile(base+'-right-'+part.toLowerCase()+'.png');}catch(e){if(e.code!=='ENOENT')throw e;}
  return {metadata,layers};
}
export async function saveUserRightLayers(root,userId,id,layers){
  if(!/^[a-f0-9-]{36}$/.test(id))throw new Error('Invalid head pointer');
  const base=path.join(userDirectory(root,userId),'heads',id);
  for(const part of ['Face','Eyes','Hair'])await writeFile(base+'-right-'+part.toLowerCase()+'.png',layers['right'+part]);
  await atomicJson(base+'-right-layers.json',{version:RIGHT_LAYER_VERSION});
}
// 탈퇴 파기(S15P31A207-257). 사용자 폴더 하나에 셀카 작업·머리 레이어·옆모습·latest가 모두 있으므로 폴더째 지운다.
// 이미 없으면 false. 다른 사용자 폴더는 해시 이름이 달라 건드리지 않는다.
export async function deleteUserData(root,userId){
  const dir=userDirectory(root,userId);
  try{await stat(dir);}catch(e){if(e.code==='ENOENT')return false;throw e;}
  await rm(dir,{recursive:true,force:true});return true;
}
