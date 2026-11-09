package com.a207.runkk.web.avatar;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.a207.runkk.avatar.FacePart;
import com.a207.runkk.avatar.SdFacePartImages;
import com.a207.runkk.common.Distance;
import com.a207.runkk.policy.OperationPolicy;
import com.a207.runkk.policy.OperationSettingService;
import com.a207.runkk.policy.SettingKey;
import com.a207.runkk.reward.RewardService;
import com.a207.runkk.running.RunMode;
import com.a207.runkk.running.RunRecordSaved;
import com.a207.runkk.web.WebTestSupport;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 5.7.1 아바타 4·상점 2 엔드포인트(web.avatar). 기존 5파츠 데이터 조회를 함께 검증한다. */
@DisplayName("web.avatar 아바타·상점 엔드포인트")
class AvatarControllerTest extends WebTestSupport {

    @Autowired com.a207.runkk.avatar.AvatarRepository avatars;
    @Autowired com.a207.runkk.avatar.FacePartImageStore images;
    @Autowired org.springframework.transaction.support.TransactionTemplate tx;
    @Autowired OperationSettingService settings;
    @Autowired OperationPolicy policy;
    @Autowired RewardService rewards;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;

    /**
     * 내장 Redis는 시험 실행 사이에 남을 수 있고 H2 회원 ID는 실행마다 1부터라, 같은 날 앞선 실행의 셀카 실패 수·잠금이 새 회원에게 붙는다. 시험마다 셀카
     * 키를 지워 격리한다.
     */
    @org.junit.jupiter.api.BeforeEach
    void clearSelfieKeys() {
        java.util.Set<String> keys = redis.keys("selfie:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private static SdFacePartImages parts() {
        Map<FacePart, byte[]> images = new EnumMap<>(FacePart.class);
        for (FacePart part : FacePart.values()) {
            images.put(part, new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        }
        return new SdFacePartImages(images);
    }

    private static final String CAP_901 =
            "{\"id\":901,\"name\":\"러닝 모자\",\"slot\":\"HAT\",\"price\":30,\"onSale\":true}";
    private static final String SHORTS_902 =
            "{\"id\":902,\"name\":\"반바지\",\"slot\":\"BOTTOM\",\"price\":40,\"onSale\":true}";

    /** W8 잠긴 아이템(S15P31A207-299): 유효 러닝 1번이면 열린다. */
    private static final String LOCKED_951 =
            "{\"id\":951,\"name\":\"완주 기념 티\",\"slot\":\"TOP\",\"price\":10,\"onSale\":true,"
                    + "\"assetKey\":\"top_finisher_tee\",\"unlock\":{\"kind\":\"RUN_COUNT\","
                    + "\"threshold\":1,\"text\":\"1번 달리면 열려요\"}}";

    /** 시험 문맥을 함께 쓰는 다른 시험이 넣은 아이템은 지우지 못하므로(D-44) 지금 목록에 없는 시험 아이템만 덧붙인다. */
    private void ensureShop() throws Exception {
        java.util.List<String> items = new java.util.ArrayList<>();
        java.util.Set<Long> have = new java.util.HashSet<>();
        for (var item : policy.shopCatalog().items()) {
            have.add(item.id());
            items.add(write(item));
        }
        boolean added = false;
        for (var e : Map.of(901L, CAP_901, 902L, SHORTS_902, 951L, LOCKED_951).entrySet()) {
            if (!have.contains(e.getKey())) {
                items.add(e.getValue());
                added = true;
            }
        }
        if (added) {
            settings.change(
                    "ops@runkk",
                    SettingKey.SHOP_ITEMS,
                    "{\"items\":[" + String.join(",", items) + "]}");
        }
    }

    private void earn(long memberId, long meters) {
        rewards.onRunSaved(
                new RunRecordSaved(
                        UUID.randomUUID().toString(),
                        memberId,
                        LocalDateTime.now().minusHours(1),
                        Distance.ofMeters(meters),
                        Duration.ofMinutes(30),
                        true,
                        40.0,
                        RunMode.FREE,
                        null,
                        null,
                        true,
                        1,
                        LocalDateTime.now()));
    }

    @Test
    @DisplayName("이전 5파츠 얼굴 조회와 기본 얼굴 전환은 유지한다")
    void legacyFace() throws Exception {
        Session s = signUp();
        tx.executeWithoutResult(
                status -> {
                    var avatar = avatars.findForUpdate(s.memberId()).orElseThrow();
                    avatar.applySelfieFace(
                            images.save(s.memberId(), parts()),
                            java.time.LocalDate.now(),
                            new com.a207.runkk.policy.SelfieLimit(1));
                    avatars.save(avatar);
                });
        String faceKey =
                body(mvc.perform(as(s, get("/api/v1/avatar")))
                                .andReturn()
                                .getResponse()
                                .getContentAsString())
                        .get("faceKey")
                        .asText();
        String set = faceKey.substring("selfie:".length());
        mvc.perform(as(s, get("/api/v1/avatar/parts/" + set + "/EYES")))
                .andExpect(status().isOk())
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                                .contentType(MediaType.IMAGE_PNG))
                .andExpect(
                        org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                                .bytes(new byte[] {(byte) 0x89, 'P', 'N', 'G'}));
        Session viewer = signUp();
        mvc.perform(as(viewer, get("/api/v1/avatar/parts/" + set + "/HAIR")))
                .andExpect(status().isOk());
        mvc.perform(as(viewer, get("/api/v1/avatar/parts/" + set + "/WINGS")))
                .andExpect(status().isBadRequest());
        mvc.perform(
                        as(
                                viewer,
                                get(
                                        "/api/v1/avatar/parts/face/"
                                                + s.memberId()
                                                + "/00000000-0000-0000-0000-000000000000/EYES")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FACE_IMAGE_NOT_FOUND"));
        mvc.perform(get("/api/v1/avatar/parts/" + set + "/EYES"))
                .andExpect(status().isUnauthorized());

        mvc.perform(
                        as(s, post("/api/v1/avatar/default"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("characterId", "FOX"))))
                .andExpect(jsonPath("$.faceKey").value("default:FOX"))
                .andExpect(jsonPath("$.defaultCharacterId").value("FOX"));
        mvc.perform(
                        as(s, post("/api/v1/avatar/default"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("characterId", "DRAGON"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("상점·구매·착용: 잔액 부족 거부, 산 아이템만 착용, 안 산 아이템은 부위별 거부 사유, null은 해제")
    void shopAndEquip() throws Exception {
        ensureShop();
        Session s = signUp();
        mvc.perform(as(s, get("/api/v1/shop").param("slot", "HAT")))
                .andExpect(jsonPath("$.items[0].itemId").value(901))
                .andExpect(jsonPath("$.items[0].owned").value(false))
                .andExpect(jsonPath("$.balance").value(0));
        mvc.perform(
                        as(s, post("/api/v1/shop/purchases"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("itemId", 901))))
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_REWARD"));

        earn(s.memberId(), 5_000);
        mvc.perform(
                        as(s, post("/api/v1/shop/purchases"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("itemId", 901))))
                .andExpect(jsonPath("$.balance").value(20));

        Map<String, Object> changes = new HashMap<>();
        changes.put("HAT", 901);
        changes.put("BOTTOM", 902);
        mvc.perform(
                        as(s, put("/api/v1/avatar/equipment"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("changes", changes))))
                .andExpect(jsonPath("$.equipped.HAT").value(901))
                .andExpect(jsonPath("$.rejected.BOTTOM").value("ITEM_NOT_OWNED"))
                .andExpect(jsonPath("$.balance").value(20));
        Map<String, Object> off = new HashMap<>();
        off.put("HAT", null);
        mvc.perform(
                        as(s, put("/api/v1/avatar/equipment"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("changes", off))))
                .andExpect(jsonPath("$.equipped").isEmpty());
        mvc.perform(as(s, get("/api/v1/shop")))
                .andExpect(jsonPath("$.items[?(@.itemId == 901)].owned").value(true));
    }

    @Test
    @DisplayName(
            "배경(v0.11 FR-201·FR-202): 목록은 개인 랜드마크 22개, 잠긴 키 선택은 409 BACKGROUND_LOCKED, null은 기본 배경")
    void backgrounds() throws Exception {
        Session s = signUp();
        mvc.perform(as(s, get("/api/v1/avatar/backgrounds")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedKey").doesNotExist())
                .andExpect(jsonPath("$.backgrounds.length()").value(22))
                .andExpect(jsonPath("$.backgrounds[0].key").value("INCHEON"))
                .andExpect(jsonPath("$.backgrounds[0].name").value("인천"))
                .andExpect(jsonPath("$.backgrounds[0].stage").value("DOMESTIC"))
                .andExpect(jsonPath("$.backgrounds[0].distanceMeters").value(60_000))
                .andExpect(jsonPath("$.backgrounds[0].unlocked").value(false))
                .andExpect(jsonPath("$.backgrounds[21].key").value("EARTH_LAP"));

        mvc.perform(
                        as(s, put("/api/v1/avatar/background"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("key", "BUSAN"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BACKGROUND_LOCKED"));
        Map<String, Object> none = new HashMap<>();
        none.put("key", null);
        mvc.perform(
                        as(s, put("/api/v1/avatar/background"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(none)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backgrounds.length()").value(22));
        mvc.perform(as(s, get("/api/v1/avatar")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backgroundKey").doesNotExist());
    }

    // W8 내 러너·배경 확인·상점 잠금(S15P31A207-299, contract-W8.md)

    @Test
    @DisplayName("W8 GET /avatar/runner: 러닝 없는 회원은 기본 체형(변화 0)·러닝량 0·대표 시각 없음, 토큰 없으면 401")
    void runner() throws Exception {
        Session s = signUp();
        mvc.perform(as(s, get("/api/v1/avatar/runner")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bodyShape").value("STAGE1"))
                .andExpect(jsonPath("$.bodyVariant").value(0))
                .andExpect(jsonPath("$.skinTone").value("DAYTIME"))
                .andExpect(jsonPath("$.typicalStartHour").doesNotExist())
                .andExpect(jsonPath("$.recentRunCount").value(0))
                .andExpect(jsonPath("$.recentDistanceMeters").value(0))
                .andExpect(jsonPath("$.runAmount").value(0))
                .andExpect(jsonPath("$.restDaysBeforeDecline").value(4));
        mvc.perform(get("/api/v1/avatar/runner")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("W8 배경: 누적 거리·남은 거리·해금 일시(앱 해금 팝업 판정용)")
    void backgroundsRemaining() throws Exception {
        Session s = signUp();
        var incheon = policy.landmarkTable().landmarks().get(0);
        tx.executeWithoutResult(
                status -> {
                    var avatar = avatars.findForUpdate(s.memberId()).orElseThrow();
                    avatar.unlock(java.util.List.of(incheon), LocalDateTime.of(2026, 11, 9, 7, 40));
                    avatars.save(avatar);
                });

        mvc.perform(as(s, get("/api/v1/avatar/backgrounds")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMeters").value(0))
                .andExpect(jsonPath("$.backgrounds[0].unlocked").value(true))
                .andExpect(
                        jsonPath("$.backgrounds[0].unlockedAt").value("2026-11-09T07:40:00+09:00"))
                .andExpect(jsonPath("$.backgrounds[0].remainingMeters").value(0))
                .andExpect(jsonPath("$.backgrounds[1].unlocked").value(false))
                .andExpect(jsonPath("$.backgrounds[1].remainingMeters").value(120_000));
    }

    @Test
    @DisplayName("W8 상점: 그림 키·잠김·조건 문구, 잠긴 아이템 구매는 409 ITEM_LOCKED")
    void shopLockedItem() throws Exception {
        ensureShop();
        Session s = signUp();
        earn(s.memberId(), 3_000);
        mvc.perform(as(s, get("/api/v1/shop").param("slot", "TOP")))
                .andExpect(
                        jsonPath("$.items[?(@.itemId == 951)].assetKey").value("top_finisher_tee"))
                .andExpect(jsonPath("$.items[?(@.itemId == 951)].locked").value(true))
                .andExpect(
                        jsonPath("$.items[?(@.itemId == 951)].unlockCondition")
                                .value("1번 달리면 열려요"));
        mvc.perform(as(s, get("/api/v1/shop").param("slot", "HAT")))
                .andExpect(jsonPath("$.items[?(@.itemId == 901)].locked").value(false));
        mvc.perform(
                        as(s, post("/api/v1/shop/purchases"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(write(Map.of("itemId", 951))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_LOCKED"));
    }
}
