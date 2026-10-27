package com.a207.runkk.crew;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.ReasonCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 크루 게시글 하나와 사진 목록(설계 6.1.7, FR-211, D-100). 본문 0~1,000자, 사진 0~5장. 사진 바이트는 갖지 않고 PostPhoto 메타만
 * 만든다(바이트는 CrewPostService가 PostPhotoStore로 같은 트랜잭션에서 저장한다).
 */
@Entity
@DiscriminatorValue("BOARD")
public class BoardPost extends CrewPost {

    /** 글 하나의 사진 상한(D-113). */
    public static final int MAX_PHOTOS = 5;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "post_id", nullable = false, updatable = false)
    @OrderBy("seq")
    private List<PostPhoto> photos = new ArrayList<>();

    protected BoardPost() {}

    /**
     * 본문이 앞뒤 공백을 지우면 비고 사진도 0장이면 POST_EMPTY(D-165), 사진 6장 이상이면 PHOTO_LIMIT, 본문 1,000자 초과
     * BODY_TOO_LONG.
     */
    public BoardPost(long crewId, long authorId, BoardDraft draft, LocalDateTime now) {
        super(crewId, authorId, draft.body(), now);
        if (draft.photos().size() > MAX_PHOTOS) {
            throw new BusinessRuleException(ReasonCode.PHOTO_LIMIT);
        }
        if (body().strip().isEmpty() && draft.photos().isEmpty()) {
            throw new BusinessRuleException(ReasonCode.POST_EMPTY);
        }
        for (int i = 0; i < draft.photos().size(); i++) {
            photos.add(new PostPhoto(crewId, i + 1, draft.photos().get(i).size(), now));
        }
    }

    @Override
    protected int maxBody() {
        return LONG_BODY;
    }

    @Override
    public PostKind kind() {
        return PostKind.BOARD;
    }

    /** 본문 + 사진 ID(seq 순). 주소(/api/v1/crew-photos/{photoId})는 web.crew가 붙인다. */
    @Override
    public PostSummary summary(long viewerId) {
        return baseSummary(viewerId).withPhotos(photos.stream().map(PostPhoto::getId).toList());
    }

    /** 사진 메타, seq 순. */
    public List<PostPhoto> photos() {
        return Collections.unmodifiableList(photos);
    }
}
