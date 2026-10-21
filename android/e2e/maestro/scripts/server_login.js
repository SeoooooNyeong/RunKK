// 서버 API로 로그인해 output.token에 접근 토큰을 둔다. 앱과 같은 카카오 스텁 토큰(e2e-<id>)을 쓴다.
// 서버는 스텁에 토큰을 물어 실제 검증 절차를 그대로 거친다(인증 우회 없음).
var res = http.post(SERVER_URL + '/api/v1/auth/login', {
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ provider: 'KAKAO', providerToken: KAKAO_TOKEN })
});
if (!res.ok) throw new Error('login failed status=' + res.status + ' body=' + res.body);
var login = json(res.body);
if (!login.accessToken) throw new Error('login did not return accessToken: ' + login.status);
output.token = login.accessToken;
