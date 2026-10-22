// 시나리오 3: 방금 달린 기록이 서버에 올라갔는지(대시보드 누적 거리·횟수) 확인한다.
var res = http.get(SERVER_URL + '/api/v1/dashboard', { headers: { Authorization: 'Bearer ' + output.token } });
if (!res.ok) throw new Error('dashboard status=' + res.status);
var d = json(res.body);
if (!(d.totalRuns >= 1)) throw new Error('server totalRuns=' + d.totalRuns);
if (!(d.totalMeters >= MIN_METERS)) throw new Error('server totalMeters=' + d.totalMeters + ' < ' + MIN_METERS);
console.log('server run ok: runs=' + d.totalRuns + ' meters=' + d.totalMeters);
