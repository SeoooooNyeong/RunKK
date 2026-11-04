/* Right head profile only. Body and clothing do not participate in this cache. */
(() => {
  'use strict';
  async function hashBlob(blob){const bytes=await crypto.subtle.digest('SHA-256',await blob.arrayBuffer());return Array.from(new Uint8Array(bytes),v=>v.toString(16).padStart(2,'0')).join('');}
  class AvatarViews {
    constructor({capture,request,decode,signature,update,hash=hashBlob}){Object.assign(this,{capture,request,decode,signature,update,hash});this.entries={};this.active='front';this.busy=false;this.revision=0;this.source=null;}
    setSource(source){if(this.source!==source){this.source=source;this.entries={};this.active='front';this.revision++;}}
    select(direction){this.setSource(this.signature());if(direction!=='front'&&!this.entries[direction])return false;this.active=direction;this.update();return true;}
    async reference(){const blob=await this.capture();return {blob,sourceHash:await this.hash(blob)};}
    async generate(direction){
      if(direction!=='right'||this.busy)throw new Error('우측 머리만 생성할 수 있어요. 다른 작업이 끝난 뒤 다시 눌러 주세요.');
      this.setSource(this.signature());const revision=this.revision;this.busy=true;this.update();
      try{
        const {blob,sourceHash}=await this.reference();
        this.setSource(this.signature());if(revision!==this.revision)throw new Error('정면 모습이 변경됐어요. 현재 모습으로 다시 생성해 주세요.');
        const form=new FormData();form.append('front',blob,'runkk-front-head.png');form.append('userId',localStorage.getItem('runkk-avatar-demo-user'));
        const result=await this.request('/api/v1/heads/right-profile',{method:'POST',body:form});
        if(result.scope!=='head'||result.sourceHash!==sourceHash||result.direction!==direction)throw new Error('우측 머리의 정면 기준 정보가 맞지 않아요. 기존 정면은 유지해요.');
        const image=await this.decode(result.image);
        this.setSource(this.signature());if(revision!==this.revision)throw new Error('생성 중 얼굴이나 코디가 변경돼서 결과를 적용하지 않았어요. 현재 정면으로 다시 생성해 주세요.');
        this.entries[direction]={image,sourceHash,viewId:result.viewId};this.active=direction;return result;
      }finally{this.busy=false;this.update();}
    }
    async restore(){
      if(this.busy)throw new Error('옆모습 작업이 끝난 뒤 다시 눌러 주세요.');
      this.setSource(this.signature());const revision=this.revision;this.busy=true;this.update();
      try{
        const {sourceHash}=await this.reference();
        const found=await Promise.all(['right'].map(async direction=>{
          let result;try{result=await this.request(`/api/v1/heads/right-profile/${sourceHash}?userId=${encodeURIComponent(localStorage.getItem('runkk-avatar-demo-user'))}`);}catch(error){if(error.status===404)return null;throw error;}
          if(result.scope!=='head'||result.sourceHash!==sourceHash||result.direction!==direction)throw new Error('저장된 우측 머리의 기준이 맞지 않아요.');
          return {direction,image:await this.decode(result.image),sourceHash,viewId:result.viewId};
        }));
        this.setSource(this.signature());if(revision!==this.revision)return [];
        const loaded=[];for(const entry of found)if(entry){this.entries[entry.direction]=entry;loaded.push(entry.direction);}return loaded;
      }finally{this.busy=false;this.update();}
    }
  }
  window.RunKKAvatarViews=AvatarViews;
})();
