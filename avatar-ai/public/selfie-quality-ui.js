/* Read-only quality presentation. Does not change the avatar or saved layers. */
(() => {
  'use strict';
  const labels={clear:'선명하게 보임',unclear:'확인 어려움',occluded:'가려져 있어요',unknown:'판단 어려움',adequate:'적절해요',too_dark:'얼굴이 어두워요',too_bright:'얼굴이 너무 밝아요'};
  function render(host,{quality=null,selected=false,busy=false,error=null}={}){
    if(!host)return;
    const make=(tag,text,className)=>{const el=document.createElement(tag);if(text)el.textContent=text;if(className)el.className=className;return el;};
    const status=quality?.status||(error?'error':busy?'checking':'pending');
    host.dataset.status=status;host.replaceChildren();
    const title=make('div',null,'selfie-quality-title');title.append(make('h3','사진 품질 확인'),make('span',({pass:'생성 가능',warning:'안내 확인',retake:'재촬영 필요',checking:'확인 중',error:'확인 실패',pending:'미확인'})[status],'selfie-quality-badge'));host.append(title);
    const message=error||quality?.message||(busy?'얼굴의 눈·코·입과 조명 상태를 확인하고 있어요.':selected?'특징 분석 또는 AI 머리 생성을 누르면 사진 품질을 먼저 확인해요.':'셀카를 선택한 뒤 특징 분석을 누르면 눈·코·입과 얼굴 밝기를 확인해요.');
    host.append(make('p',message,'selfie-quality-message'));
    const grid=make('div',null,'selfie-quality-grid');
    for(const [key,name]of [['eyes','눈'],['nose','코'],['mouth','입'],['lighting','얼굴 밝기']]){
      const value=key==='lighting'?quality?.lighting:quality?.visibility?.[key],cell=make('div');
      cell.append(make('span',name),make('strong',value?labels[value]:'—'));grid.append(cell);
    }
    host.append(grid);
    if(quality?.issues?.length){const list=make('ul',null,'selfie-quality-issues');for(const issue of quality.issues){const item=make('li',issue.message);item.dataset.severity=issue.severity;list.append(item);}host.append(list);}
    host.append(make('p','배경이 아니라 얼굴 상태를 기준으로 확인해요. AI 판정에는 오차가 있을 수 있어요.','selfie-quality-note'));
  }
  window.RunKKSelfieQuality={render};
})();
