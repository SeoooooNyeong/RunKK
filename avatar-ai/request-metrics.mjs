import {AsyncLocalStorage} from 'node:async_hooks';
import {randomUUID} from 'node:crypto';
import {mkdir,appendFile} from 'node:fs/promises';
import path from 'node:path';
const active=new AsyncLocalStorage();
function numeric(value,depth=0){if(depth>5)return null;if(typeof value==='number'&&Number.isFinite(value))return value;if(!value||typeof value!=='object')return null;const entries=Object.entries(value).flatMap(([key,v])=>{const safe=numeric(v,depth+1);return safe===null?[]:[[key,safe]];});return entries.length?Object.fromEntries(entries):null;}
export function measuredClient(client){
  const wrap=(method,operation)=>async args=>{const record=active.getStore(),start=performance.now();let response,error;
    try{return response=await method(args);}catch(e){error=e;throw e;}finally{if(record)record.calls.push({operation,model:args.model,elapsedMs:Math.round(performance.now()-start),ok:!error,status:error?.status||200,usage:numeric(response?.usage),providerCredits:typeof response?.usage?.credits_used==='number'?response.usage.credits_used:typeof response?.credits_used==='number'?response.credits_used:null});}
  };
  return {images:{edit:wrap(client.images.edit.bind(client.images),'images.edit')},chat:{completions:{create:wrap(client.chat.completions.create.bind(client.chat.completions),'chat.completions.create')}}};
}
function summarize(record){const credits=record.calls.length===0?0:record.calls.every(c=>c.providerCredits!==null)?record.calls.reduce((n,c)=>n+c.providerCredits,0):null,imageCalls=record.calls.filter(c=>c.operation==='images.edit').length,visionCalls=record.calls.filter(c=>c.operation==='chat.completions.create').length;return {requestId:record.id,elapsedMs:Math.round(performance.now()-record.start),imageCalls,visionCalls,providerCredits:credits,creditBasis:credits===null?'not_returned':'provider_reported',estimatedCredits:imageCalls*788+visionCalls*8,estimateBasis:'user-supplied-average-2026-10-29; image=788, vision=8; not actual debit',upstreamCalls:record.calls};}
export function installMetrics(app,root){app.use((req,res,next)=>{
  if(req.method!=='POST'||!/^\/api\/(?:v1\/)?(?:analyze|generate|generate-body|heads\/right-profile)$/.test(req.path))return next();
  const record={id:randomUUID(),start:performance.now(),calls:[]},send=res.json.bind(res);res.setHeader('X-Request-Id',record.id);
  res.json=body=>{if(res.statusCode<400&&body&&typeof body==='object')body={...body,metrics:summarize(record)};return send(body);};
  res.on('finish',()=>{const summary={createdAt:new Date().toISOString(),path:req.originalUrl.split('?')[0],status:res.statusCode,...summarize(record)};const dir=path.join(root,'data/metrics');mkdir(dir,{recursive:true}).then(()=>appendFile(path.join(dir,'requests.jsonl'),JSON.stringify(summary)+'\n')).catch(()=>console.error('[metrics] write failed'));});
  active.run(record,next);
});}
