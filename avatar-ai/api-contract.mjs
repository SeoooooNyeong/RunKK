const defaults={400:'BAD_REQUEST',401:'UPSTREAM_AUTH_FAILED',403:'FORBIDDEN',404:'NOT_FOUND',409:'GENERATION_BUSY',413:'PAYLOAD_TOO_LARGE',422:'BUSINESS_RULE_REJECTED',429:'TOO_MANY_ATTEMPTS',500:'INTERNAL_ERROR',502:'GENERATION_OR_UPSTREAM_FAILED',503:'SERVICE_UNAVAILABLE',504:'UPSTREAM_TIMEOUT'};
const localCodes=new Set(['INVALID_USER_ID','HEAD_NOT_FOUND','BODY_NOT_FOUND','JOB_NOT_FOUND','JOB_NOT_READY','IDEMPOTENCY_CONFLICT','FACE_ASSETS_INCOMPLETE','BAD_REQUEST','SELFIE_QUALITY_REJECTED','SELFIE_QUALITY_UNAVAILABLE','INVALID_FRONT_IMAGE','INVALID_VIEW_REFERENCE','AVATAR_VIEW_NOT_FOUND','AVATAR_VIEW_INVALID','MISSING_FRONT_IMAGE','INVALID_VIEW_DIRECTION','LIMIT_FILE_SIZE','LIMIT_UNEXPECTED_FILE','LIMIT_FILE_COUNT','LIMIT_FIELD_VALUE']);
export function installApiContract(app){
  app.use((req,res,next)=>{
    req.versionedApi=/^\/api\/v1(?:\/|$)/.test(req.url);
    if(req.versionedApi)req.url=req.url.replace(/^\/api\/v1(?=\/|$)/,'/api');
    const send=res.json.bind(res);
    res.json=body=>{
      if(req.versionedApi&&res.statusCode>=400){
        const result={code:localCodes.has(body?.code)?body.code:(defaults[res.statusCode]||'INTERNAL_ERROR'),message:String(body?.message||body?.error||'처리에 실패했어요.')};
        if(body?.quality)result.quality=body.quality;
        return send(result);
      }
      return send(body);
    };
    next();
  });
}
