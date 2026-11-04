import {readFile,writeFile,mkdir,readdir,unlink} from 'node:fs/promises';
import {createHash,randomUUID} from 'node:crypto';
import path from 'node:path';
import {requireUserId,userDirectory,atomicJson} from './user-storage.mjs';
const terminal=new Set(['SUCCEEDED','FAILED']);
const notFound=()=>Object.assign(new Error('이 사용자의 생성 작업을 찾을 수 없어요.'),{status:404,code:'JOB_NOT_FOUND'});
export function registerHeadJobs(app,{root,upload,normalizeSelfie,port,normalizeResult=async data=>data,workerIntervalMs=1000,generationTimeoutMs=900000}){
  const queue=[],known=new Set(),submissionLocks=new Map();let processing=false,notifying=false;
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
  async function pump(){if(processing||!queue.length)return;processing=true;const identity=queue.shift();let job;
    try{job=await read(identity.userId,identity.jobId);if(job.status!=='QUEUED')return;
      job.status='RUNNING';job.updatedAt=new Date().toISOString();await persist(job);
      const form=new FormData();for(const[k,v]of Object.entries(job.options))form.append(k,v);form.append('userId',job.userId);form.append('selfie',new Blob([await readFile(path.join(dir(job.userId,job.jobId),'selfie.jpg'))],{type:'image/jpeg'}),'selfie.jpg');
      let response,data;try{response=await fetch(`http://127.0.0.1:${port}/api/v1/generate`,{method:'POST',body:form,signal:AbortSignal.timeout(generationTimeoutMs)});data=await response.json();}catch(error){await complete(job,'FAILED',{code:error.name==='TimeoutError'?'UPSTREAM_TIMEOUT':'GENERATION_OUTCOME_UNKNOWN',message:'생성 결과가 불명확해 자동 재시도하지 않았어요. 저장된 최근 머리를 확인해 주세요.'});return;}
      if(response.status===409&&data.code==='GENERATION_BUSY'){job.status='QUEUED';await persist(job);queue.push(identity);return;}
      if(!response.ok){await complete(job,'FAILED',{code:data.code||'GENERATION_FAILED',message:data.message||'생성에 실패했어요.',...(data.quality?{quality:data.quality}:{})});return;}
      if(!data.saved){await complete(job,'FAILED',{code:'STORAGE_FAILED',message:'결과를 영구 저장하지 못했어요. 자동 재생성하지 않았어요.'});return;}
      try{data=await normalizeResult(data,job.userId);}catch(e){await complete(job,'FAILED',{code:e.code||'FACE_ASSETS_INCOMPLETE',message:e.message||'얼굴 이미지 세트가 완성되지 않았어요.'});return;}
      await atomicJson(path.join(dir(job.userId,job.jobId),'result.json'),data);job.headId=data.headId;await complete(job,'SUCCEEDED');
    }catch(error){if(job)await complete(job,'FAILED',{code:'STORAGE_FAILED',message:'작업 결과를 저장하지 못했어요. 자동 재생성하지 않았어요.'}).catch(()=>{});console.error('[head-jobs] worker storage failure',error.code||error.name);}
    finally{processing=false;}
  }
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
  app.get('/api/head-jobs/:jobId',async(req,res,next)=>{try{res.json(publicJob(await read(requireUserId(req.query.userId),req.params.jobId)));}catch(e){next(e);}});
  app.get('/api/head-jobs/:jobId/result',async(req,res,next)=>{try{const job=await read(requireUserId(req.query.userId),req.params.jobId);if(job.status!=='SUCCEEDED')return res.status(409).json({error:'성공한 작업에서만 결과를 조회할 수 있어요.',code:'JOB_NOT_READY'});res.json(await normalizeResult(JSON.parse(await readFile(path.join(dir(job.userId,job.jobId),'result.json'),'utf8')),job.userId));}catch(e){next(e);}});
  const timer=setInterval(()=>{pump();if(!notifying){notifying=true;scan().catch(()=>console.error('[head-jobs] scan failed')).finally(()=>{notifying=false;});}},workerIntervalMs);timer.unref();
  return {stop:()=>clearInterval(timer)};
}
