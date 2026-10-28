// 시나리오 6: 바꾼 설정(코스 지도 공유 켬)이 서버에 저장됐는지 확인한다.
var res = http.get(SERVER_URL + '/api/v1/me/settings', { headers: { Authorization: 'Bearer ' + output.token } });
if (!res.ok) throw new Error('me/settings status=' + res.status);
var s = json(res.body);
if (s.courseMapShared !== true) throw new Error('server courseMapShared=' + s.courseMapShared);
console.log('server setting ok');
