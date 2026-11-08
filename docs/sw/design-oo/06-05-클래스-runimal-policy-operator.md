> [런크크 객체지향 분석·설계서](../design-oo.md) 목차 · 이전: [06-04-클래스-crew](06-04-클래스-crew.md) · 다음: [06-06-클래스-표현-인프라-앱-워치-ai](06-06-클래스-표현-인프라-앱-워치-ai.md)

#### 6.1.8 runimal, avatar, reward

v0.1의 한 패키지를 세 패키지로 나눴다(D-33). 아래 명세는 runimal(RunimalProfile ~ RunimalQuery), avatar(Avatar ~ AvatarQuery), reward(RewardWallet ~ RewardWithdrawalListener) 순서다. 세 패키지 사이 연결은 avatar → reward(보유 확인) 하나다.

##### RunimalProfile
| 항목 | 내용 |
|------|------|
| 패키지 | runimal |
| 책임 | 회원의 Runimal 판정 상태를 보관한다 |

속성: memberId(고유), validRunCount(int, 직전 반영 순번), provisionalType(RunimalType, nullable), confirmedType(RunimalType, nullable), ratios(AxisRatios, nullable), judgedAt(LocalDateTime).

| 시그니처 | 설명 | 사후 조건 / 예외 |
|---------|------|-----------------|
| `JudgeTrigger onValidRun(int validRunOrdinal)` | 유효 러닝 순번 반영 | 순번 ≤ validRunCount면 이미 반영한 순번이라 NONE. 아니면 validRunCount < p ≤ 순번인 판정 지점 p를 본다: 10의 배수가 있으면 CONFIRM, 없고 3이 있으면 PROVISIONAL, 둘 다 없으면 NONE(4~9회는 다시 판정하지 않음, Q-47). validRunCount ← 순번. 순번은 running이 정하므로 스스로 세지 않는다. 앞 순번 처리가 실패해 건너뛰어 와도 판정 지점을 놓치지 않는다(D-37, D-40, design-review.md H-05') |
| `void assignProvisional(RunimalType t, AxisRatios r, LocalDateTime now)` | 임시 유형 | onValidRun이 PROVISIONAL을 준 뒤에만 |
| `boolean confirm(RunimalType t, AxisRatios r, LocalDateTime now)` | 확정 유형 | onValidRun이 CONFIRM을 준 뒤에만. 바뀌었으면 true(새 카드 표시) |
| `int remainingToNextJudgement()` | 남은 횟수 | 3 미만이면 3 − count, 아니면 10 − (count mod 10)(FR-154) |
| `Optional<RunimalCardView> card()` | 유형 카드 | 확정 우선, 없으면 임시, 둘 다 없으면 비어 있음 |
| `Optional<RunimalType> currentType()` | | |

##### RunimalType (enum)
상수 16개(3.6.1). 상수마다 displayName, icon(동물 이름 텍스트), description, 4축 AxisLean. `static RunimalType of(Map<RunimalAxis, AxisLean> leans)`.

##### AxisRatios (값)
속성: ratios(EnumMap<RunimalAxis, Double>, A쪽 비율 0~1). `AxisLean dominant(RunimalAxis a, RunimalRule rule)`, `int percentOfDominant(RunimalAxis a)`(카드 "솔로 78%").

##### AxisEvaluator (인터페이스)와 구현 4개
| 시그니처 | 설명 |
|---------|------|
| `RunimalAxis axis()` | 맡은 축 |
| `OptionalDouble ratioOfA(RunSample s, RunimalRule rule)` | A쪽 비율. 판정할 표본이 없으면 비어 있음 |
| `AxisLean lean(double ratioOfA, RunimalRule rule)` | 기준과 비교. 축마다 자기 기준 값(RunimalRule의 companionRatio·shortRatio·sameCourseRatio·habitRatio)을 읽는다. 동행 축은 크루런 비율(1 − ratioOfA)이 기준 미만일 때만 A다(6.2 A-14, v0.8, TF-VC-02, D-68) |
| `default AxisLean leanWithoutSample()` | 표본이 없을 때의 쪽. 기본 A(부록 E E-08). CourseAxisEvaluator만 B(탐험길)로 재정의한다(srs FR-152·FR-166 보완, D-147) (v0.11, 7.13.5 TF-VC-27) |

| 구현 | ratioOfA | A 판정 |
|------|----------|--------|
| CompanionAxisEvaluator | 1 − (최근 한 달 유효 러닝 중 크루런 연결 러닝 비율) | 크루런 비율 < 50% → 솔로런(A), 정확히 50%는 소셜런(B) (srs.md 3.6.1, v0.8, TF-VC-02) |
| DistanceAxisEvaluator | 최근 10회 중 3km 미만 비율 | ≥ 50% → 단거리(A) |
| CourseAxisEvaluator | 최근 10회 중 같은 코스 판정 비율(경로 파기 기록 제외. 비교 대상이 없는 러닝은 같은 코스 아님으로 든다) | ≥ 60% → 단골길(A). 표본 없음 → 비어 있음, leanWithoutSample() = B(탐험길, D-147) (v0.11, 7.13.5 TF-VC-27) |
| HabitAxisEvaluator | 최근 10회 중 시작 시각이 중앙값 ±2시간 안 비율 | ≥ 60% → 루틴런(A) |

##### RunSample (값)
속성: recent(List<SampleRun(recordId, startedAt, distance, sameCourse(Boolean), crewRunLinked)>), lastMonth(같은 형식). `static RunSample of(List<RunSampleRow> recent10, List<RunSampleRow> lastMonth, Map<String, Boolean> courseVerdicts, Set<String> linkedIds)`. RunSampleRow(recordId, startedAt, distance)는 running.RunQueryService.runSamples가 내준 유효 기록 값이고 세 항목을 모두 쓴다(RunRecord 엔티티·범용 RunSummary를 받지 않는다, D-32, D-41).

##### RunimalJudge
속성: evaluators(List<AxisEvaluator>, 4개).

| 시그니처 | 설명 |
|---------|------|
| `Judged judgeProvisional(RunSample s, RunimalRule rule)` | 4축 모두 데이터. 데이터가 없는 축은 비율 0.5와 그 평가기의 leanWithoutSample()로 둔다: 코스 축은 탐험길(B, srs FR-152 보완, D-147), 다른 축은 A(부록 E E-08, 판정 회차 기록이 표본에 들어가 실제로 비지 않는다) (v0.11, 7.13.5 TF-VC-27). v0.10의 "동행·코스는 태그 값 우선" 경로를 지웠다. 결과는 v0.10에서 태그 없는 회원과 같다 (v0.11, FR-152, D-91) |
| `Judged judgeConfirmed(RunSample s, RunimalRule rule)` | 4축 모두 데이터. 표본이 없는 축은 비율 0.5와 leanWithoutSample()(코스 축은 탐험길, D-147 (v0.11, 7.13.5 TF-VC-27), A-14). v0.10의 "코스 표본이 없으면 태그의 코스 분류 값" 경로를 지웠다(태그 없는 회원과 같은 결과, FR-166) (v0.11, FR-152, D-91) |

Judged(record): type(RunimalType), ratios(AxisRatios). v0.11에서 두 연산의 TagSet 매개변수를 지웠다(러너 성향 태그 삭제, FR-119) (v0.11, D-91).

##### CourseSimilarityJudge
`boolean isSameCourse(List<GeoPoint> track, List<List<GeoPoint>> previous, CourseSimilarityRule rule)`: 6.2 알고리즘. 새 포인트의 coverage(80%) 이상이 이전 어느 한 코스의 radius(50m) 안이면 true.

##### CourseVerdict (엔티티)
속성: recordId(고유), memberId, sameCourse(boolean), judgedAt.

##### RunimalService
| 항목 | 내용 |
|------|------|
| 의존 | RunimalProfileRepository, CourseSimilarityService, RunimalJudge, RunSampleReader, OperationPolicy, Clock, RunimalTransactions. MemberQueryService(member)는 지웠다: tagsOf를 부르지 않아 runimal → member는 MemberWithdrawn 수신만 남는다(12.2 D5) (v0.11, FR-152, D-91, CR2-U7) |

| 시그니처 | 설명 |
|---------|------|
| `void onRunSaved(RunRecordSaved e)` | 비동기. 유효만. 경로가 있으면 CourseSimilarityService.judge → RunimalProfile.onValidRun(e.validRunOrdinal) → PROVISIONAL·CONFIRM이면 RunSampleReader.read(memberId, 오늘)로 RunSample 구성 → RunimalJudge(태그 없이) → assignProvisional / confirm. 판정 한 건은 RunimalTransactions.retrying으로 감싼다 (v0.11, FR-152, D-91, CR2-U7) |
| `RunimalStatusView status(long memberId)` | 유형, 남은 횟수, 카드, 16유형 목록 |
| `RunimalCardView shareCard(long memberId)` | 유형이 없으면 NO_TYPE(FR-168) |

##### CourseSimilarityService
`void judge(long memberId, String recordId)`: RunimalService.onRunSaved가 사건의 memberId·recordId를 넘긴다(design-review.md M19). RunQueryService.runSamples(memberId, 30)로 같은 회원의 최근 유효 30회의 recordId를 고르고 RunQueryService.routePoints로 경로를 읽어(경로가 남은 것만) CourseSimilarityJudge.isSameCourse → CourseVerdict 저장. 비교 대상이 없으면 저장하지 않는다(FR-166).

##### RunimalQuery
| 항목 | 내용 |
|------|------|
| 패키지 | runimal (공개 조회 클래스) |
| 책임 | 다른 패키지가 읽을 회원별 Runimal 유형을 내준다 |

`Map<Long, RunimalType> typesOf(Collection<Long> memberIds)`: 확정 우선, 없으면 임시. 유형이 없는 회원은 맵에 없다. web.crew(CrewMembersAssembler, 크루원 목록, D-46)와 profile이 쓴다. crew는 부르지 않는다(design-review.md M15). RunimalProfile 엔티티를 내주지 않는다(design-review.md A23).

##### RunimalWithdrawalListener
`void onMemberWithdrawn(MemberWithdrawn e)`: RunimalProfile, CourseVerdict 삭제(D11의 유형 부분).

##### 구현 보조 클래스 (runimal) (v0.11, CR2-U7)
| 클래스 | 종류 | 책임 | 속성·연산 |
|--------|------|------|-----------|
| RunSampleReader | 패키지 전용 협력 객체 | 축 평가 표본을 묶음 조회로 만든다 | 의존 RunQueryService(running), CourseVerdictRepository, CrewRunParticipationQuery(구현이 없으면 크루런 참가 기록 없음으로 본다). 상수 RECENT_RUNS = 10. `RunSample read(long memberId, LocalDate today)`: 최근 10회 + 최근 한 달(동행 축) + 코스 판정 + 크루런 연결 기록, 조회 4번(6.5 "Runimal 표본"). RunimalService의 Fan-Out이 7을 넘지 않게 표본 조립만 떼었다 |
| RunimalTransactions | 패키지 전용 보조 | 판정 한 건을 새 트랜잭션에서 충돌 재시도한다 | 상수 MAX_ATTEMPTS = 3. `<T> T retrying(Supplier<T> work)`: 같은 회원 기록 두 건이 거의 동시에 저장되면 비동기 수신자 두 개가 같은 판정 상태 행을 만들거나 고쳐 충돌한다(OptimisticLockingFailureException, DataIntegrityViolationException). 다시 읽어 최대 3회, 그래도 충돌이면 그 예외를 던진다. 트랜잭션 틀의 통합 위치는 F.8 P-1(P-1, /sw-design 검증에서 결정) |

##### Avatar
| 항목 | 내용 |
|------|------|
| 패키지 | avatar |
| 책임 | 회원 아바타의 외형 상태를 보관한다 |

속성: memberId(고유), face(Face, nullable), creationCount(int), lastCreatedOn(LocalDate, nullable), bodyShape(BodyShape, 기본 STAGE1), skinTone(SkinTone), equipped(EnumMap<ItemSlot, Long>), selectedBackgroundKey(String, nullable = 기본 배경, 칼럼 avatar.selected_background), unlockedBackgrounds(List<UnlockedBackground>, 표 avatar_background). ItemSlot은 common에 있다(D-33, D-48) (v0.11, FR-201, FR-202, D-107).

| 시그니처 | 설명 | 사후 조건 / 예외 |
|---------|------|-----------------|
| `boolean canCreateSelfie(LocalDate today, SelfieLimit limit)` | 셀카 생성 가능 | 오늘 만든 횟수 < limit.perDay(기본 1회, FR-157) |
| `void applySelfieFace(SdFaceParts parts, LocalDate today)` | 셀카 얼굴 적용 | face = SelfieFace, creationCount + 1, lastCreatedOn = today. 오늘 이미 만들었으면 BusinessRuleException(SELFIE_LIMIT) |
| `void chooseDefault(DefaultCharacter c)` | 기본 캐릭터 | face = DefaultFace(c). 셀카 얼굴이 있어도 바꿀 수 있다(FR-158). (v0.34, CR-154) 진행 중 얼굴 작업의 자동 얼굴 교체를 취소한다(이미 성공한 생성 횟수는 반영) |
| (v0.34) `void beginFaceJob(String requestId)` | 얼굴 작업 예약 | 패키지 전용. Avatar 행 잠금 안에서 진행 중 요청 ID를 둔다(회원별 접수 직렬화, FR-297) |
| (v0.34) `void applyGeneratedHead(String requestId, String jobId, LocalDate today, SelfieLimit limit)` | 생성 얼굴 반영 | 패키지 전용. 생성 횟수를 한 번만 더하고, 진행 중 요청과 같을 때만 face를 head:{회원ID}:{작업ID} 키로 바꾼다(FR-296·FR-297). 옛 selfie: 키·기본 얼굴 키는 그대로 읽힌다(FR-298) |
| `void equip(long itemId, ItemSlot slot)` | 착용 | 같은 부위 기존 아이템 교체. 보유 확인은 AvatarService가 먼저 하고 Avatar는 보유 여부 플래그를 받지 않는다(design-review.md 9.2.1 패키지 안 제어 플래그) |
| `void unequip(ItemSlot slot)` | 해제 | |
| `boolean updateAppearance(BodyShape shape, SkinTone tone)` | 외형 갱신 | 체형이 바뀌면 true(알림 대상) |
| `List<UnlockedBackground> unlock(Collection<Landmark> reached, LocalDateTime now)` | 배경 해금 | reached 중 아직 해금하지 않은 키만 UnlockedBackground(키·이름·단계·거리 스냅숏, unlockedAt = now)로 더하고, 새로 해금한 것만 돌려준다. 해금은 취소하지 않는다(기준값이 바뀌어도 지우지 않는다) (v0.11, FR-201, D-107) |
| `void selectBackground(String key)` | 배경 선택 | 해금하지 않은 키면 BusinessRuleException(BACKGROUND_LOCKED)이고 선택은 그대로 (v0.11, FR-202, D-107) (v0.11 구현 반영, U5-A-7, D-176) |
| `void useDefaultBackground()` | 기본 배경으로 | selectedBackgroundKey = null. Optional 인수(빈 값과 null 두 가지로 기본 배경이 되던 것)를 두 연산으로 나눴다(D-36과 같은 이유). 코드는 BE5-A-11로 맞춘다 (v0.11 구현 반영, U5-A-7, D-176) |
| `AvatarView view(int balance)` | 조회 | 얼굴 없으면 "기본 캐릭터를 고를 수 있다". AvatarView에 backgroundKey(selectedBackgroundKey, null = 기본 배경)를 싣는다 (v0.11, FR-162, FR-202) |

##### UnlockedBackground (엔티티) (v0.11, FR-201, D-107)
| 항목 | 내용 |
|------|------|
| 패키지 | avatar |
| 책임 | 회원이 해금한 랜드마크 배경 하나를 해금 때의 모습으로 보관한다 |

속성: landmarkKey(String, Landmark.key, (회원, 키) 기본 키), landmarkName(String), stage(LandmarkStage 이름), distance(Distance), unlockedAt(LocalDateTime). 해금 때의 스냅숏이라 운영 기준값(LANDMARKS)이 바뀌어도 그대로다. 자료사전 랜드마크배경(랜드마크번호 + 해금일시)의 랜드마크번호는 문자열 키다. 연산 없음(Avatar.unlock만 만든다).

##### Face (인터페이스), SelfieFace, DefaultFace
`String renderKey()`: 앱이 얼굴 이미지를 고르는 키. SelfieFace(parts: SdFaceParts) → 부위 이미지 저장 위치 키, DefaultFace(character: DefaultCharacter) → "default:" + 이름. 아바타를 그리는 쪽은 종류를 묻지 않고 renderKey만 쓴다.

##### SdFaceParts (값), SdFacePartImages (값), FacePartImageStore (포트)
SdFaceParts 속성: parts(EnumMap<FacePart, String 이미지 저장 키>), FacePart = EYES, NOSE, MOUTH, EARS, HAIR. SdFacePartImages 속성: images(EnumMap<FacePart, byte[] PNG>)로 FaceTransformGateway가 돌려주는 변환 결과다. FacePartImageStore(`SdFaceParts save(long memberId, SdFacePartImages images)`, `void deleteByMember(long memberId)`)는 avatar가 정의하고 infra.avatar가 이미지 저장소로 구현한다. 변환된 SD 부위 이미지만 저장하고 원본 셀카는 저장하지 않는다. 저장 키는 api가 만든다(ai 서버는 저장하지 않는다, D-59).

##### AppearanceCalculator
| 시그니처 | 설명 |
|---------|------|
| `BodyShape bodyShape(AppearanceInput in, BodyShapeRule rule)` | in = (최근 7일 유효 횟수, 마지막 유효 러닝 일시, 이전 체형, 오늘). 쉰 날 > 4면 이전 체형.lowerOneStep(), 아니면 BodyShape.ofStage(rule.stageFor(횟수))(FR-163). 단계 번호 1~4 ↔ BodyShape 순서, 시간대 번호 0~4 ↔ SkinTone 순서는 policy와 avatar 사이의 숨은 계약이라 14장 계약 시험으로 지킨다 (v0.8, CR-U01, D-70) |
| `SkinTone skinTone(List<LocalDateTime> recent10Starts, SkinToneRule rule)` | 시간대별 개수 최대, 같으면 가장 최근 러닝의 시간대(FR-164) |

##### AvatarService
| 항목 | 내용 |
|------|------|
| 의존 | AvatarRepository, FaceTransformGateway, RewardService(reward), MemberQueryService(member), OperationPolicy, Clock. 셀카 변환 단계는 SelfieConversion에, 트랜잭션 나누기는 AvatarTransactions에 맡긴다 (v0.11, CR2-U7). (v0.34, CR-154) 셀카 접수·조회는 SelfieJobService로 옮겨 AvatarService에는 chooseDefault·equip·view만 남았고 FaceTransformGateway 의존이 없다(코드 1d1e5d44). 아래 createFromSelfie 행은 동기 변환 때의 기록이다 |

| 시그니처 | 설명 |
|---------|------|
| `AvatarResult createFromSelfie(long memberId, byte[] selfie)` | SQ-11. 변환 단계(한도 검사 → 진행 중 잠금 → 변환 → 재검사·저장·적용)는 SelfieConversion.convert가 한다 (v0.11, CR2-U7). 축소 운영·동의(MemberQueryService.hasConsent(FACE_TRANSFER))·하루 한도(policy.selfieLimit()) 검사 → 회원별 진행 중 잠금(SET NX 30초, 5.3. 잡은 시각 + 30초 정각에 풀려 그때부터 다음 요청을 받는다, v0.10, TF-VC-19). 잠금을 잡지 못한 같은 회원의 두 번째 요청은 BusinessRuleException(SELFIE_IN_PROGRESS, "이미 변환 중") → 409이고 얼굴 변환을 부르지 않으며 횟수를 바꾸지 않는다(v0.8, TF-VC-13) → FaceTransformGateway.transform(selfie) → 성공이면 FacePartImageStore.save(memberId, images)로 저장 키를 받아 applySelfieFace(SdFaceParts), 실패면 FailureReason별 사유 + 기본 캐릭터(횟수 그대로, FR-155). selfie는 어디에도 저장하지 않는다(D-59) |
| `AvatarResult chooseDefault(long memberId, DefaultCharacter c)` | 4종 안인지 enum이 보장 |
| `AvatarView equip(long memberId, List<EquipChange> changes)` | 변경마다: 착용이면 RewardService.owns(memberId, itemId)가 false일 때 ITEM_NOT_OWNED로 거부하고, 참이면 policy.shopCatalog().slotOf(itemId)로 부위를 읽어 Avatar.equip(itemId, slot). 해제면 Avatar.unequip(slot). 거부된 항목은 사유로 모은다 |
| `AvatarView view(long memberId)` | 잔액은 RewardService.balanceOf로 붙인다. 배경 키(backgroundKey)를 싣는다. 배포 전에 이미 넘은 랜드마크의 해금은 이 조회 때 알림 없이 채운다(GET /avatar, D-107 (3)). 채우기는 BackgroundService.backfillReached(memberId, now)를 부른다(같은 패키지) (v0.11, FR-162, FR-201, D-107) |

##### AppearanceService
의존: AvatarRepository, AppearanceCalculator, RunQueryService(running: dailyStats, recentStartTimes), OperationPolicy(policy: BodyShapeRule, SkinToneRule), NotificationService(notification: BODY_SHAPE_CHANGED), Clock (design-review.md M29).

`int refreshAll(LocalDate today)`: batch.avatar의 AppearanceRefreshJob이 0시에 부른다. 회원마다 RunQueryService.dailyStats(최근 7일, 날짜·횟수)·recentStartTimes(memberId, 10)로 AppearanceInput(최근 7일 유효 횟수, 마지막 유효 러닝 일시 = 가장 최근 시작 일시, 이전 체형, 오늘)과 피부색 입력을 만들어 AppearanceCalculator → Avatar.updateAppearance → 체형이 바뀌면 알림(BODY_SHAPE_CHANGED). 바뀐 수 반환.

##### AvatarQuery
| 항목 | 내용 |
|------|------|
| 패키지 | avatar (공개 조회 클래스) |
| 책임 | 다른 패키지가 읽을 회원 아바타 얼굴 키를 내준다 |

`Optional<String> faceKey(long memberId)`: Face.renderKey. 얼굴을 고르지 않았으면 비어 있음. profile이 쓴다. Avatar 엔티티(7필드)를 내주지 않는다(design-review.md A23).

`Optional<String> backgroundKeyOf(long memberId)`: 선택한 배경 키(Avatar.selectedBackgroundKey). 기본 배경이면 비어 있음. profile.ProfileQueryService가 ProfileView.avatarBackgroundKey에 쓴다(12.2 D37) (v0.11, FR-202).

##### BackfillProperties (설정 값, record) (v0.12, U6-3, U6-5)
패키지 avatar. `@ConfigurationProperties(prefix = "runkk.avatar") record BackfillProperties(LocalDateTime backfillCutoff)`. 생성자가 null을 IllegalArgumentException으로 거부한다. BackgroundService.backfillReached가 RunQueryService.validDistanceBefore(memberId, backfillCutoff)로 배포 전 거리만 센다. 값 규칙은 D-176 (5)와 5.4.

##### BackgroundService (v0.11, FR-201, FR-202, D-107)
| 항목 | 내용 |
|------|------|
| 패키지 | avatar |
| 책임 | 랜드마크 배경의 해금·선택 유스케이스를 조정한다 |
| 의존 | AvatarRepository, RunQueryService(running: validDistanceOf, memberIdsSavedBetween, v0.11 검증 D-139), OperationPolicy(policy: landmarkTable), NotificationService(notification: BACKGROUND_UNLOCKED), Clock. 기존 avatar → running·policy·notification 화살표(12.2 D18·D20·D21) 안이라 새 화살표가 없다 |

| 시그니처 | 설명 |
|---------|------|
| `List<UnlockedBackground> unlockReached(long memberId, LocalDateTime now)` | RunQueryService.validDistanceOf(memberId)(누적 유효 거리, v0.11 검증 D-139) ≥ 랜드마크 거리인 개인 단계(DOMESTIC·EAST_ASIA·INTERCONTINENTAL) 랜드마크로 Avatar.unlock. 새로 해금한 것이 있으면 BACKGROUND_UNLOCKED 알림 한 건(여럿이면 가장 먼 것 이름). 새로 해금한 것을 돌려준다 — v0.11 7.13.5: 알림 내용은 새로 해금한 배경 이름을 모두 거리 오름차순으로 적는다(한 건, srs FR-201 보완, D-148) (v0.11, 7.13.5 TF-VC-20) |
| `List<UnlockedBackground> unlockReachedQuietly(long memberId, LocalDateTime now)` | unlockReached와 같은 판정(누적 유효 거리 전체)으로 해금만 하고 알림을 내지 않는다. 이미 해금한 배경은 다시 넣지 않는다. BackgroundUnlockListener.onRunRejudged(RunRecordRejudged, AFTER_COMMIT)가 부른다(재판정은 푸시를 보내지 않는다) (v0.12 구현 반영, CR-11, D-191) |
| `List<UnlockedBackground> backfillReached(long memberId, LocalDateTime now)` | unlockReached와 같은 판정으로 해금만 채우고 알림을 내지 않는다(배포 전에 넘은 랜드마크, D-107 (3)). AvatarService.view와 backgrounds가 부른다. 두 연산은 private 판정 하나를 함께 쓰고 불리언 매개변수로 알림 여부를 고르지 않는다(D-36) (v0.11 정리). 해금 행이 하나도 없는 회원에게만 채우고, 판정 거리는 배포 전 거리 RunQueryService.validDistanceBefore(memberId, cutoff)(cutoff = V25 적용 시각 설정값)로 한정한다. 그 뒤에 넘은 랜드마크는 리스너·보정이 알림과 함께 넣는다(BE5-A-4) (v0.11 구현 반영, D-176) |
| `BackgroundsView backgrounds(long memberId, LocalDateTime now)` | GET /avatar/backgrounds. 먼저 backfillReached로 알림 없이 해금을 채운 뒤(배포 전에 넘은 랜드마크) 목록을 만든다: 현재 운영 기준값의 개인 랜드마크 순서, 그 뒤에 표에서 빠졌지만 이미 해금한 배경(해금 유지). 항목마다 key·name·stage·distance·unlocked·unlockedAt, 그리고 selectedKey |
| `BackgroundsView choose(long memberId, String key)` | PUT /avatar/background(key 있음). Avatar.selectBackground(key)(잠긴 키면 BACKGROUND_LOCKED) → 저장 → 목록. 같은 회원 행 충돌은 다시 시도한다 (v0.11 구현 반영, U5-A-7, D-176) |
| `BackgroundsView useDefault(long memberId)` | PUT /avatar/background(key 없음·null). Avatar.useDefaultBackground → 저장 → 목록. 컨트롤러(web.avatar AvatarController)가 `key == null`로 둘 중 하나를 고른다 (v0.11 구현 반영, U5-A-7, D-176) |
| `int reconcile(LocalDateTime now)` | batch.avatar의 BackgroundReconcileJob(0시 10분)이 부른다. RunQueryService.memberIdsSavedBetween(최근 2일)의 회원마다 unlockReached (v0.11 검증, D-139). 커밋 뒤 수신자 실패를 보정한다 |

배경은 개인 단계 랜드마크 22개만이다(크루 단계 CREW_TOTAL 제외). 앱은 키로 그림을 찾고 모르는 키는 단계별 기본 그림을 쓴다(D-107 (1)).

##### BackgroundUnlockListener (v0.11, FR-201, D-107)
`void onRunSaved(RunRecordSaved e)`: `@Async` + `@TransactionalEventListener(AFTER_COMMIT)`(비동기 수신자, D-129). e.valid일 때만 BackgroundService.unlockReached(e.memberId, now). 실패하면 운영 로그를 남기고 BackgroundService.reconcile이 채운다.

##### BackgroundsView (조회 결과, record) (v0.11, FR-202)
selectedKey(String, null = 기본 배경), backgrounds(List<항목(key, name, stage, distance, unlocked, unlockedAt?)>). web.avatar BackgroundsResponse(F.2.2)로 바꾼다.

##### AvatarWithdrawalListener
`void onMemberWithdrawn(MemberWithdrawn e)`: Avatar 삭제와 FacePartImageStore.deleteByMember(부위 이미지)(D11의 외형 부분). 해금 배경 행(avatar_background)은 avatar 행 삭제의 FK CASCADE로 함께 지워진다 (v0.11, D-107). (v0.34, CR-154) selfie_job 행은 SelfieJobService.onMemberWithdrawn이 SelfieJobRepository.deleteByMember로 지운다. ai(Node) 쪽 얼굴 자료 삭제는 #244·#257 후속 확인이다.

##### SelfieJob (엔티티), SelfieJobService, FaceJobGateway (포트) (v0.34, CR-154, FR-293~FR-298)
얼굴 생성을 ai 비동기 작업으로 바꿨다. 책임·상태도·동시성은 [얼굴비동기-박](얼굴비동기-박.md)이 원본이고 여기에는 기존 명세와 잇는 요점만 적는다.

| 클래스 | 종류 | 책임 | 속성·연산 |
|--------|------|------|-----------|
| SelfieJob | 엔티티(selfie_job, V36) | 사진 요청 하나의 작업 진행과 반영 여부를 기록한다 | requestId(사진 해시 기반, 응답에 내지 않음), memberId, jobId, 접수 날짜, 상태, finished. `accepted(FaceJob)`, `retryAt(now)`, `submissionInProgress(now)`(접수 여부를 모르는 예약은 30초 뒤 다시 낼 수 있다), `finish()`, `missing()`, `view()`. 사진 원본·Data URL은 저장하지 않는다(FR-156) |
| SelfieJobService | 응용 서비스 | 접수·조회와 아바타 반영을 조정한다 | 의존 FaceJobGateway, SelfieJobRepository, AvatarRepository, MemberQueryService(member), OperationPolicy, SelfieFailureStore, AvatarTransactions, Clock. `FaceJob submit(memberId, byte[] image)`(202, FR-293) · `FaceJob status(memberId, jobId)`(상태만, FR-294·FR-295) · `GeneratedHead result(memberId, jobId)`(검증 뒤 짧은 트랜잭션에서 Avatar.applyGeneratedHead, FR-296·FR-297) · `GeneratedHead latest(memberId)` · `GeneratedHead head(viewerId, ownerId, jobId)` · `AvatarBody body(bodyId)`. 외부 호출은 트랜잭션 밖, 회원 상태는 Avatar 행 잠금을 얻은 뒤 읽는다 |
| SelfieJobRepository | 포트 | selfie_job을 읽고 쓴다 | `find(requestId)`, `byJob(memberId, jobId)`, `pending(memberId)`, `save`, `deleteByMember`. 구현 infra.avatar.JpaSelfieJobRepository |
| FaceJobGateway | 포트 | 인증 회원의 ai 얼굴 작업을 부른다 | `submit(memberId, image)`, `status(memberId, jobId)`, `result(memberId, jobId)`, `latest(memberId)`, `body(bodyId)`. 구현 infra.integration.ai.AiServerFaceJobGateway(응답을 얼굴 계약으로 검증) |
| FaceJob, GeneratedHead, AvatarBody | 값(record) | 작업 상태(QUEUED·RUNNING·SUCCEEDED·FAILED)와 오류, 생성 머리(faceImage·eyesImage·hairImage·합성 정보), 몸 묶음 | |

SelfieConversion·SelfieLockStore·FaceTransformGateway·FaceTransformOutcome은 코드에서 빠졌다. SelfieFailureStore는 SelfieJobService가 그대로 쓴다. 5파츠 얼굴(SdFaceParts·FacePartImageStore·FaceImageService)은 기존 자료 조회용으로 남는다(FR-298).

##### FaceImageService (v0.11, CR2-U1, CR2-U7)
| 항목 | 내용 |
|------|------|
| 패키지 | avatar |
| 책임 | 셀카 아바타의 SD 부위 이미지를 내준다 |
| 의존 | FacePartImageStore, MemberQueryService(member) |

`byte[] partImage(long viewerId, String imageKey)`: GET /avatar/parts/face/{ownerId}/{set}/{part}(web.avatar AvatarController). 저장 키 형식("face/{회원}/{묶음}/{부위}")이 아니면 IllegalArgumentException, 주인이 탈퇴했으면 PermissionDeniedException, 이미지가 없으면 BusinessRuleException(FACE_IMAGE_NOT_FOUND). 얼굴은 프로필 공개 수준과 관계없이 보이는 항목이라(비공개도 아바타 얼굴은 보임, Q-69) 탈퇴만 거부한다. PNG 바이트를 돌려준다.

##### 구현 보조 클래스 (avatar) (v0.11, CR2-U7)
| 클래스 | 종류 | 책임 | 속성·연산 |
|--------|------|------|-----------|
| SelfieConversion | 패키지 전용 협력 객체 | 셀카 한 장을 SD 얼굴로 바꿔 아바타에 적용한다 | 상수 LOCK_TTL = 30초(변환 읽기 시간 제한 15초보다 길다), MAX_FAILURES_PER_DAY = 5. 의존 FaceTransformGateway, FacePartImageStore, SelfieLockStore, SelfieFailureStore, AvatarRepository, MemberQueryService(member), AvatarTransactions. `Optional<FailureReason> convert(long memberId, byte[] selfie, LocalDate today, SelfieLimit limit)`: 하루 한도 검사(짧은 읽기, 넘으면 SELFIE_LIMIT) → 하루 실패 5번이면 SELFIE_RETRY_LIMIT → 회원별 진행 중 잠금(못 잡으면 SELFIE_IN_PROGRESS) → 변환(잠금 없이) → 행 잠금·재검사·부위 이미지 저장·적용(짧은 쓰기). 성공이면 비어 있고 실패면 사유(생성 횟수 그대로, FR-155). 셀카 바이트는 저장·로그하지 않는다(FR-156, D-59) |
| AvatarTransactions | 패키지 전용 보조 | 아바타 한 건 단위로 트랜잭션을 나눈다 | `<T> T write(Supplier<T> work)`, `<T> T read(Supplier<T> work)`, `<T> Optional<T> once(String job, Supplier<T> work)`(새 트랜잭션 한 번, 실패하면 로그만 남기고 비어 있음). 셀카 변환 동안 행을 잠그지 않게 하고(5.3), 0시 외형 재계산은 회원마다 따로 감싼다. 트랜잭션 틀의 통합 위치는 F.8 P-1(P-1, /sw-design 검증에서 결정) |
| SelfieLockStore | 포트 | 회원별 셀카 변환 진행 중 잠금을 건다 | `Optional<String> tryLock(long memberId, Duration ttl)`(없을 때만 잡고 토큰, 이미 잡혀 있으면 비어 있음, ttl 지나면 저절로 풀림), `void unlock(long memberId, String token)`(그 토큰의 잠금일 때만 푼다). 구현 infra.avatar.RedisSelfieLockStore(SET NX) |
| SelfieFailureStore | 포트 | 회원별 하루 셀카 변환 실패 횟수를 센다 | `int failuresOn(long memberId, LocalDate day)`, `void recordFailure(long memberId, LocalDate day)`. 구현 infra.avatar.RedisSelfieFailureStore(INCR + 만료 48시간). 실패는 생성 횟수를 쓰지 않아 ai를 끝없이 부르는 것을 막는다 |

##### RewardWallet
| 항목 | 내용 |
|------|------|
| 패키지 | reward |
| 책임 | 회원 한 명의 리워드 지갑을 보관한다 |

속성: memberId(고유), balance(int ≥ 0), ownedItems(List<OwnedItem>), lastAccrualDate(LocalDate, nullable, 가장 최근 적립일), lastAccrualDateAmount(int ≥ 0, 그날 적립 합계), version. v0.10의 accruals(List<RewardAccrual>) 컬렉션 매핑은 지웠다: 지갑을 읽을 때 전체 적립 내역을 읽지 않는다. 적립 내역은 RewardAccrualRepository로만 읽고 쓴다 (v0.11, D-109, L14).

| 시그니처 | 설명 | 사후 조건 / 예외 |
|---------|------|-----------------|
| `Optional<RewardAccrual> accrue(AccrualRequest req, RewardRule rule, int alreadyOnThatDay)` | 적립 | req = (reason, sourceId, baseAmount, at). 적립량 = min(base, dailyCap − alreadyOnThatDay). 0 이하면 비어 있음(기록하지 않는다). 아니면 잔액을 늘리고, req.at의 날짜가 lastAccrualDate 이상이면 집계 칼럼을 고친 뒤(같은 날 더하기, 더 새 날 바꾸기) 저장할 RewardAccrual을 돌려준다(FR-159). 중복 (reason, sourceId) 확인은 호출자가 RewardAccrualRepository.amountFor로 먼저 한다 (v0.11, D-109) (다음 판 변경, [05-26](05-26-설계-이벤트-성.md) [임시:D-성-8]) req.reason이 EVENT면 상한과 alreadyOnThatDay를 보지 않는다: baseAmount ≤ 0이면 비어 있음, 아니면 잔액만 늘리고 집계 칼럼(lastAccrualDate, lastAccrualDateAmount)을 그대로 둔 채 RewardAccrual(baseAmount)을 돌려준다 |
| `OptionalInt accruedOn(LocalDate day)` | 그날 적립 합계 | day가 lastAccrualDate와 같으면 lastAccrualDateAmount, 더 새 날이면 0, 더 옛날(보정 작업의 지난 날짜)이면 비어 있음 → RewardService가 RewardAccrualRepository.sumOn으로 읽는다 (v0.11, D-109) |
| `void purchase(ShopItem item, LocalDateTime now)` | 구매 | 보유면 ALREADY_OWNED, 잔액 < 가격이면 INSUFFICIENT_REWARD. 잔액 차감, 보유 추가 |
| `boolean owns(long itemId)` | | |
| `int balance()` | | |

v0.11에서 지운 연산: `int accruedFor(AccrualReason reason, String sourceId)`(지갑이 내역을 갖지 않는다. RewardService.accruedFor가 RewardAccrualRepository.amountFor로 답한다), `int accrue(AccrualRequest req, RewardRule rule)`(위 세 인자 연산으로 바꿈) (v0.11, D-109).

##### RewardService
| 항목 | 내용 |
|------|------|
| 책임 | 리워드 적립·조회 유스케이스를 조정한다 |
| 의존 | RewardWalletRepository, RewardAccrualRepository (v0.11, D-109), OperationPolicy, Clock |

| 시그니처 | 설명 |
|---------|------|
| `void onRunSaved(RunRecordSaved e)` | 유효만. accrueWithRetry(AccrualSource(memberId, VALID_RUN, recordId, 저장 일시), rule → rule.amountForRun(distance)) (v0.11, CR34-03) |
| `void onBattleJudged(BattleJudged e)` | 승자마다 accrueWithRetry(AccrualSource(winnerId, BATTLE_WIN, battleId, 판정 시각), RewardRule::battleWin). 운영자가 RewardRule을 바꾸면 다음 판정부터 바뀐 값으로 적립한다(design-review.md M13). 정정으로 패가 되어도 회수하지 않는다(제안) (v0.11, CR34-03) |
| `int accrueEvent(long memberId, String sourceId, int amount, LocalDateTime at)` | (다음 판 변경, [임시:D-성-8]) 이벤트 포인트. event.CoachWinPointListener가 부른다. accrueWithRetry(AccrualSource(memberId, EVENT, sourceId, at), rule → amount). sourceId는 「이벤트ID:대상」(예 「1:celebrity-11」). 하루 상한 없이 새 트랜잭션에서 적립하고 실제 적립량을 돌려준다. 같은 근거가 있거나 실패하면 0(실패는 reward_accrual_failed 로그) |
| `int accrueWithRetry(AccrualSource source, ToIntFunction<RewardRule> amountOf)` (패키지 전용) | 새 트랜잭션에서 지갑 행 잠금(findOrCreateForUpdate) → RewardAccrualRepository.amountFor(memberId, reason, sourceId) > 0이면 0 → 그날 합계 = wallet.accruedOn(day), 비면 RewardAccrualRepository.sumOn(memberId, day) → RewardWallet.accrue(source.request(amountOf(현재 rule)), rule, 그날 합계) → 적립 행 저장 → 지갑 저장. 일시 오류(TransientDataAccessException)면 최대 3회 다시 하고, 그래도 실패하면 로그만 남기고 0. 수신자와 RewardReconcileService가 함께 쓴다(M4 결정). 실제 적립량을 돌려준다 (v0.11, CR34-03, D-109) |
| `int accruedFor(AccrualReason reason, String sourceId, long memberId)` | 조회. RewardAccrualRepository.amountFor로 답한다. analysis.RecordQueryService(기록 상세)와 web.battle(대결 결과 화면), RewardReconcileService가 부른다 (v0.11, D-109, CR34-03) |
| `boolean owns(long memberId, long itemId)` | 보유 확인. avatar.AvatarService가 착용 전에 부른다 |
| `int balanceOf(long memberId)` | 잔액 |

##### ShopService
`ShopView shop(long memberId, Optional<ItemSlot> slot)`(판매 중인 아이템과 이미 보유한 아이템 + 보유 여부), `PurchaseResult purchase(long memberId, long itemId)`(ShopCatalog.find가 비면 ITEM_NOT_FOUND, onSale이 false면 ITEM_NOT_ON_SALE → RewardWallet.purchase).

##### AccrualSource (값, record, 패키지 전용) (v0.11, CR34-03, CR2-U7)
속성: memberId(long), reason(AccrualReason), sourceId(String, 기록 ID 또는 대결 ID), at(LocalDateTime, 사건 발생 시각 = 하루 상한의 "그날"). 적립량은 그때의 RewardRule로 정하므로 담지 않는다. `AccrualRequest request(int baseAmount)`: 상한 적용 전 적립량으로 지갑에 넘길 요청을 만든다. 수신자와 보정 작업이 같은 근거를 accrueWithRetry에 넘긴다.

##### AccrualReason (열거) (다음 판 변경, [임시:D-성-8])
VALID_RUN, BATTLE_WIN, EVENT. EVENT는 이벤트 포인트이고 하루 상한을 받지 않으며 그날 합계에도 넣지 않는다. RewardAccrualRepository.sumOn(memberId, day)은 reason <> EVENT만 더한다(JpaRewardAccrualRepository). V35_2가 reward_accrual.ck_reward_accrual_reason에 EVENT를 더한다. RewardReconcileService(보정)는 VALID_RUN, BATTLE_WIN만 다시 적립하고 EVENT는 다루지 않는다.

##### RewardReconcileService (v0.11, CR34-03, CR2-U7)
| 항목 | 내용 |
|------|------|
| 패키지 | reward |
| 책임 | 리워드 적립 누락을 매일 보정한다 |
| 의존 | RewardService, RunQueryService(running: savedBetween), BattleResultService(battle: winsJudgedBetween, BattleWin), MemberQueryService(member: isActive), OperationPolicy. reward → member·running·battle 메서드 호출 화살표다(12.2, CR34-04) |

`int reconcile(LocalDateTime now)`: batch.reward의 RewardReconcileJob이 매일 0시 10분에 부른다. 상수 LOOKBACK_DAYS = 2(전날 전체 + 하루 여유). (오늘 − 2일) 0시부터 now까지 RunQueryService.savedBetween의 유효 기록과 BattleResultService.winsJudgedBetween의 승리를 다시 보고, 근거마다 restore: 탈퇴 회원(MemberQueryService.isActive 거짓)은 지갑을 다시 만들지 않게 건너뛰고, RewardService.accruedFor > 0이면 지갑 잠금 없이 건너뛰고, 아니면 RewardService.accrueWithRetry(source, amountOf). 현재 규칙으로 적립량이 0인 근거(⌊km × perKm⌋ = 0인 짧은 유효 러닝, battleWin = 0)는 지갑 행을 잠그지 않고 건너뛴다. 하루 상한은 사건 발생일(기록 저장 일시, 판정 시각) 기준이다. (회원, 사유, 근거) 고유 제약이 막아 여러 번 돌려도 같다(멱등). 새로 적립한 건수(적립량 > 0)를 돌려준다. 근거 하나의 실패는 로그 뒤 다음 근거로 넘어간다.

##### RewardWithdrawalListener
`void onMemberWithdrawn(MemberWithdrawn e)`: RewardWallet 삭제(D12).

##### 포트 인터페이스 (runimal, avatar, reward가 정의)
| 인터페이스 (패키지) | 시그니처 |
|-----------|---------|
| RunimalProfileRepository (runimal) | `RunimalProfile findOrCreate(long memberId)`, `void save(RunimalProfile)`, `Map<Long, RunimalType> typesOf(Collection<Long>)`, `void deleteByMember(long)` — RunimalQuery만 typesOf를 밖에 내준다 |
| CourseVerdictRepository (runimal) | `void save(CourseVerdict)`, `Map<String, Boolean> findByRecords(Collection<String>)`, `void deleteByMember(long)` |
| CrewRunParticipationQuery (runimal) | `Set<String> linkedRecordIds(long memberId, DateRange range)` — 구현 crew.CrewRunParticipationReader |
| AvatarRepository (avatar) | `Avatar findOrCreate(long)`, `Optional<Avatar> findForUpdate(long)`, `void save(Avatar)`, `Stream<Avatar> streamAll()`, `void deleteByMember(long)` |
| FaceTransformGateway (avatar) | `FaceTransformOutcome transform(byte[] image)` (성공 SdFacePartImages 또는 실패 사유 FailureReason ∈ {NO_FACE, MULTIPLE_FACES, UNSUPPORTED_IMAGE, TOO_LARGE, UPSTREAM_FAILED, TIMEOUT} — ai 서버 FaceRouter의 reason과 같은 이름. 회원 식별자 매개변수 없음) — 구현 infra.integration.ai.AiServerFaceGateway. (v0.34, CR-154) 코드에서 빠지고 아래 FaceJobGateway로 바뀌었다(FR-155의 동기 변환·회원 ID 미전송 대신 회원별 ai 작업 경로) |
| (v0.34) FaceJobGateway, SelfieJobRepository (avatar) | 위 "SelfieJob, SelfieJobService, FaceJobGateway" 표 — 구현 infra.integration.ai.AiServerFaceJobGateway, infra.avatar.JpaSelfieJobRepository (CR-154) |
| FacePartImageStore (avatar) | `SdFaceParts save(long memberId, SdFacePartImages images)`, `void deleteByMember(long memberId)` — 구현 infra.avatar(D-59). `Optional<byte[]> find(String imageKey)`(FaceImageService가 부위 PNG를 읽는다) (v0.11, CR2-U1) |
| SelfieLockStore, SelfieFailureStore (avatar) | 위 avatar 구현 보조 클래스 표 — 구현 infra.avatar.RedisSelfieLockStore, RedisSelfieFailureStore (v0.11, CR2-U7). (v0.34) SelfieLockStore·RedisSelfieLockStore는 코드에서 빠졌다(접수 직렬화는 Avatar 행 잠금) |
| RewardWalletRepository (reward) | `RewardWallet findOrCreateForUpdate(long)`, `void save(RewardWallet)`, `void deleteByMember(long)` |
| RewardAccrualRepository (reward) | `int amountFor(long memberId, AccrualReason reason, String sourceId)`(유일 색인, 없으면 0), `int sumOn(long memberId, LocalDate day)`(색인 idx_reward_accrual_member_accrued, 과거 날짜 합계), `void save(RewardAccrual a)` — 구현 infra.reward.JpaRewardAccrualRepository (v0.11, D-109) |

Repository 구현은 infra.runimal, infra.avatar, infra.reward에 있고 각 패키지 밖에서 쓰지 않는다(ArchUnit 규칙, 13.1).

#### 6.1.9 policy

##### OperationSetting (엔티티)
속성: key(SettingKey, 기본 키), value(String, JSON), version(long), updatedAt.
`static OperationSetting initial(SettingKey key, LocalDateTime now)`: 행이 없는 항목의 첫 변경용. 값은 그 키의 기본값(srs.md 예시값)이고, 첫 변경 이력의 이전 값이 이 기본값이 된다(v0.8, CR-U04).
`OperationSettingHistory change(String newValue, String actorId, LocalDateTime now)`: key.validate(newValue)와 key.validateTransition(value, newValue)를 통과해야 하며 이전 값·새 값을 담은 이력을 돌려준다. 실패하면 BusinessRuleException(VALUE_INVALID)(FR-174). 저장된 이전 값이 깨져 읽을 수 없으면 OperationPolicy가 화면에 쓰는 것과 같이 그 키의 기본값을 이전 값으로 보고 전이를 검사한다. 그래서 깨진 값도 관리툴에서 고칠 수 있다(TC-1029, TC-1033) (v0.8, TF-BE-02, D-71). actorId는 확인된 운영자 계정 문자열이다(policy가 operator를 import하지 않는다).

##### SettingKey (enum, 다형성)
22개 상수(자료사전 기준항목 21개 + OPERATOR_WHITELIST, 부록 C) (v0.8, CR-U02, TF-VC-16). 상수마다 `ValidationResult validate(String json)`와 `Object parse(String json)`을 구현한다. `ValidationResult validateTransition(String beforeJson, String afterJson)`은 기본이 통과이고 SHOP_ITEMS·ITEM_PRICES만 재정의한다(D-44). beforeJson을 읽을 수 없으면 그 키의 기본값으로 바꿔 검사한다("형식 오류"로 거부하지 않는다, v0.8, TF-BE-02). 한 키의 parse는 그 키 하나의 형식만 만든다. 두 키를 합친 값 객체(ShopCatalog의 가격, CrewRunRule)는 OperationPolicy가 합친다(v0.8, CR-U02).

| 상수 | 값 객체 | 검사 |
|------|--------|------|
| MATCH_CONDITION | MatchCondition | tierWindow 0~2, ghostAfter 5~60초 |
| REWARD_RULE | RewardRule | perKm·battleWin·dailyCap > 0 |
| ITEM_PRICES | Map<Long, Integer>(아이템 ID → 가격) | 가격 > 0. 바꿀 때 기존 아이템 ID를 지우지 못한다(validateTransition, D-44). OperationPolicy.shopCatalog()가 SHOP_ITEMS 가격을 이 값으로 덮어 합친다 (v0.8, CR-U02) |
| SHOP_ITEMS | ShopCatalog | 아이템 ID 겹침 없음, 부위가 ItemSlot. 바꿀 때 기존 아이템 ID를 지우거나 부위를 바꾸지 못한다. 판매 중지는 onSale = false(validateTransition, D-44) |
| BODY_SHAPE_RULE | BodyShapeRule | 구간 하한 4개, 첫 하한 0(FR-163의 0회 구간), 엄격한 오름차순, 쉼 일수 ≥ 1 (v0.8, TF-BE-06) |
| SKIN_TONE_RULE | SkinToneRule | 시간대 5개가 24시간을 빈틈없이 나눔 |
| COACHING_RULE | CoachingRule | ratio 0.5~1.0, 기본 bpm 100~220 |
| TIER_BOUNDARY | TierBoundary | 경계 4개 오름차순 |
| RUNIMAL_RULE | RunimalRule | 비율 0~1, 기준 거리 > 0 |
| COURSE_SIMILARITY | CourseSimilarityRule | coverage 0~1, radius > 0, 비교 수 1~100 |
| LANDMARKS | LandmarkTable | 1개 이상, 거리 오름차순, 단계가 LandmarkStage, 개인 단계(DOMESTIC·EAST_ASIA·INTERCONTINENTAL) 랜드마크 1개 이상 (v0.8, TF-BE-01). 원소마다 key가 있고 형식(영문 대문자·숫자·밑줄)을 지키며 표 안에서 고유하다 (v0.11, FR-201, D-107) |
| VIRTUAL_RECORD_RULE | VirtualRecordRule | 열린 구간 점수 > 0, 오프셋 5개 |
| SELFIE_ENABLED, AI_COMMENT_ENABLED | FeatureToggles | true/false |
| SELFIE_LIMIT | SelfieLimit | 양의 정수 |
| LLM_DAILY_LIMIT | LlmCallLimit | 양의 정수 2개(회원, 전체). 전체 ≤ 200(ai 서버 절대 상한 240 ÷ 1.2, D-62) |
| SUPPLEMENT_CAP | SupplementCap | 양의 거리 |
| NOTIFICATION_RETENTION | NotificationRetention | 양의 정수(일) |
| CALORIE_COEFFICIENT | CalorieCoefficient | 10~200 |
| CREW_RUN_LOBBY_OPEN | CrewRunLobby(lobbyOpenBeforeMinutes, reminderBeforeMinutes, minReady) | 모두 양수 (v0.8, CR-U02) |
| CREW_RUN_END_RULE | CrewRunEnd(minutesPerKm) | 양수. OperationPolicy.crewRunRule()이 CREW_RUN_LOBBY_OPEN과 합쳐 CrewRunRule을 만든다 (v0.8, CR-U02) |
| OPERATOR_WHITELIST | OperatorWhitelist | (provider, accountId) 목록, 1개 이상. provider는 "KAKAO"·"GOOGLE"(member.ProviderType 이름과 같은 대문자, policy 안 상수 집합. 소문자·모르는 제공자는 VALUE_INVALID), accountId는 비지 않은 문자열(v0.10, TF-BE-24, D-87). 기본값은 빈 목록이고 첫 운영자는 시작 때 환경 변수로 넣는다(v0.8, CR-U10, D-74). 넣는 클래스는 operator.OperatorBootstrap(6.1.12)이다 (v0.11, CR2-U2) |

자료사전의 21개 기준항목 이름과 상수의 대응은 부록 C.

##### (삭제) OperatorWhitelistSeeder → operator.OperatorBootstrap (v0.11, CR2-U2)
v0.10의 policy.OperatorWhitelistSeeder는 구현에서 operator.OperatorBootstrap(6.1.12)으로 위치·이름이 바뀌었다. 첫 운영자 넣기는 운영자 계정 확인과 같은 책임 묶음이라 operator에 둔다. 의존은 operator → policy(OperationPolicy, OperationSettingService) → audit 방향이라 12.2 D40·D41 안이다. 명세는 6.1.12 OperatorBootstrap 절을 본다.

##### OperationPolicy
| 항목 | 내용 |
|------|------|
| 책임 | 운영 기준값을 형식 있는 값 객체로 내준다 |
| 의존 | OperationSettingRepository |

속성: cache(ConcurrentHashMap<SettingKey, Object>).

| 시그니처 | 설명 |
|---------|------|
| `TierBoundary tierBoundary()`, `MatchCondition matchCondition()`, `RewardRule rewardRule()`, `CoachingRule coachingRule()`, `RunimalRule runimalRule()`, `CourseSimilarityRule courseSimilarityRule()`, `LandmarkTable landmarkTable()`, `VirtualRecordRule virtualRecordRule()`, `ShopCatalog shopCatalog()`, `FeatureToggles features()`, `SelfieLimit selfieLimit()`, `LlmCallLimit llmCallLimit()`, `SupplementCap supplementCap()`, `NotificationRetention notificationRetention()`, `CalorieCoefficient calorieCoefficient()`, `BodyShapeRule bodyShapeRule()`, `SkinToneRule skinToneRule()`, `CrewRunRule crewRunRule()`, `OperatorWhitelist operatorWhitelist()` | 캐시에서 읽는다. 없으면 저장소에서 읽어 parse, 행이 없거나 읽을 수 없으면 srs.md 예시값. shopCatalog()는 SHOP_ITEMS에 ITEM_PRICES 가격을 덮고, crewRunRule()은 CREW_RUN_LOBBY_OPEN과 CREW_RUN_END_RULE을 합친다(v0.8, CR-U02) |
| `void invalidate(SettingKey key)` | 변경 커밋 뒤 캐시 무효화(D-24) |

##### 운영 기준 값 객체 (record)
| 값 객체 | 속성 | 연산 |
|---------|------|------|
| TierBoundary | lowerBounds(실버 30, 골드 35, 플래티넘 40, 마스터 45) | `Tier tierOf(double score)`, `double midScoreOf(Tier t, VirtualRecordRule r)`(열린 구간은 브론즈 27·마스터 48) |
| MatchCondition | tierWindow(int, 기본 0 = 같은 티어), ghostAfter(Duration 15초) | `boolean accepts(Tier a, Tier b)` |
| RewardRule | perKm(10), battleWin(20), dailyCap(200) | `int amountForRun(Distance d)` = ⌊km × perKm⌋ |
| CoachingRule | ratio(0.9), defaultBpm(180), holdSeconds(30), cooldownSeconds(60) | `int thresholdFor(OptionalInt age)` = (220 − 나이) × ratio, 나이 없으면 defaultBpm |
| RunimalRule | companionRatio(0.5), shortDistance(3km), shortRatio(0.5), sameCourseRatio(0.6), habitWindow(2시간), habitRatio(0.6) | 연산 없음. 축 평가기가 자기 구성요소를 읽는다. threshold(RunimalAxis)는 policy → runimal 화살표(순환)를 만들어 두지 않는다 (v0.8, CR-U01) |
| CourseSimilarityRule | coverage(0.8), radiusMeters(50), compareCount(30) | |
| VirtualRecordRule | openLowScore(27), openHighScore(48), paceOffsets(−5%, −2.5%, 0, +2.5%, +5%), distances(1·3·5·10km) | |
| ShopCatalog | items(List<ShopItem>) | `Optional<ShopItem> find(long id)`, `List<ShopItem> bySlot(Optional<ItemSlot>)`, `Optional<ItemSlot> slotOf(long id)`(avatar가 부위만 읽는다, D-41), `boolean allowsTransitionFrom(ShopCatalog before)`(before의 모든 id가 같은 slot으로 남아 있으면 true, D-44) |
| ShopItem | id, name, slot(ItemSlot), price, onSale(boolean, 기본 true) | 판매 중지는 onSale = false. 지우지 않는다 |
| FeatureToggles | selfieEnabled(true), aiCommentEnabled(true) | 축소 운영 두 스위치(plan.md 3.1.1) |
| SelfieLimit | perDay(1) | |
| LlmCallLimit | perMember(1), global(200) | |
| SupplementCap | cap(Distance 10km) | |
| NotificationRetention | days(30) | `LocalDateTime cutoff(LocalDateTime now)` = now − days. `boolean isExpired(LocalDateTime occurredAt, LocalDateTime now)` = occurredAt ≤ cutoff(now)(경과 ≥ days, 정각에 만료, srs.md 3.0.4). NotificationService의 조회·열기·정리가 모두 cutoff 하나로 경계를 정한다(v0.10, TF-BE-23, TF-VC-17, D-81) |
| CalorieCoefficient | kcalPerKm(65) | `int estimate(Distance d)` = round(kcalPerKm × km)(FR-079) |
| BodyShapeRule | bands(하한 0, 1, 3, 5), restDaysBeforeDecline(4) | `int stageFor(int runsIn7Days)`: 단계 번호 1~4. avatar가 BodyShape 순서로 바꾼다(policy → avatar 화살표를 만들지 않는다, v0.8, CR-U01) |
| SkinToneRule | bands(새벽 04~07, 아침 07~11, 낮 11~17, 저녁 17~21, 밤 21~04) | `int toneOf(LocalTime t)`: 시간대 번호 0~4. avatar가 SkinTone 순서로 바꾼다 (v0.8, CR-U01) |
| CrewRunRule | lobbyOpenBefore(30분), reminderBefore(10분), minReady(2), minutesPerKm(15). 두 키 값을 OperationPolicy가 합친 것(v0.8, CR-U02) | `LocalDateTime closesAt(LocalDateTime departedAt, StandardDistance d)` |
| OperatorWhitelist | accounts(Map<String, Set<String>>: 제공자 이름 "KAKAO"·"GOOGLE" → 계정 ID) | `boolean contains(String provider, String accountId)`. member.SocialAccount를 받지 않아 policy → member 화살표가 없다 (v0.8, CR-U01) |

##### LandmarkTable, Landmark, LandmarkStage
LandmarkTable 속성: landmarks(List<Landmark>, 거리 오름차순). Landmark(record): key(String, 영문 대문자·숫자·밑줄, 표 안에서 고유), stage(LandmarkStage), name, distance(Distance, roundTrip이면 srs.md 3.3.1 표의 왕복 거리 그대로, v0.8, CR-U05), roundTrip(boolean). key는 배경 해금의 고정 식별자다: 순번·이름은 표 수정·번역 때 어긋나므로 쓰지 않는다. 기본 키는 D-107 (1)의 목록(INCHEON … EARTH_LAP, 크루 단계 EARTH_2_LAPS … KM_1M)이다. 기존 운영 기준값 행은 V25가 원소마다 key를 붙인다(기본 표 이름이면 D-107의 키, 아니면 `'LM_' || 순번`) (v0.11, FR-201, D-107).

| 시그니처 | 설명 |
|---------|------|
| `Optional<LandmarkProgress> personalProgressOf(Distance total)` | 개인 누적 거리용. 1~3단계(DOMESTIC, EAST_ASIA, INTERCONTINENTAL) 랜드마크만 본다. 그 단계 랜드마크가 없으면 비어 있음(대시보드 랜드마크 항목 없음, 예외 없음, TC-1014) (v0.8, TF-BE-01) |
| `Optional<LandmarkProgress> crewProgressOf(Distance total)` | 크루 누적 거리용. 1~4단계(CREW_TOTAL 포함)를 본다. 표가 비면 비어 있음 |

두 연산은 볼 단계 범위만 다르고 같은 비공개 연산 progressAmong(후보 랜드마크, total)을 쓴다: 가장 최근에 넘은 랜드마크를 찾아 그 stage.label(...)로 문구를 만들고, 넘은 것이 없으면 첫 랜드마크까지 진행률이다(Q-77, srs.md 3.3.1). v0.2는 범위 열거형 매개변수 하나로 단계 범위를 골라 호출자가 피호출자의 내부 갈래를 정하는 제어 결합이었다(design-review.md 9.2.1 D32, R1, D-41).

LandmarkStage(enum, 다형성) 상수별 `String label(Landmark passed, Distance total)`:
| 상수 | 문구 |
|------|------|
| DOMESTIC | "서울에서 ○○까지 달렸어요! (왕복 n회)", n = ⌊total ÷ distance⌋(distance가 이미 왕복 거리, v0.8, CR-U05) |
| EAST_ASIA, INTERCONTINENTAL(편도) | "서울에서 ○○까지 달렸어요!" |
| INTERCONTINENTAL(지구 둘레 이후) | "지구 n바퀴째 도는 중!" |
| CREW_TOTAL | 380,000km부터 "달나라를 향해 달리는 중! (달까지 n%)", 384,400km부터 "달에서 돌아오는 중 (n%)", 768,800km부터 "달까지 다녀왔어요! (달 왕복 n회)" |

`if (단계 == …)` 분기를 표기 문구 생성 한 곳에만 두지 않고 상수가 문구를 만든다. 단계별 문구가 바뀌어도 LandmarkTable은 바뀌지 않는다.

##### OperationSettingService
| 항목 | 내용 |
|------|------|
| 패키지 | policy |
| 책임 | 운영 기준값 변경 유스케이스를 조정한다 |
| 의존 | OperationSettingRepository, OperationPolicy, AuditService, DomainEventPublisher, Clock |

| 시그니처 | 설명 |
|---------|------|
| `SettingChangeResult change(String actorId, SettingKey key, String value)` | OperationSetting.change(값 검사, 바꾸기 전후 검사 validateTransition) → 이력 저장 → 감사(SETTING_CHANGE, 전후 값, 행위자 actorId·OPERATOR) → 커밋 뒤 OperationPolicy.invalidate(key) → OperationSettingChanged(key, now) 발행(FR-174). TIER_BOUNDARY·VIRTUAL_RECORD_RULE이면 battle.VirtualRecordService가 사건을 받아 가상 기록을 다시 만든다(FR-104, Q-86, D-45). policy는 battle을 부르지 않는다(D-34) |

actorId는 web.admin의 OperatorCheckInterceptor가 확인하고 web.admin.setting이 요청 속성으로 받아 넘긴 운영자 계정이다(D-50). 결과 SettingChangeResult(key, 이전 값, 새 값)는 policy 소유다(R29). v0.1 관리툴 패키지의 기준값 변경 서비스를 옮긴 것이다.

##### OperationSettingChanged (사건, record)
policy가 발행한다(D-31). 항목: key(SettingKey), at(LocalDateTime). 바뀐 값은 싣지 않는다. 수신자는 OperationPolicy에서 새 값을 읽는다.

##### OperationSettingRepository
`Optional<OperationSetting> find(SettingKey)`, `OperationSetting save(OperationSetting)`, `void saveHistory(OperationSettingHistory)`, `List<OperationSetting> findAll()`. 구현 infra.policy.JpaOperationSettingRepository.

#### 6.1.10 notification

##### Notification (엔티티)
속성: id, recipientId, type(NotificationType), content(String), occurredAt, read(boolean), target(NavigationTarget, nullable).

| 시그니처 | 설명 |
|---------|------|
| `void markRead()` | 읽음 |
| `boolean isExpired(LocalDateTime now, NotificationRetention retention)` | retention.isExpired(occurredAt, now) (기본 30일, 30일 정각부터 참, v0.10 D-81) |

##### NotificationType (enum)
12개: PLAN_MISSED, BODY_SHAPE_CHANGED, JOIN_REQUESTED, JOIN_DECIDED, JOIN_EXPIRED, LEADER_TRANSFERRED, CREW_DELETED, KICKED, CREW_RUN_RECRUIT, CREW_RUN_SOON, OPPONENT_ARRIVED, BATTLE_CORRECTED (자료사전 알림종류 12개와 1:1). 상수별 `Screen defaultScreen()`(코치 현황, 아바타, 크루 관리, 크루 홈, 크루런 모집, 대결 결과 등, FR-178).

v0.11에서 9개를 더해 21개다(괄호는 defaultScreen) (v0.11, FR-175, FR-178, FR-218):

| 상수 | defaultScreen | 보내는 곳 |
|------|---------------|-----------|
| CREW_NOTICE | CREW_NOTICE | crew.CrewNewsNotifier(공지) |
| CREW_POLL | CREW_POLL | crew.CrewNewsNotifier(투표) |
| POLL_CLOSING_SOON | CREW_POLL | crew.CrewPostService.remindClosingPolls(마감 1시간 전) |
| CREW_SCHEDULE | CREW_SCHEDULE | crew.CrewNewsNotifier(대면 모임) |
| CREW_CHAT | CREW_CHAT | crew.chat.CrewChatService(방 밖 크루원, notifyCollapsing) |
| CREW_GOAL_ACHIEVED | CREW_HOME | crew.WeeklyRaceService(공동 목표 달성) |
| MANAGER_CHANGED | CREW_HOME | crew.CrewService(운영진 지정·해제 대상) |
| BACKGROUND_UNLOCKED | AVATAR_BACKGROUND | avatar.BackgroundService.unlockReached |
| REPORT_RESOLVED | CREW_HOME | report.ReportService.hide·dismiss(그 사례의 열린 신고자, v0.11 검증 D-138). 기본 화면은 srs FR-178·FR-216 보완으로 확정했다(E-28 닫음). targetId = 사례의 crewId(D-149) (v0.11, 7.13.5 TF-VC-24) |

##### Screen (enum)
기존 6개(COACH_PROGRESS, AVATAR, CREW_MANAGE, CREW_HOME, CREW_RUN_RECRUIT, BATTLE_RESULT) + v0.11 5개(CREW_NOTICE, CREW_POLL, CREW_SCHEDULE, CREW_CHAT, AVATAR_BACKGROUND) = 11개. 자료사전 이동대상이다. 앱 core.navigation Routes에 같은 5개를 더한다 (v0.11, FR-178). CREW_BOARD는 가는 알림종류가 없어 지웠다(srs FR-178 보완, D-150) (v0.11, 7.13.5 TF-VC-24).

##### NavigationTarget (값, record)
속성: screen(Screen), targetId(String, nullable).

##### NotificationRequest (값, record)
속성: recipientId, type, content, target. 생성자에서 content가 비면 IllegalArgumentException.

##### PushDevice (엔티티)
속성: token(String, 기본 키), memberId(long), registeredAt(LocalDateTime). 연산: `static PushDevice register(long memberId, String token, LocalDateTime now)`, `void reassign(long memberId, LocalDateTime now)`(같은 토큰이 다른 회원으로 다시 오면 주인을 바꾼다. 기기를 넘겨받은 경우). 회원 ID와 FCM 등록 토큰의 대응은 notification이 소유하고 탈퇴 때 지운다(D-43, design-review.md H-16).

##### NotificationService
| 항목 | 내용 |
|------|------|
| 책임 | 알림 저장·발송·조회 유스케이스를 조정한다 |
| 의존 | NotificationRepository, PushDeviceRepository, MemberQueryService(member), PushGateway, OperationPolicy, Clock |

| 시그니처 | 설명 |
|---------|------|
| `void notify(NotificationRequest req)` | SQ-14. MemberQueryService.pushDecision(recipientId, type.name())이 WITHDRAWN이면 버림 → 알림함 저장(수신 설정과 무관, W-03) → ALLOWED면 PushDeviceRepository.tokensOf(recipientId)의 토큰으로 PushGateway.send(MUTED면 저장만). 실패는 운영 로그만(재시도 없음). PushResult의 무효 토큰은 deleteTokens로 지운다. Member 엔티티를 받지 않는다(design-review.md A62) |
| `InboxView inbox(long memberId, Optional<DateRange> range)` | 최신순. 화면이 오늘·이번 주·그 전으로 묶는다. 보관 기간(policy.notificationRetention())이 지난 알림은 정리 작업 전이어도 돌려주지 않는다(occurredAt > retention.cutoff(now)인 알림만. 30일 정각 알림은 안 보인다) (v0.8, TF-VC-12, D-67, v0.10, TF-VC-17, D-81) |
| `void notifyCollapsing(NotificationRequest req)` | 묶음 알림(CREW_CHAT). 같은 수신자·같은 종류·같은 대상(target.targetId = crewId)에 읽지 않은 알림이 있으면 새 행을 만들지 않고 그 행의 내용·일시를 바꾼다. 없으면 notify와 같이 저장한다. 수신 설정(pushDecision, FR-175)은 notify와 같이 확인하고, 푸시는 FCM collapse key `crew_chat_<crewId>`로 보내 기기 알림도 한 건으로 묶는다. 메시지마다 알림 행을 만들면 알림함이 채팅으로 덮인다(FR-218). 앱은 열린 채팅방의 크루면 표시하지 않는다(data.push OpenChatRoomTracker). 푸시는 PushGateway.sendCollapsing(tokens, type, content, collapseKey)로 보내고, 읽지 않은 같은 알림의 고쳐 쓰기·새 행은 NotificationRepository.saveCollapsing(req, occurredAt) 한 연산이 맡는다(조회 뒤 저장이 아니라 SQL 한 묶음이라 동시 요청에도 읽지 않은 행이 하나) (v0.11, FR-218, D-102) (v0.11 구현 반영, U5-A-2, D-172) |
| `NavigationTarget open(long memberId, long notificationId)` | 본인 알림만 → markRead. 없는 알림ID(보관 기간이 지나 inbox가 거른 알림 포함)는 BusinessRuleException(NOTIFICATION_NOT_FOUND, "알림 없음") → 409, 남의 알림은 PermissionDeniedException → 403. 읽음 변경 없음 (v0.8, TF-VC-11) |
| `void registerDevice(long memberId, String token)` | PUT /me/push-token. PushDevice를 저장하거나(같은 토큰이면 주인 갱신) 그대로 둔다. 앱 domain.notification.PushTokenSync가 로그인 뒤·토큰 갱신 때 부른다(D-43) |
| `int purgeExpired(LocalDateTime now)` | policy.notificationRetention()이 지난 알림 삭제(occurredAt ≤ retention.cutoff(now). inbox가 거르는 집합과 같다, v0.10, TF-BE-23, D-81). batch.notification의 NotificationPurgeJob이 매일 0시(Asia/Seoul)에 부른다(srs.md 6.3 7.3, FR-175) (v0.8, TF-VC-12, D-67) |

##### PushGateway (인터페이스)
`PushResult send(List<String> deviceTokens, NotificationType type, String content)`, `PushResult sendCollapsing(List<String> deviceTokens, NotificationType type, String content, String collapseKey)`(같은 키의 기기 알림을 하나로 묶는다, CREW_CHAT, v0.11 D-102). PushResult(record: sentCount, invalidTokens). 구현 infra.integration.push.FcmPushGateway는 받은 토큰으로 보내기만 하고 토큰 표를 갖지 않는다. 회원 ID를 받지 않는다(D-43).

##### 운영자 공지 (다음 판 변경, [임시:CR-성-6], 설계 [05-27](05-27-설계-공지알림-성.md))
NotificationType에 `NOTICE(Screen.EXTERNAL_LINK)`를 더해 22개, Screen에 `EXTERNAL_LINK`를 더해 12개다. NOTICE는 MemberSettings.NOTIFICATION_TYPES 밖이라 끌 수 없다([임시:D-성-21]). Notification에 `title`(공지 제목, 60자, null 허용), targetId 길이 300(V35_3). NotificationRequest에 다섯째 구성요소 `title`(네 인자 생성자 유지). InboxView.Item에 `title`. NotificationService에 패키지 전용 `boolean deliver(NotificationRequest)`(notify의 본문, 저장했으면 참). PushGateway에 기본 메서드 `send(tokens, type, title, content)`(FcmPushGateway가 제목을 알림 제목으로). 새 `NoticeService.send(String actorId, NoticeInput): NoticeResult`(1~100명, 구글 폼 링크 검사, 회원마다 deliver, 감사 SETTING_CHANGE 「NOTICE:제목」), 값 `NoticeInput(memberIds, title, body, linkUrl)`, `NoticeResult(requested, stored, skippedMemberIds)`. 의존 notification → audit이 는다([임시:D-성-20], [임시:D-성-23]).

##### NotificationRepository, PushDeviceRepository, NotificationWithdrawalListener
NotificationRepository: `save`, `List<Notification> findByRecipient(long, Optional<DateRange>, LocalDateTime occurredAfter)`(보관 기간 하한, v0.8 D-67), `Optional<Notification> findById(long)`, `void saveCollapsing(NotificationRequest req, LocalDateTime occurredAt)`(묶음 알림 저장: 읽지 않은 같은 수신자·종류·대상 행을 고쳐 쓰고, 없으면 INSERT … ON CONFLICT DO NOTHING, 넣지 못했으면(같은 순간 다른 요청이 넣음) 다시 고쳐 쓴다. 예외 없이 끝나 부르는 트랜잭션이 깨지지 않는다. V23 부분 유일 색인 uq_notification_unread_collapse (recipient_id, type, target_id) WHERE is_read = FALSE AND type = 'CREW_CHAT' AND target_id IS NOT NULL이 동시 중복을 막는다. 처음 설계한 findUnread는 두지 않는다) (v0.11 구현 반영, U5-A-2, D-172), `int deleteOlderThan(LocalDateTime)`, `void deleteByRecipient(long)`. PushDeviceRepository: `void save(PushDevice)`, `Optional<PushDevice> findByToken(String)`, `List<String> tokensOf(long memberId)`, `void deleteTokens(Collection<String>)`, `void deleteByMember(long)`. 구현은 infra.notification의 JpaNotificationRepository, JpaPushDeviceRepository이고 notification 밖에서 쓰지 않는다. NotificationWithdrawalListener.`onMemberWithdrawn(e)`: 그 회원 알림 삭제(D17)와 기기 토큰 삭제(PushDeviceRepository.deleteByMember, FR-021).

#### 6.1.11 audit

##### AuditRecord (엔티티, 불변)
속성: id, at, actorId(String: 회원ID 또는 운영자 계정), actorRole(ActorRole), action(AuditAction), targetId(String), before(String, nullable), after(String, nullable). 변경 메소드가 없다. 생성은 `static AuditRecord of(AuditEntry e, LocalDateTime at)`만 한다. `AuditRecordView view()`(패키지 전용)가 조회 투영을 만든다. getter를 열지 않고 AuditService.query가 이것으로 바꾼다(v0.8, CR-U07).

##### AuditEntry (값, record)
속성: actorId, actorRole, action, targetId, before, after.

##### AuditAction (enum)
11개: SIGN_UP_CONSENT, CONSENT_CHANGE, ROUTE_DISPOSAL, WITHDRAWAL, LEADER_TRANSFER, CREW_DELETE, KICK, POST_FORCE_DELETE, CELEBRITY_REGISTER, CELEBRITY_DELETE, SETTING_CHANGE (자료사전 행위종류 11개와 1:1). 심박 원자료 파기(FR-029)는 ROUTE_DISPOSAL과 같은 위치정보법상 대상은 아니지만 FR-029가 감사 기록을 요구하므로 ROUTE_DISPOSAL의 before 값에 "HEART_RATE"를 적어 구분한다 **(제안, 부록 E E-09)**.

구현에서 3개가 더해져 v0.10 코드 기준 14개다: OPERATOR_LOGIN_SUCCESS, OPERATOR_LOGIN_FAILURE(운영자 로그인, V16, 행위자 = 운영자 계정 또는 "제공자:UNKNOWN", 대상 = 접속지, 변경 후 = SUCCESS 또는 실패 사유) (v0.11, CR34-06), RANKING_RECOMPUTED(운영자 랭킹 다시 계산, V18, 대상 = "RANKING", 변경 후 = 집계 시각·개인·크루 건수·저장 실패 수) (v0.11, CR2-U1). v0.11에서 5개를 더해 19개다: MANAGER_APPOINT, MANAGER_DISMISS(크루 운영진 지정·해제, FR-204), CHAT_FORCE_DELETE(남의 채팅 메시지 삭제, FR-213), REPORT_HIDE, REPORT_DISMISS(운영자 신고 처리, 대상 caseId, FR-216). 남의 게시글 삭제는 기존 POST_FORCE_DELETE를 쓴다 (v0.11, FR-204, FR-213, FR-216). 자료사전 행위종류(11개)와의 1:1 대응이 깨지므로 srs.md 다음 판 자료사전에 행위종류 8개를 더하도록 요청한다(부록 E E-33). v0.11 7.13.5: srs v0.10 보완으로 자료사전 행위종류가 15개(채팅강제삭제·운영진지정·운영진해제·신고처리 포함)가 되어 CHAT_FORCE_DELETE·MANAGER_APPOINT·MANAGER_DISMISS는 1:1, 신고처리는 REPORT_HIDE·REPORT_DISMISS 둘, 미이용 게스트 파기는 WITHDRAWAL + ActorRole.SYSTEM(D-118)이다. 자료사전에 없는 것은 OPERATOR_LOGIN_SUCCESS·OPERATOR_LOGIN_FAILURE·RANKING_RECOMPUTED 셋과 신고처리의 둘 나눔이다(E-33) (v0.11, 7.13.5 TF-VC-26, TF-VB-23)

##### ActorRole (enum)
RUNNER, CREW_LEADER, CREW_MANAGER, OPERATOR, SYSTEM. CREW_MANAGER는 v0.11 신규다: 운영진이 남의 콘텐츠를 지운 감사(POST_FORCE_DELETE, CHAT_FORCE_DELETE)의 행위자 역할이다 (v0.11, FR-213, D-98).

##### AuditService
| 시그니처 | 설명 |
|---------|------|
| `void record(AuditEntry e)` | 추가만. 호출한 트랜잭션 안에서 함께 커밋(감사 누락 방지) |
| `List<AuditRecordView> query(DateRange range, Optional<AuditAction> action)` | 최신순. 엔티티 AuditRecord 대신 읽기 전용 투영 AuditRecordView(at, actorId, actorRole, action, targetId, before, after)를 돌려준다(D-32, design-review.md D52 권고). web.admin의 운영자 확인(OperatorCheckInterceptor → OperatorAuthorizer.ensureOperator) 뒤 web.admin.audit이 바로 부른다(FR-177, D-50). v0.1의 감사 조회 전용 서비스는 이 연산과 같아 없앴다 |
| `int purgeExpired(LocalDateTime now)` | 기록일 + 6개월이 지난 것 삭제(FR-176, NFR-035) |

AuditRepository: `void append(AuditRecord)`, `List<AuditRecord> find(DateRange, Optional<AuditAction>)`, `int deleteOlderThan(LocalDateTime)`. update 메소드를 두지 않는다.

#### 6.1.12 operator

operator 패키지의 책임은 "운영자 계정을 확인한다"이다. 의존은 member(SocialLoginGateway, ProviderType), policy(OperatorWhitelist, OperationSettingService)와 audit(운영자 로그인 감사)이다. operator → audit 화살표는 v0.11에서 12.2에 더한다 (v0.11, CR34-04, CR34-06, CR2-U2). 관리툴 토큰 발급·검증은 operator가 정의한 포트 OperatorTokenIssuer로 하고 infra.integration.auth가 구현한다(D-42). v0.1 관리툴 패키지의 세 서비스는 데이터를 가진 패키지로 옮기거나 없앴다: 유명인 기록 → battle.CelebrityRecordService(6.1.6), 기준값 변경 → policy.OperationSettingService(6.1.9), 감사 조회 → audit.AuditService.query(6.1.11)를 web.admin이 바로 부른다(D-34).

##### OperatorId (값, record)
속성: account(SocialAccount). 운영자 화이트리스트 항목 하나다. `String actorId()`는 감사 기록·서비스 호출에 넘길 운영자 계정 문자열이다.

##### OperatorAuthorizer
| 항목 | 내용 |
|------|------|
| 패키지 | operator |
| 책임 | 운영자 화이트리스트를 검사한다 |
| 의존 | SocialLoginGateway(member), OperatorTokenIssuer(operator), OperationPolicy(policy), AuditService(audit) (v0.11, CR34-06, CR34-04) |

| 시그니처 | 설명 |
|---------|------|
| `OperatorSession authenticate(String provider, String providerToken, String client)` | v0.11: 세 번째 인수 client(접속지 문자열, web.admin이 web.attempt.ClientAddress로 정한다, v0.11 검증 D-132)를 더했다. 성공·실패를 모두 감사 기록으로 남긴다: 성공은 OPERATOR_LOGIN_SUCCESS(행위자 운영자 계정, ActorRole.OPERATOR, 대상 client, 변경 후 "SUCCESS"), 실패는 OPERATOR_LOGIN_FAILURE(행위자 확인한 계정 또는 "제공자:UNKNOWN"·"UNKNOWN", 변경 후 실패 사유 UNKNOWN_PROVIDER·AUTH_FAILED·PROVIDER_UNAVAILABLE·NOT_WHITELISTED). 성공 감사를 남기지 못하면 토큰을 주지 않는다. 실패 감사 저장이 실패해도 원래 거부 사유를 가리지 않는다(오류 로그만). 제공자 무응답은 BusinessRuleException(PROVIDER_UNAVAILABLE) (v0.11, CR34-06). 이하 v0.10 설명: web.admin의 운영자 로그인 요청(AdminAuthController, D-50). provider는 문자열("KAKAO"·"GOOGLE")로 받아 operator 안에서 member.ProviderType으로 바꾼다(모르는 값이면 IllegalArgumentException). web.admin이 member 형식을 import하지 않게 하려는 것이다(design-review.md R26, E13). SocialLoginGateway로 계정 확인 → 화이트리스트(policy.operatorWhitelist().contains(제공자 이름, 계정 ID))에 있으면 OperatorTokenIssuer.issue(OperatorId)로 관리툴 토큰을 발급해 OperatorSession(adminToken, actorId)을 돌려주고, 없으면 PermissionDeniedException → 403 (SRS 보완 요청 R-05). 소셜 인증 자체가 실패하면(제공자 토큰 만료·위조) member의 AuthFailureException을 잡아 operator의 OperatorAuthFailedException으로 바꿔 올리고, AdminAuthController(web.admin)가 이를 401로 바꾼다. 토큰을 발급하지 않으며 응답에 제공자 토큰을 담지 않는다(TC-3872). member 예외를 그대로 올리면 web.admin이나 web.error가 member를 import해야 해 R26이 없앤 화살표가 되살아나므로 operator 경계에서 바꾼다(v0.8 검증, R36). 모르는 제공자 문자열은 400 (v0.8, TF-VC-10, D-76) |
| `OperatorId ensureOperator(String adminToken)` | web.admin의 OperatorCheckInterceptor가 /admin/* 요청마다(로그인 제외) 컨트롤러보다 먼저 부른다(D-50). OperatorTokenIssuer.verify(adminToken)로 OperatorId를 얻고(위조·만료면 PermissionDeniedException), 화이트리스트에서 빠졌으면 거부(FR-173). 돌려준 OperatorId.actorId()를 요청 속성 actorId로 붙이고, web.admin.celebrity·setting이 CelebrityRecordService·OperationSettingService에 넘긴다 |

##### OperatorTokenIssuer (포트 인터페이스)
| 시그니처 | 설명 |
|---------|------|
| `String issue(OperatorId operator)` | 관리툴 토큰 발급(만료 = 발급 + 8시간 **(제안)**) |
| `OperatorId verify(String adminToken)` | 서명·만료 확인 뒤 OperatorId. now < 발급 + 8:00:00일 때만 유효하고 정각(+8:00:00)부터 만료다. 실패면 PermissionDeniedException → 403 (TC-3876, v0.8, TF-VC-10, D-76) |

구현 infra.integration.auth.JwtOperatorTokenIssuer. 회원 토큰(member.TokenIssuer)과 서명 키·발급자 클레임을 나눠 한쪽 토큰으로 다른 쪽 검사를 통과하지 못한다. OperatorSession(record: adminToken, actorId)은 authenticate의 결과다(D-42, design-review.md M1·R15).

##### OperatorBootstrap (v0.11, CR2-U2, D-74)
| 항목 | 내용 |
|------|------|
| 패키지 | operator |
| 책임 | 화이트리스트가 비었을 때 첫 운영자를 넣는다 |
| 의존 | OperationPolicy(policy), OperationSettingService(policy), ObjectMapper, Seed(설정 runkk.operator.bootstrap-operators, 환경 변수 RUNKK_BOOTSTRAP_OPERATORS) |

v0.10의 policy.OperatorWhitelistSeeder를 옮긴 것이다. OPERATOR_WHITELIST 기본값이 빈 목록이고 변경은 확인된 운영자만 할 수 있어 관리툴로는 첫 운영자를 만들 수 없기 때문이다(FR-173).

| 시그니처 | 설명 |
|---------|------|
| `void onApplicationReady()` | `@EventListener(ApplicationReadyEvent)`. seedIfEmpty를 부른다. 시작 작업을 그 작업을 가진 패키지가 한다(D-55) |
| `boolean seedIfEmpty()` | 시드 값("KAKAO:계정ID,GOOGLE:계정ID")이 비면 아무것도 하지 않고 false. policy.operatorWhitelist()가 이미 비어 있지 않으면 건너뛴다(관리툴 변경이 우선). 아니면 값을 OPERATOR_WHITELIST JSON(`{"accounts":{제공자:[계정ID]}}`, 제공자는 ProviderType 이름만)으로 바꿔 OperationSettingService.change(ACTOR_ID, OPERATOR_WHITELIST, json)로 넣는다. 그 경로가 값 검사(SettingKey.OPERATOR_WHITELIST.validate), 감사(SETTING_CHANGE), 캐시 무효화를 함께 한다. 형식 오류·검사 실패는 예외를 밖으로 던지지 않고 운영 로그만 남겨 기동은 계속된다(화이트리스트가 빈 목록이라 관리툴 로그인은 403). 넣었으면 true — 시드 값이 null이면 로그 없이 false, 빈 문자열이면 WARN 1줄(operator_seed_empty) 뒤 false. 형식 오류·검사 실패도 WARN 1줄, 제공자는 대문자만(D-166, K-5) (v0.11, test.md v0.6 TF-BE-41) |

상수 ACTOR_ID = "bootstrap:RUNKK_BOOTSTRAP_OPERATORS"(감사 행위자). v0.10 설계(D-74·D-87)와 구현이 다른 점이 셋 있다: 환경 변수 이름(RUNKK_OPERATOR_SEED → RUNKK_BOOTSTRAP_OPERATORS), 넣는 조건(OPERATOR_WHITELIST 행 없음 → 화이트리스트가 빔), 감사 행위자 역할(SYSTEM → OperationSettingService.change가 남기는 OPERATOR, 행위자 문자열 ACTOR_ID). 위 표는 구현대로 적었다. v0.11 정리: 셋 모두 구현이 옳다고 보고 D-74를 구현에 맞췄다(변수 이름은 운영 설정 사실, 조건은 기본값이 빈 목록이라 같은 효과, 감사는 OperationSettingService.change 한 경로로 검사·감사·캐시 무효화를 함께 하고 행위자 ID로 구분된다). D-87과 다른 점(소문자 제공자를 대문자로 바꿔 받음, 빈 문자열이면 로그 없이 건너뜀)은 설계를 유지하고 구현 결함 후보 K-5로 넘긴다(부록 E.2) (v0.11, CR2-U2).


#### 6.1.12a report (v0.11, FR-214, FR-215, FR-216, D-104)

report 패키지의 책임은 "콘텐츠 신고의 접수·숨김·처리를 관리한다"이다. 대상 하나에 처리 단위 `ReportCase` 한 행, 신고 한 건마다 `ContentReport` 한 행을 둔다. 신고 대상(게시글·채팅 메시지)은 콘텐츠를 가진 패키지가 포트 `ReportedContentPort`를 구현해 내준다: crew.CrewPostReportAdapter(CHEER·SCHEDULE·NOTICE·POLL·BOARD, 6.1.7), crew.chat.ChatReportAdapter(CHAT_MESSAGE, 6.1.7a). report는 crew·crew.chat을 import하지 않고 notification·audit·member·common만 import한다(12.2 N6·N5·N7~N10, 순환 없음 F.6.3). 저장 포트 ReportCaseRepository의 구현은 infra.report.JpaReportCaseRepository다. 표 report_case, content_report(V24, 파일 D19)이고 crew 표에 FK를 걸지 않는다(대상이 지워지면 사례는 "대상 없음"으로 남고 운영자가 기각한다) (v0.11, D-104, F.5.3).

##### ReportTargetKind, ReportReason, ReportStatus (enum) (v0.11, F.5.1)
| enum | 상수 | 비고 |
|------|------|------|
| ReportTargetKind | CHEER, SCHEDULE, NOTICE, POLL, BOARD, CHAT_MESSAGE | 자료사전 신고대상종류. SCHEDULE은 크루런·대면 모임 둘 다 |
| ReportReason | ABUSE(욕설·비방), SEXUAL(음란), SPAM(광고·스팸), PRIVACY(개인정보 노출), OTHER(기타) | 자료사전 신고사유. OTHER면 detail 필수(1~200자, srs FR-214 보완으로 확정, E-27 닫음) (v0.11, 7.13.5 TF-VC-22) |
| ReportStatus | RECEIVED(접수), AUTO_HIDDEN(자동숨김), HIDDEN(숨김), DISMISSED(기각) | 자료사전 신고처리상태(ReportCase.status) |
| ReportDecision (삭제) | — | 운영자 처리 입력 상수가 report 안의 갈래를 고르는 제어 결합이라 hide·dismiss 연산 둘로 나누고 지웠다 (v0.11 검증, D-138) |

##### ReportCase (엔티티) (v0.11, D-104)
| 항목 | 내용 |
|------|------|
| 패키지 | report |
| 책임 | 신고 대상 하나의 처리 상태를 보관한다 |

속성:
| 이름 | 형 | 제약 |
|------|----|------|
| id | long | 발급 뒤 불변 |
| targetKind | ReportTargetKind | 불변. (targetKind, targetId) 고유 |
| targetId | long | 불변. 게시글 ID 또는 채팅 메시지 ID |
| crewId | long | 불변 |
| status | ReportStatus | 상태도 02 ReportCase |
| openReporterCount | int | ≥ 0, 아직 처리하지 않은 서로 다른 신고자 수 |
| openedAt | LocalDateTime | 사례를 연(다시 연) 시각 |
| resolvedAt | LocalDateTime | nullable |
| resolverId | String | nullable, 처리한 운영자 계정 |
| version | long | 낙관적 잠금 |

상수 `AUTO_HIDE_REPORTERS = 3`(서로 다른 신고자 수, D-113. 운영 기준값이 아님).

연산:
| 시그니처 | 설명 | 사전 조건 | 사후 조건 / 예외 |
|---------|------|----------|-----------------|
| `boolean addReport()` | 신고 한 건 반영 | 같은 신고자 중복은 호출 전에 걸렀다 | openReporterCount + 1. RECEIVED이고 AUTO_HIDE_REPORTERS 이상이 되면 AUTO_HIDDEN으로 바꾸고 true(호출자가 같은 트랜잭션에서 콘텐츠를 숨긴다). 그 밖에는 false (FR-215) |
| `void hide(String operatorId, LocalDateTime now)` / `void dismiss(String operatorId, LocalDateTime now)` | 운영자 처리 | RECEIVED 또는 AUTO_HIDDEN | hide면 HIDDEN, dismiss면 DISMISSED. resolvedAt = now, resolverId = operatorId, openReporterCount = 0. 이미 HIDDEN·DISMISSED면 BusinessRuleException(REPORT_DECIDED). 두 연산은 private 마무리 하나를 함께 쓴다 (FR-216) (v0.11 검증, D-138) |
| `void reopen()` | 기각 뒤 새 신고 | DISMISSED | RECEIVED, 열린 신고자 수 0부터, openedAt 갱신. 새 신고는 이어서 addReport로 센다 (D-104). 호출자는 같은 신고자의 이전 신고가 없음을 먼저 확인했다(기각 뒤 같은 신고자 재신고는 reopen 없이 ALREADY_REPORTED). 자동 숨김은 이 뒤 신고자만 센다(D-149) (v0.11, 7.13.5 TF-VC-23) |
| 조회 | `getId()`, `getStatus()`, `getTargetKind()`, `getTargetId()`, `getCrewId()`, `getOpenReporterCount()` | | |

##### ContentReport (엔티티) (v0.11, D-104)
| 항목 | 내용 |
|------|------|
| 패키지 | report |
| 책임 | 신고 한 건의 신고자·사유를 보관한다 |

속성: id(long), caseId(long), reporterId(Long, 탈퇴하면 null), reason(ReportReason), detail(String, nullable, ≤ 200자), reportedAt(LocalDateTime), resolved(boolean, 사례 처리 때 true). 같은 신고자는 같은 사례에 한 번만(고유 색인 uq_content_report_reporter). 생성자 입력 값 `ContentReport.Content(ReportReason reason, String detail)`(record)이 사유와 상세를 묶는다(생성자 매개변수 4개 이하) (v0.12, U6-5).

| 시그니처 | 설명 |
|---------|------|
| `void anonymizeReporter()` | reporterId = null. 회원 탈퇴 때(FR-021) |

##### ReportedContentPort (포트, report가 정의) (v0.11, D-104)
| 시그니처 | 설명 |
|---------|------|
| `boolean supports(ReportTargetKind kind)` | 이 구현이 맡는 대상 종류인가 |
| `Optional<ReportedContent> find(ReportTargetKind kind, long targetId)` | 대상이 없거나 삭제됐거나 종류가 맞지 않으면 비어 있음. 숨긴 콘텐츠는 돌려주고 hidden을 참으로 싣는다(D-115) |
| `void hide(ReportTargetKind kind, long targetId)` | 콘텐츠를 숨긴다(crew_post.hidden, crew_chat_message.hidden). 숨긴 콘텐츠는 목록·조회에서 빠진다 |
| `void unhide(ReportTargetKind kind, long targetId)` | 숨김을 푼다(기각) |

구현: report.target.CrewPostReportAdapter, report.target.ChatReportAdapter (v0.11 검증, D-135). 숨긴 글은 신고 접수에서 REPORT_TARGET_NOT_FOUND로 거부하고(F.2.2) 관리툴 미리보기(contentPreview)에서는 보인다. 숨김 상태를 ReportedContent.hidden에 실어 find 하나로 두 쓰임을 가른다(D-115) (v0.11, FR-214, FR-216).

##### ReportedContent (값) (v0.11, D-104)
속성: crewId(long), authorId(Long, 탈퇴면 null), preview(String, 본문 앞 100자 이하), hidden(boolean, D-115), crewMemberIds(Set<Long>, 대상 크루의 현재 구성원, 포트 구현이 CrewMembershipReader로 채운다). `boolean isCrewMember(long memberId)` = crewMemberIds.contains(memberId). report가 crew에 묻지 않게 판정 자료를 값에 담는다(D-115).

##### ReportCommand, ReportResult, ReportCaseView, ReportResolution (입력·결과, record) (v0.11, D-104)
| record | 항목 |
|--------|------|
| ReportCommand | targetKind, targetId, reason, detail(String?, 앞뒤 공백 제거 뒤 ≤ 200자(코드 포인트), reason = OTHER면 1자 이상 필수). 생성자 검사(위반 IllegalArgumentException → 400, D-127, D-149) (v0.11, 7.13.5 TF-VC-22) |
| ReportResult | reportId(long), status(ReportStatus, 접수 결과 사례 상태: RECEIVED 또는 AUTO_HIDDEN) |
| ReportCaseView | caseId, targetKind, targetId, crewId, contentPreview(String?, 대상이 지워졌으면 null), targetMissing(boolean), authorId(Long?), status, reporterCount(int), reasons(Map<ReportReason, Integer>), firstReportedAt, lastReportedAt |
| ReportResolution | caseId(long), status(ReportStatus) |
| ReportCaseListView | cases(List<ReportCaseView>), nextCursor(Long?, 다음 페이지가 없으면 null). 다른 목록 결과(PostListView 등)와 같은 꼴 (v0.11 정리) |

web.report·web.admin.report가 F.2.2의 ReportRequest·ReportResponse·ReportCaseItem으로 바꾼다(12.2 N13·N14).

##### ReportService (v0.11, D-104)
| 항목 | 내용 |
|------|------|
| 패키지 | report |
| 책임 | 콘텐츠 신고 유스케이스를 조정한다 |
| 의존 | ReportCaseRepository, List<ReportedContentPort>, NotificationService(notification: REPORT_RESOLVED), AuditService(audit: REPORT_HIDE·REPORT_DISMISS), Clock |

| 시그니처 | 설명 |
|---------|------|
| `ReportResult submit(long reporterId, ReportCommand command)` | POST /reports. supports(kind)인 포트의 find(kind, targetId)가 비거나 content.hidden이면 BusinessRuleException(REPORT_TARGET_NOT_FOUND, D-115) → content.isCrewMember(reporterId)가 거짓이면 NOT_CREW_MEMBER → authorId == reporterId면 TARGET_INVALID(본인 콘텐츠) → 한 트랜잭션에서 report_case 행을 잠근다(없으면 만들고, 고유 제약 충돌이면 다시 읽어 잠금) → 같은 신고자·같은 대상의 신고가 하나라도 있으면(기각된 것 포함, existsReport, FR-214 보완 (v0.11, test.md v0.6 TF-BE-38)) ALREADY_REPORTED → DISMISSED면 ReportCase.reopen(다른 신고자의 새 신고만 사례를 다시 연다, D-149 (v0.11, 7.13.5 TF-VC-23)) → ContentReport 저장 → ReportCase.addReport()가 true면 같은 트랜잭션에서 ReportedContentPort.hide. 동시 신고 경쟁은 행 잠금으로 막는다 (FR-214, FR-215, D-104) |
| `ReportCaseListView cases(Optional<ReportStatus> status, PageCursor cursor)` | GET /admin/reports. status가 없으면 RECEIVED·AUTO_HIDDEN. (status, openedAt) 색인 순. 미리보기는 포트 find로 채우고(숨긴 콘텐츠도 보인다, D-115) 대상이 없으면 targetMissing = true. ReportCaseListView(cases, nextCursor)는 PageCursor.slice 결과를 옮긴 것이다(F.6.2 N14) (FR-216) |
| `ReportResolution hide(String operatorId, long caseId)` / `ReportResolution dismiss(String operatorId, long caseId)` | POST /admin/reports/{caseId}/hide / …/dismiss. 사례 없음이면 REPORT_NOT_FOUND → ReportCase.hide / dismiss(이미 처리면 REPORT_DECIDED) → hide면 콘텐츠를 숨긴 채 둔다(AUTO_HIDDEN이 아니었으면 ReportedContentPort.hide), dismiss면 ReportedContentPort.unhide(자동 숨김을 푼다) → 그 사례의 열린 신고를 resolved로 → 감사 REPORT_HIDE / REPORT_DISMISS(행위자 운영자 계정, ActorRole.OPERATOR, 대상 caseId) → 열린 신고자에게 REPORT_RESOLVED 알림(커밋 뒤). operatorId는 web.admin OperatorCheckInterceptor가 확인한 actorId다(D-50). 공통 마무리(열린 신고 정리·알림)는 private 연산 하나다 (FR-216) (v0.11 검증, D-138) — v0.11 7.13.5: REPORT_RESOLVED는 targetId = 사례의 crewId로 보내 크루 홈으로 간다(srs FR-216 보완, D-149) (v0.11, 7.13.5 TF-VC-24) |

##### ReportCaseRepository (포트, report가 정의) (v0.11, D-104)
구현 infra.report.JpaReportCaseRepository. report 밖에서 쓰지 않는다. 연산(코드 저장소 포트 이름 규칙): `Optional<ReportCase> findByTargetForUpdate(ReportTargetKind kind, long targetId)`(행 잠금), `ReportCase saveCase(ReportCase c)`(대상 고유 제약 충돌이면 호출자가 findByTargetForUpdate로 다시 읽어 잠근다), `Optional<ReportCase> findByIdForUpdate(long caseId)`, `List<ReportCase> findByStatus(Collection<ReportStatus> statuses, PageCursor page)`((status, openedAt) 색인, size + 1), `ContentReport saveReport(ContentReport r)`, `boolean existsReport(long caseId, long reporterId)`(처리된 신고 포함, v0.11 정리의 existsOpenReport를 바꿨다, D-149 (v0.11, 7.13.5 TF-VC-23)), `List<Long> openReporterIds(long caseId)`, `Map<ReportReason, Integer> reasonCounts(long caseId)`, `void resolveOpenReports(long caseId)`(resolved = true), `void anonymizeReporter(long memberId)` (v0.11 정리).

##### ReportWithdrawalListener (v0.11, FR-021, D-104)
`void onMemberWithdrawn(MemberWithdrawn e)`: 그 회원의 ContentReport.anonymizeReporter(신고자 익명화). 사례와 신고 건수는 남긴다(FR-021, 12.2 N9).
