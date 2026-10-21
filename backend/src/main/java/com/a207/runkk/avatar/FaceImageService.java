package com.a207.runkk.avatar;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.DisclosureLevel;
import com.a207.runkk.member.MemberQueryService;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 셀카 아바타의 SD 부위 PNG 조회(GET /avatar/parts/{저장 키}). 설계 6.1.8에 부위 이미지를 내주는 연산이 없어 앱이 아바타를 그릴 수 없던 빈 곳을
 * 메운다(B 단계 결정). 얼굴은 프로필 공개 수준과 관계없이 보이는 항목이므로(비공개도 아바타 얼굴은 보임, Q-69) 탈퇴한 회원만 거부한다. 저장 키
 * 형식("face/{회원}/{묶음}/{부위}")이 아니면 잘못된 요청이다.
 */
@Service
public class FaceImageService {

    private static final Pattern KEY =
            Pattern.compile("face/(\\d{1,19})/([A-Za-z0-9-]{1,64})/(EYES|NOSE|MOUTH|EARS|HAIR)");

    private final FacePartImageStore facePartImageStore;
    private final MemberQueryService memberQueryService;

    public FaceImageService(
            FacePartImageStore facePartImageStore, MemberQueryService memberQueryService) {
        this.facePartImageStore = facePartImageStore;
        this.memberQueryService = memberQueryService;
    }

    /** 저장 키의 PNG. 형식이 틀리면 IllegalArgumentException, 주인이 탈퇴했으면 권한 없음, 없으면 FACE_IMAGE_NOT_FOUND. */
    @Transactional(readOnly = true)
    public byte[] partImage(long viewerId, String imageKey) {
        Matcher m = KEY.matcher(imageKey == null ? "" : imageKey);
        if (!m.matches()) {
            throw new IllegalArgumentException("부위 이미지 키 형식이 아니다");
        }
        long ownerId = Long.parseLong(m.group(1));
        DisclosureLevel level =
                memberQueryService
                        .disclosureOf(viewerId, List.of(ownerId))
                        .getOrDefault(ownerId, DisclosureLevel.WITHDRAWN);
        if (level == DisclosureLevel.WITHDRAWN) {
            throw new PermissionDeniedException("탈퇴했거나 없는 회원의 얼굴");
        }
        return facePartImageStore
                .find(imageKey)
                .orElseThrow(() -> new BusinessRuleException(ReasonCode.FACE_IMAGE_NOT_FOUND));
    }
}
