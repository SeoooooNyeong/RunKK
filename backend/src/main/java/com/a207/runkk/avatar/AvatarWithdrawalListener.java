package com.a207.runkk.avatar;

import com.a207.runkk.member.MemberWithdrawn;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 탈퇴한 회원의 아바타와 SD 부위 이미지를 지운다(설계 6.1.8, FR-021 D11의 외형 부분, D-28). */
@Component
public class AvatarWithdrawalListener {

    private final AvatarRepository avatarRepository;
    private final FacePartImageStore facePartImageStore;

    public AvatarWithdrawalListener(
            AvatarRepository avatarRepository, FacePartImageStore facePartImageStore) {
        this.avatarRepository = avatarRepository;
        this.facePartImageStore = facePartImageStore;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onMemberWithdrawn(MemberWithdrawn e) {
        avatarRepository.deleteByMember(e.memberId());
        facePartImageStore.deleteByMember(e.memberId());
    }
}
