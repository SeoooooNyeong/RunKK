package com.a207.runkk.crew;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 게시글 사진 한 장의 메타(설계 6.1.7, FR-211, D-100). 바이트는 갖지 않는다: 같은 표 crew_photo의 jpeg 칼럼은 PostPhotoStore가
 * 읽고 쓴다. post_id는 BoardPost가 합성으로 채운다.
 */
@Entity
@Table(name = "crew_photo")
public class PostPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "post_id", insertable = false, updatable = false)
    private Long postId;

    @Column(name = "crew_id", nullable = false)
    private long crewId;

    @Column(name = "seq", nullable = false)
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private int seq;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected PostPhoto() {}

    PostPhoto(long crewId, int seq, int sizeBytes, LocalDateTime now) {
        this.crewId = crewId;
        this.seq = seq;
        this.sizeBytes = sizeBytes;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    /** 속한 게시글(저장 뒤 다시 읽은 행에만 있다). */
    public Long postId() {
        return postId;
    }

    public long crewId() {
        return crewId;
    }

    public int seq() {
        return seq;
    }

    public int sizeBytes() {
        return sizeBytes;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }
}
