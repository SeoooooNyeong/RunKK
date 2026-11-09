package com.a207.runkk.avatar;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 패키지가 읽을 회원 아바타 얼굴 키·배경 키(설계 6.1.8). profile이 쓴다. Avatar 엔티티를 내주지 않는다(design-review.md A23).
 */
@Service
public class AvatarQuery {

    private final AvatarRepository avatarRepository;

    public AvatarQuery(AvatarRepository avatarRepository) {
        this.avatarRepository = avatarRepository;
    }

    /** Face.renderKey. 얼굴을 고르지 않았으면 비어 있다. */
    @Transactional(readOnly = true)
    public Optional<String> faceKey(long memberId) {
        return avatarRepository.findOrCreate(memberId).face().map(Face::renderKey);
    }

    /**
     * 여러 회원의 얼굴 키(Face.renderKey). 얼굴을 고르지 않은 회원은 맵에 없다. 다른 러너에게 아바타는 늘 보이므로(FR-006, D-241) 거르지
     * 않는다. 탈퇴 회원 ID는 부르는 쪽이 넘기지 않는다. web.crew가 크루런 대기실·결과의 크루원 아바타에 쓴다(크루 정원 10명이라 회원마다 읽는다).
     */
    @Transactional(readOnly = true)
    public Map<Long, String> faceKeysOf(Collection<Long> memberIds) {
        Map<Long, String> keys = new LinkedHashMap<>();
        for (Long id : memberIds) {
            if (id != null && !keys.containsKey(id)) {
                faceKey(id).ifPresent(key -> keys.put(id, key));
            }
        }
        return keys;
    }

    /** 선택한 배경 키(Avatar.selectedBackgroundKey). 기본 배경이면 비어 있다(FR-202, 12.2 D37). */
    @Transactional(readOnly = true)
    public Optional<String> backgroundKeyOf(long memberId) {
        return avatarRepository.findOrCreate(memberId).selectedBackgroundKey();
    }
}
