// 시나리오 7: 서버 알림함도 비어 있는지 확인한다(앱 화면과 대조).
var res = http.get(SERVER_URL + '/api/v1/notifications', { headers: { Authorization: 'Bearer ' + output.token } });
if (!res.ok) throw new Error('notifications status=' + res.status);
var n = json(res.body);
if (n.items.length !== 0) throw new Error('server notifications=' + n.items.length);
console.log('server inbox empty');
