package com.a207.runkk.web.avatar;

import com.a207.runkk.avatar.AvatarService;
import com.a207.runkk.avatar.BackgroundService;
import com.a207.runkk.avatar.FaceImageService;
import com.a207.runkk.common.ItemSlot;
import com.a207.runkk.reward.PurchaseResult;
import com.a207.runkk.reward.ShopService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 아바타 꾸미기·상점 엔드포인트(설계 5.7.1 AvatarController: 아바타 4 + 배경 2(v0.11 FR-201, FR-202), 상점 2). 셀카는
 * multipart로 받되 file-size-threshold가 max-file-size보다 커서 디스크 임시 파일 없이 메모리로만 받고(D-59), JPEG·PNG만 들인다.
 * 셀카 바이트를 저장·로그하지 않는다(FR-156).
 */
@RestController
@RequestMapping("/api/v1")
public class AvatarController {

    private static final String MEMBER_ID = "memberId";

    /** 부위 이미지 기기 캐시 기간(묶음 키가 바뀌면 새 주소다). */
    private static final Duration PART_CACHE = Duration.ofDays(30);

    private final AvatarService avatarService;
    private final ShopService shopService;
    private final FaceImageService faceImageService;
    private final BackgroundService backgroundService;
    private final Clock clock;

    AvatarController(
            AvatarService avatarService,
            ShopService shopService,
            FaceImageService faceImageService,
            BackgroundService backgroundService,
            Clock clock) {
        this.avatarService = avatarService;
        this.shopService = shopService;
        this.faceImageService = faceImageService;
        this.backgroundService = backgroundService;
        this.clock = clock;
    }

    @PostMapping("/avatar/default")
    AvatarDtos.AvatarResponse chooseDefault(
            @RequestAttribute(MEMBER_ID) long memberId,
            @Valid @RequestBody AvatarDtos.DefaultCharacterRequest body) {
        return AvatarDtos.AvatarResponse.of(
                avatarService.chooseDefault(memberId, body.characterId()));
    }

    @PutMapping("/avatar/equipment")
    AvatarDtos.AvatarResponse equip(
            @RequestAttribute(MEMBER_ID) long memberId,
            @Valid @RequestBody AvatarDtos.EquipRequest body) {
        return AvatarDtos.AvatarResponse.of(avatarService.equip(memberId, body.toChanges()));
    }

    @GetMapping("/avatar")
    AvatarDtos.AvatarResponse view(@RequestAttribute(MEMBER_ID) long memberId) {
        return AvatarDtos.AvatarResponse.of(avatarService.view(memberId));
    }

    /** 랜드마크 배경 목록(v0.11 FR-201, FR-202). 배포 전에 넘은 랜드마크는 알림 없이 먼저 채운다(D-107 (3)). */
    @GetMapping("/avatar/backgrounds")
    AvatarDtos.BackgroundsResponse backgrounds(@RequestAttribute(MEMBER_ID) long memberId) {
        return AvatarDtos.BackgroundsResponse.of(
                backgroundService.backgrounds(memberId, LocalDateTime.now(clock)));
    }

    /** 배경 선택(v0.11 FR-202). key가 null이면 기본 배경, 해금하지 않은 키면 409 BACKGROUND_LOCKED. */
    @PutMapping("/avatar/background")
    AvatarDtos.BackgroundsResponse chooseBackground(
            @RequestAttribute(MEMBER_ID) long memberId,
            @RequestBody AvatarDtos.BackgroundRequest request) {
        return AvatarDtos.BackgroundsResponse.of(
                request.key() == null
                        ? backgroundService.useDefault(memberId)
                        : backgroundService.choose(memberId, request.key()));
    }

    /**
     * 셀카 아바타 SD 부위 PNG(설계 공백을 메운 조회). 경로는 "/avatar/parts/" + 저장 키이고, 저장 키는 GET /avatar의 faceKey에서
     * "selfie:"를 뗀 묶음 위치 + "/" + 부위(EYES·NOSE·MOUTH·EARS·HAIR)다. 묶음은 다시 만들 때마다 새 이름이라 내용이 바뀌지 않으므로
     * 기기에 오래 둬도 된다.
     */
    @GetMapping("/avatar/parts/face/{ownerId}/{set}/{part}")
    ResponseEntity<byte[]> partImage(
            @RequestAttribute(MEMBER_ID) long memberId,
            @PathVariable String ownerId,
            @PathVariable String set,
            @PathVariable String part) {
        byte[] png =
                faceImageService.partImage(memberId, "face/" + ownerId + "/" + set + "/" + part);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(PART_CACHE).cachePrivate())
                .body(png);
    }

    @GetMapping("/shop")
    AvatarDtos.ShopResponse shop(
            @RequestAttribute(MEMBER_ID) long memberId,
            @RequestParam(required = false) ItemSlot slot) {
        return AvatarDtos.ShopResponse.of(shopService.shop(memberId, Optional.ofNullable(slot)));
    }

    @PostMapping("/shop/purchases")
    AvatarDtos.PurchaseResponse purchase(
            @RequestAttribute(MEMBER_ID) long memberId,
            @Valid @RequestBody AvatarDtos.PurchaseRequest body) {
        PurchaseResult result = shopService.purchase(memberId, body.itemId());
        return new AvatarDtos.PurchaseResponse(result.itemId(), result.balance());
    }

    /** 셀카 형식 확인: 파일 머리의 JPEG(FF D8 FF)·PNG(89 50 4E 47) 표지. 클라이언트가 보낸 Content-Type은 믿지 않는다. */
    static final class SelfieFormat {

        private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

        private SelfieFormat() {}

        static boolean isJpegOrPng(byte[] image) {
            return startsWith(image, JPEG) || startsWith(image, PNG);
        }

        private static boolean startsWith(byte[] image, byte[] magic) {
            if (image.length < magic.length) {
                return false;
            }
            for (int i = 0; i < magic.length; i++) {
                if (image[i] != magic[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
