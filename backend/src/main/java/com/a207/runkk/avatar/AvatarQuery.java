package com.a207.runkk.avatar;

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

    /** 선택한 배경 키(Avatar.selectedBackgroundKey). 기본 배경이면 비어 있다(FR-202, 12.2 D37). */
    @Transactional(readOnly = true)
    public Optional<String> backgroundKeyOf(long memberId) {
        return avatarRepository.findOrCreate(memberId).selectedBackgroundKey();
    }
}
