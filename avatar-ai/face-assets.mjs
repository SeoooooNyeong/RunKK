import {createHash} from 'node:crypto';
import {latestUserHead,readUserHead} from './user-storage.mjs';
import {mobileHead} from './mobile-composition.mjs';
export const FACE_ASSETS_VERSION='runkk-face-assets-v1';
export const FACE_IMAGE_FIELDS=Object.freeze(['frontImage','rightImage','faceImage','eyesImage','hairImage']);
const dataUrl=buffer=>'data:image/png;base64,'+buffer.toString('base64');
const incomplete=()=>Object.assign(new Error('우측 얼굴까지 완성된 얼굴 세트가 필요해요. 우측 얼굴을 생성한 뒤 다시 조회해 주세요.'),{status:409,code:'FACE_ASSETS_INCOMPLETE'});
export function faceImages(layers){
  if(![layers.composite,layers.right,layers.face,layers.eyes,layers.hair].every(b=>Buffer.isBuffer(b)&&b.length))throw incomplete();
  return {frontImage:dataUrl(layers.composite),rightImage:dataUrl(layers.right),faceImage:dataUrl(layers.face),eyesImage:dataUrl(layers.eyes),hairImage:dataUrl(layers.hair)};
}
export function profileMetadata(profile){const {image,metrics,warning,saved,...metadata}=profile;return metadata;}
export function assertFaceResult(data){
  if(data?.schemaVersion!==FACE_ASSETS_VERSION||!FACE_IMAGE_FIELDS.every(k=>typeof data[k]==='string'&&data[k].startsWith('data:image/png;base64,'))||['image','bodyImage','body','pack'].some(k=>Object.hasOwn(data,k)))throw incomplete();
  const count=value=>typeof value==='string'?Number(value.startsWith('data:image/')):value&&typeof value==='object'?Object.values(value).reduce((n,v)=>n+count(v),0):0;
  if(count(data)!==5)throw incomplete();
  return data;
}
export async function savedFaceResult(root,userId,findRight,headId=null){
  const {metadata,layers}=headId?await readUserHead(root,userId,headId):await latestUserHead(root,userId);
  let rightProfile=metadata.rightProfile;
  if(!layers.right){
    const hash=createHash('sha256').update(layers.composite).digest('hex'),profile=await findRight(userId,hash);
    if(!profile||profile.scope!=='head'||profile.direction!=='right'||profile.sourceHash!==hash||profile.userId!==userId)throw incomplete();
    layers.right=Buffer.from(profile.image.split(',')[1],'base64');rightProfile=profileMetadata(profile);
  }
  const mobile=metadata.mobile?.version==='runkk-mobile-compose-v2'?metadata.mobile:await mobileHead(layers.face,metadata.alignment);
  return assertFaceResult({...metadata,userId,schemaVersion:FACE_ASSETS_VERSION,headId:metadata.id,mobile,rightProfile,...faceImages(layers),saved:true,warning:null});
}
