import {readFile,writeFile,mkdir,readdir,unlink} from 'node:fs/promises';
import {createHash,randomUUID} from 'node:crypto';
import path from 'node:path';
import http from 'node:http';
import {requireUserId,userDirectory,atomicJson,deleteUserData} from './user-storage.mjs';
const terminal=new Set(['SUCCEEDED','FAILED']);
// 결과가 불명확하게 끝난 작업. 같은 프로세스의 생성은 계속 돌아 머리를 저장할 수 있어, 그 뒤 저장된 머리를 회수한다(DV-성-9).
const RECOVERABLE=new Set(['GENERATION_OUTCOME_UNKNOWN','UPSTREAM_TIMEOUT']);
/**
 * 같은 프로세스의 /generate를 node:http로 부른다. 전역 fetch(undici)는 응답 머리 대기(headersTimeout)·본문 대기(bodyTimeout)
 * 기본값이 300초라, 생성이 5분을 넘기면 15분 제한(generationTimeoutMs)보다 먼저 TypeError(UND_ERR_HEADERS_TIMEOUT)로 끊겨
 * GENERATION_OUTCOME_UNKNOWN이 됐다(DV-성-9). 여기서는 시간 제한을 timeoutMs 하나로 두고, 넘기면 TimeoutError로 끊는다.
 */
