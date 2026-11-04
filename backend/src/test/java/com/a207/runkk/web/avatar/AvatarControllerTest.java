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

    private void ensureShop() {
        if (policy.shopCatalog().items().isEmpty()) {
            settings.change(
                    "ops@runkk",
                    SettingKey.SHOP_ITEMS,
                    "{\"items\":[{\"id\":901,\"name\":\"러닝 모자\",\"slot\":\"HAT\",\"price\":30,"
                            + "\"onSale\":true},{\"id\":902,\"name\":\"반바지\",\"slot\":\"BOTTOM\","
                            + "\"price\":40,\"onSale\":true}]}");
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
}
