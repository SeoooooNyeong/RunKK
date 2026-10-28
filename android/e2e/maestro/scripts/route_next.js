// 시나리오 3 GPS 경로 재생: 처음 부르면 시작 시각을 잡고, 부를 때마다 "지난 시간 × 속도"만큼 간 지점을 output.route에 둔다.
// 반복 한 번이 몇 초 걸리든 실제 이동 속도는 SPEED_MPS(기본 4 m/s ≈ 4분 10초/km, 러닝 속도)로 맞는다.
// 서울시청 앞(37.5665, 126.9780)에서 북동쪽(방위 30도)으로 METERS(기본 1160m)까지 간다.
var speed = parseFloat(typeof SPEED_MPS === 'undefined' ? '4' : SPEED_MPS);
var goal = parseFloat(typeof METERS === 'undefined' ? '1160' : METERS);
var lat0 = 37.5665;
var lon0 = 126.978;
var bearing = 30 * Math.PI / 180;
if (!output.route) output.route = { t0: Date.now() };
var meters = Math.min(goal, speed * (Date.now() - output.route.t0) / 1000);
var north = meters * Math.cos(bearing);
var east = meters * Math.sin(bearing);
output.route.lat = (lat0 + north / 111320).toFixed(7);
output.route.lon = (lon0 + east / (111320 * Math.cos(lat0 * Math.PI / 180))).toFixed(7);
output.route.meters = Math.round(meters);
output.route.done = meters >= goal;
// 약 1초 간격을 둔다. Maestro에는 "기다리기" 명령이 없고, 없는 요소를 기다리게 하면 실패 기록(화면·계층 캡처)이 매번 남아
// adb 통신이 많아진다. 그래서 여기(호스트)에서 기다린다.
var until = Date.now() + parseInt(typeof INTERVAL_MS === 'undefined' ? '1000' : INTERVAL_MS, 10);
while (Date.now() < until) { /* 기다림 */ }
