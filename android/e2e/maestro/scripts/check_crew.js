// 시나리오 5: 만든 크루가 서버에 내 크루(크루장)로 있는지 확인한다.
var res = http.get(SERVER_URL + '/api/v1/crews/me', { headers: { Authorization: 'Bearer ' + output.token } });
if (!res.ok) throw new Error('crews/me status=' + res.status);
var mine = json(res.body);
if (!mine.crew || mine.crew.name !== CREW_NAME || mine.crew.myRole !== 'LEADER') {
    throw new Error('server crew mismatch: ' + res.body);
}
console.log('server crew ok: ' + mine.crew.crewId);