export async function postForm(url,form,timeoutMs){
  const encoded=new Response(form),body=Buffer.from(await encoded.arrayBuffer());
  return new Promise((resolve,reject)=>{
    let settled=false;const finish=(fn,value)=>{if(settled)return;settled=true;clearTimeout(timer);fn(value);};
    const request=http.request(url,{method:'POST',agent:false,headers:{'content-type':encoded.headers.get('content-type'),'content-length':body.length}},response=>{
      const chunks=[];response.on('data',chunk=>chunks.push(chunk));response.on('error',error=>finish(reject,error));
      response.on('end',()=>{try{finish(resolve,{status:response.statusCode,ok:response.statusCode>=200&&response.statusCode<300,data:JSON.parse(Buffer.concat(chunks).toString('utf8'))});}catch(error){finish(reject,error);}});
    });
    const timer=setTimeout(()=>{const error=Object.assign(new Error('생성 응답 시간 초과'),{name:'TimeoutError'});request.destroy(error);finish(reject,error);},timeoutMs);
    request.on('error',error=>finish(reject,error));request.end(body);
  });
}
const notFound=()=>Object.assign(new Error('이 사용자의 생성 작업을 찾을 수 없어요.'),{status:404,code:'JOB_NOT_FOUND'});
export function registerHeadJobs(app,{root,upload,normalizeSelfie,port,normalizeResult=async data=>data,workerIntervalMs=1000,generationTimeoutMs=900000}){
  const queue=[],known=new Set(),submissionLocks=new Map();let processing=false,notifying=false,active=null;
  async function withSubmissionLock(key, action){
    const previous=submissionLocks.get(key)||Promise.resolve();let release;
    const current=new Promise(resolve=>{release=resolve;});submissionLocks.set(key,current);
    await previous;
    try{return await action();}finally{release();if(submissionLocks.get(key)===current)submissionLocks.delete(key);}
  }
  const dir=(userId,id)=>path.join(userDirectory(root,userId),'jobs',id);
  async function read(userId,id){requireUserId(userId);if(!/^[a-f0-9-]{36}$/.test(id))throw notFound();try{const job=JSON.parse(await readFile(path.join(dir(userId,id),'job.json'),'utf8'));if(job.userId!==userId)throw notFound();return job;}catch(e){if(e.code==='ENOENT')throw notFound();throw e;}}
  const persist=job=>atomicJson(path.join(dir(job.userId,job.jobId),'job.json'),job);
  function publicJob(job){const suffix=`${job.jobId}?userId=${encodeURIComponent(job.userId)}`;return {jobId:job.jobId,userId:job.userId,status:job.status,createdAt:job.createdAt,updatedAt:job.updatedAt,headId:job.headId||null,error:job.error||null,statusPath:'/api/v1/head-jobs/'+suffix,resultPath:`/api/v1/head-jobs/${job.jobId}/result?userId=${encodeURIComponent(job.userId)}`,notification:job.notification?{eventId:job.notification.eventId,status:job.notification.status,attempts:job.notification.attempts}:null};}
  async function complete(job,status,error=null){job.status=status;job.error=error;job.updatedAt=new Date().toISOString();job.notification={eventId:randomUUID(),status:process.env.AI_CALLBACK_URL?'PENDING':'NOT_CONFIGURED',attempts:0,nextAttemptAt:0};await persist(job);await unlink(path.join(dir(job.userId,job.jobId),'selfie.jpg')).catch(()=>{});}
  async function notify(job){
    const n=job.notification,url=process.env.AI_CALLBACK_URL;if(!n||!url||n.status==='DELIVERED'||n.status==='GAVE_UP'||n.nextAttemptAt>Date.now())return;
    try{const u=new URL(url);if(!['http:','https:'].includes(u.protocol))throw new Error('Invalid callback URL');n.attempts++;const event={eventId:n.eventId,type:job.status==='SUCCEEDED'?'AVATAR_HEAD_READY':'AVATAR_HEAD_FAILED',jobId:job.jobId,userId:job.userId,headId:job.headId||null,status:job.status,resultPath:publicJob(job).resultPath,error:job.error||null};const response=await fetch(u,{method:'POST',headers:{'Content-Type':'application/json',...(process.env.AI_CALLBACK_TOKEN?{Authorization:'Bearer '+process.env.AI_CALLBACK_TOKEN}:{})},body:JSON.stringify(event),signal:AbortSignal.timeout(5000)});if(!response.ok)throw new Error('Callback rejected');n.status='DELIVERED';n.deliveredAt=new Date().toISOString();}
    catch{n.status=n.attempts>=8?'GAVE_UP':'PENDING';n.nextAttemptAt=Date.now()+Math.min(300000,1000*2**n.attempts);}
    await persist(job);
  }
  async function pump(){if(processing||!queue.length)return;processing=true;const identity=queue.shift();active=identity;let job;
    try{job=await read(identity.userId,identity.jobId);if(job.status!=='QUEUED')return;
      job.status='RUNNING';job.updatedAt=new Date().toISOString();job.startedAt=job.updatedAt;await persist(job);
      const form=new FormData();for(const[k,v]of Object.entries(job.options))form.append(k,v);form.append('userId',job.userId);form.append('selfie',new Blob([await readFile(path.join(dir(job.userId,job.jobId),'selfie.jpg'))],{type:'image/jpeg'}),'selfie.jpg');
      let response,data;try{response=await postForm(`http://127.0.0.1:${port}/api/v1/generate`,form,generationTimeoutMs);data=response.data;}catch(error){await complete(job,'FAILED',{code:error.name==='TimeoutError'?'UPSTREAM_TIMEOUT':'GENERATION_OUTCOME_UNKNOWN',message:'생성 결과가 불명확해 자동 재시도하지 않았어요. 저장된 최근 머리를 확인해 주세요.'});return;}
      if(response.status===409&&data.code==='GENERATION_BUSY'){job.status='QUEUED';delete job.startedAt;await persist(job);queue.push(identity);return;}
      if(!response.ok){await complete(job,'FAILED',{code:data.code||'GENERATION_FAILED',message:data.message||'생성에 실패했어요.',...(data.quality?{quality:data.quality}:{})});return;}
      if(!data.saved){await complete(job,'FAILED',{code:'STORAGE_FAILED',message:'결과를 영구 저장하지 못했어요. 자동 재생성하지 않았어요.'});return;}
      try{data=await normalizeResult(data,job.userId);}catch(e){await complete(job,'FAILED',{code:e.code||'FACE_ASSETS_INCOMPLETE',message:e.message||'얼굴 이미지 세트가 완성되지 않았어요.'});return;}
      await atomicJson(path.join(dir(job.userId,job.jobId),'result.json'),data);job.headId=data.headId;await complete(job,'SUCCEEDED');
    }catch(error){if(job)await complete(job,'FAILED',{code:'STORAGE_FAILED',message:'작업 결과를 저장하지 못했어요. 자동 재생성하지 않았어요.'}).catch(()=>{});console.error('[head-jobs] worker storage failure',error.code||error.name);}
    finally{processing=false;active=null;}
  }
  async function userJobs(userId){
    const jobs=path.join(userDirectory(root,userId),'jobs');let ids;try{ids=await readdir(jobs);}catch(e){if(e.code==='ENOENT')return [];throw e;}
    const found=[];for(const id of ids.filter(id=>/^[a-f0-9-]{36}$/.test(id))){try{found.push(JSON.parse(await readFile(path.join(jobs,id,'job.json'),'utf8')));}catch(e){if(e.code!=='ENOENT')throw e;}}return found;
  }
  /**
   * 결과가 불명확하게 끝난 작업(RECOVERABLE)을 그 뒤 저장된 최근 머리로 성공 처리한다. 유료 생성은 다시 하지 않는다.
   * 머리가 이 작업 시작(startedAt) 뒤에 저장됐고, 다른 작업이 그 머리를 갖지 않았고, 그사이 같은 사용자의 다른 작업이 생성을 시작하지
   * 않았을 때만 이 작업의 결과로 본다(생성은 직렬이라 그 구간의 머리는 이 작업 것이다).
   */
  async function recover(job){
    if(job.status!=='FAILED'||!RECOVERABLE.has(job.error?.code)||!job.startedAt)return job;
    const userDir=userDirectory(root,job.userId);let pointer,head;
    try{pointer=JSON.parse(await readFile(path.join(userDir,'latest.json'),'utf8'));if(!/^[a-f0-9-]{36}$/.test(pointer?.id||''))return job;head=JSON.parse(await readFile(path.join(userDir,'heads',pointer.id+'.json'),'utf8'));}catch(e){if(e.code==='ENOENT')return job;throw e;}
    const savedAt=Date.parse(head.createdAt),startedAt=Date.parse(job.startedAt);
    if(head.userId!==job.userId||!(savedAt>=startedAt))return job;
    for(const other of await userJobs(job.userId)){
      if(other.jobId===job.jobId)continue;
      if(other.headId===pointer.id)return job;
      const otherStart=Date.parse(other.startedAt||'');if(otherStart>startedAt&&otherStart<=savedAt)return job;
    }
    const data=await normalizeResult({userId:job.userId,headId:pointer.id},job.userId);
    await atomicJson(path.join(dir(job.userId,job.jobId),'result.json'),data);
    job.headId=pointer.id;job.recoveredFrom=job.error.code;await complete(job,'SUCCEEDED');
    console.log('[head-jobs] recovered',job.recoveredFrom);
    return job;
  }
  const readRecovered=(userId,id)=>withSubmissionLock(userId+'/'+id,async()=>recover(await read(userId,id)));
  async function scan(){
    const users=path.join(root,'data/users');let entries;try{entries=await readdir(users,{withFileTypes:true});}catch(e){if(e.code==='ENOENT')return;throw e;}
    for(const user of entries.filter(e=>e.isDirectory()&&/^[a-f0-9]{64}$/.test(e.name))){let jobs;try{jobs=await readdir(path.join(users,user.name,'jobs'));}catch(e){if(e.code==='ENOENT')continue;throw e;}for(const id of jobs.filter(id=>/^[a-f0-9-]{36}$/.test(id))){const job=JSON.parse(await readFile(path.join(users,user.name,'jobs',id,'job.json'),'utf8'));requireUserId(job.userId);if(path.resolve(dir(job.userId,id))!==path.resolve(users,user.name,'jobs',id))continue;
      const key=user.name+'/'+id;if(!known.has(key)){known.add(key);if(job.status==='RUNNING')await complete(job,'FAILED',{code:'GENERATION_INTERRUPTED',message:'서버가 재시작되어 결과를 확정하지 못했어요. 중복 과금을 막기 위해 자동 재생성하지 않았어요.'});else if(job.status==='QUEUED')queue.push({userId:job.userId,jobId:id});}
      if(terminal.has(job.status))await notify(job);
    }}
  }
  app.post('/api/head-jobs',upload.single('selfie'),async(req,res,next)=>{try{
    const userId=requireUserId(req.body?.userId),jobId=String(req.body?.clientRequestId||'').toLowerCase();if(!/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/.test(jobId))throw Object.assign(new Error('clientRequestId는 UUID로 보내 주세요.'),{status:400,code:'BAD_REQUEST'});if(!req.file)throw Object.assign(new Error('셀카 파일이 필요해요.'),{status:400});
    await withSubmissionLock(userId+"/"+jobId,async()=>{
    const selfie=await normalizeSelfie(req.file.buffer),options=Object.fromEntries(['traits','hairLength','similarity'].filter(k=>typeof req.body[k]==='string').map(k=>[k,req.body[k]]));const hash=createHash('sha256').update(selfie).update(JSON.stringify(options)).digest('hex');
    let old;try{old=await read(userId,jobId);}catch(e){if(e.code!=='JOB_NOT_FOUND')throw e;}if(old){if(old.requestHash!==hash)throw Object.assign(new Error('같은 clientRequestId에 다른 입력을 보낼 수 없어요.'),{status:409,code:'IDEMPOTENCY_CONFLICT'});return res.status(202).json(publicJob(old));}
    const createdAt=new Date().toISOString(),job={jobId,userId,status:'QUEUED',createdAt,updatedAt:createdAt,requestHash:hash,options};const directory=dir(userId,jobId);try{await mkdir(directory,{recursive:false});}catch(e){if(e.code==='ENOENT'){await mkdir(path.dirname(directory),{recursive:true});try{await mkdir(directory);}catch(c){if(c.code==='EEXIST')throw Object.assign(new Error('동일 요청을 접수 중이에요. 같은 clientRequestId로 다시 조회해 주세요.'),{status:409,code:'IDEMPOTENCY_CONFLICT'});throw c;}}else if(e.code==='EEXIST')throw Object.assign(new Error('동일 요청을 접수 중이에요.'),{status:409,code:'IDEMPOTENCY_CONFLICT'});else throw e;}
    await writeFile(path.join(directory,'selfie.jpg'),selfie);await persist(job);known.add(path.basename(userDirectory(root,userId))+'/'+jobId);queue.push({userId,jobId});res.status(202).json(publicJob(job));
    });
  }catch(e){next(e);}});
  app.get('/api/head-jobs/:jobId',async(req,res,next)=>{try{res.json(publicJob(await readRecovered(requireUserId(req.query.userId),req.params.jobId)));}catch(e){next(e);}});
  app.get('/api/head-jobs/:jobId/result',async(req,res,next)=>{try{const job=await readRecovered(requireUserId(req.query.userId),req.params.jobId);if(job.status!=='SUCCEEDED')return res.status(409).json({error:'성공한 작업에서만 결과를 조회할 수 있어요.',code:'JOB_NOT_READY'});res.json(await normalizeResult(JSON.parse(await readFile(path.join(dir(job.userId,job.jobId),'result.json'),'utf8')),job.userId));}catch(e){next(e);}});
  // 탈퇴 파기(S15P31A207-257). ai DELETE /users/{userId}가 부른다. 생성 중이면 결과 파일이 다시 생기므로 409 USER_BUSY로 거절하고
  // (ai가 503을 돌려 api가 다시 부른다), 대기 중인 작업은 큐에서 빼고 셀카와 함께 지운다.
  app.delete('/api/users/:userId',async(req,res,next)=>{try{
    const userId=requireUserId(req.params.userId);
    if(active&&active.userId===userId)return res.status(409).json({error:'이 사용자의 아바타를 만드는 중이에요. 끝난 뒤 다시 지워 주세요.',code:'USER_BUSY'});
    for(let i=queue.length-1;i>=0;i--)if(queue[i].userId===userId)queue.splice(i,1);
    const hashed=path.basename(userDirectory(root,userId))+'/';for(const key of [...known])if(key.startsWith(hashed))known.delete(key);
    res.json({userId,deleted:await deleteUserData(root,userId)});
  }catch(e){next(e);}});
  const timer=setInterval(()=>{pump();if(!notifying){notifying=true;scan().catch(()=>console.error('[head-jobs] scan failed')).finally(()=>{notifying=false;});}},workerIntervalMs);timer.unref();
  return {stop:()=>clearInterval(timer)};
}
