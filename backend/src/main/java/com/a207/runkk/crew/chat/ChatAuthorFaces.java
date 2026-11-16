package com.a207.runkk.crew.chat;

import com.a207.runkk.avatar.AvatarQuery;
import com.a207.runkk.member.DisclosureLevel;
import com.a207.runkk.member.Gender;
import com.a207.runkk.member.MemberQueryService;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 채팅 작성자·반응자의 아바타 얼굴(설계 5.31 A, [임시:D-성-230]). 공개 판정(DisclosureLevel.showsProfile(), 크루원 목록과 같은
 * 규칙)과 얼굴 키·표시 성별 조회를 한곳에 모은다. CrewChatService(목록, 보내기 응답, 반응자)와 ChatDeliveryListener(실시간 알림)가 같이
 * 쓴다. 사람 수와 상관없이 공개 판정, 얼굴 키, 표시 성별을 한 번씩만 묻는다(N+1 없음).
 */
@Component
class ChatAuthorFaces {

    /** 보는 회원이 없는 판정(회원 ID는 1부터라 누구와도 같지 않다). 탈퇴가 아니면 PUBLIC이다. */
    static final long NO_VIEWER = 0L;

    private final MemberQueryService memberQuery;
    private final AvatarQuery avatarQuery;

    ChatAuthorFaces(MemberQueryService memberQuery, AvatarQuery avatarQuery) {
        this.memberQuery = memberQuery;
        this.avatarQuery = avatarQuery;
    }

    /**
     * 회원별 얼굴. 프로필을 보이지 않는 회원(탈퇴)과 null ID는 맵에 없다(부르는 쪽이 둘 다 null로 싣는다).
     *
     * @param viewerId 보는 회원
     * @param memberIds 작성자·반응자 회원 ID(null과 중복을 허용한다)
     */
    Map<Long, Face> of(long viewerId, Collection<Long> memberIds) {
        Set<Long> ids =
                memberIds.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, DisclosureLevel> levels = memberQuery.disclosureOf(viewerId, ids);
        Set<Long> shown =
                ids.stream()
                        .filter(
                                id ->
                                        levels.getOrDefault(id, DisclosureLevel.WITHDRAWN)
                                                .showsProfile())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        if (shown.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> faceKeys = avatarQuery.faceKeysOf(shown);
        Map<Long, Gender> genders = memberQuery.shownGendersOf(shown);
        Map<Long, Face> faces = new HashMap<>();
        for (Long id : shown) {
            Gender gender = genders.get(id);
            faces.put(id, new Face(faceKeys.get(id), gender == null ? null : gender.name()));
        }
        return faces;
    }

    /**
     * 실시간 알림에 싣는 작성자 얼굴. 받는 사람마다 따로 만들지 않으므로 보는 사람 없이(NO_VIEWER) 판정한다. 작성자 본인 기준으로 물으면 탈퇴한 작성자도
     * 본인(SELF)으로 판정돼 얼굴이 나가므로 쓰지 않는다. 작성자가 없거나(null) 탈퇴했으면 Face.NONE.
     */
    Face ofAuthor(Long authorId) {
        if (authorId == null) {
            return Face.NONE;
        }
        return of(NO_VIEWER, java.util.List.of(authorId)).getOrDefault(authorId, Face.NONE);
    }

    /**
     * 얼굴 한 건.
     *
     * @param faceKey 아바타 얼굴 키(Face.renderKey), 얼굴을 고르지 않았으면 null
     * @param shownGender 표시 성별 이름(MALE | FEMALE)
     */
    record Face(String faceKey, String shownGender) {

        /** 얼굴을 싣지 않는 경우(탈퇴 작성자). */
        static final Face NONE = new Face(null, null);
    }
}
