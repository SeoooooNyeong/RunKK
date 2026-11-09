# 러닝 GPS 흔들림 완화 설계

> 작성: 2026-11-09 | 다음 판 변경 | 입력: S15P31A207-280, FR-032~FR-034, FR-047, FR-194, A-02

| 항목 | 설계 |
|---|---|
| [임시:D-박-1] 입력 | 폰 `MeasurementService`는 Android 위치의 속도와 속도 오차가 모두 유한하고 0 이상일 때 합을 `LocationSample.speedUpperEstimateMps`로 전달한다. 없는 값은 null이다. |
| 순서 | `LocationFilter`는 정확도 반경과 10초 불안정 규칙을 먼저 적용한다. 불안정 회복점은 받은 좌표로 새 기준을 잡는다. |
| 좌표 고정 | 정상 표본의 속도 상단 추정치가 0.5m/s 이하이고 이전 유효점이 3초 이내이면 직전 좌표를 사용한다. 새 시각을 남겨 재이동 거리·페이스와 서버의 인접점 계산을 이어 간다. |
| 측정 수명 | 이전점은 러닝마다 새로 시작하고 수동 일시정지에서 재개할 때 초기화한다. 시간 역행·동시각은 고정에 쓰지 않는다. |
| 소비자 | `RunTracker`가 필터 결과를 `RunSession`과 지도 경로에 함께 전달한다. 저장 좌표의 시각·신호 상태와 업로드 형식을 유지한다. |
| 기본값 | 속도 판단이 불확실한 표본과 워치 단독은 들어온 좌표를 사용한다. 임계값은 실내 정지와 느린 실외 걸음을 함께 측정해 검증할 초기값이다. |

속도 오차는 Android가 제공하는 68% 수준 추정치다. [Android Location](https://developer.android.com/reference/android/location/Location#getSpeedAccuracyMetersPerSecond())
