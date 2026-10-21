package com.a207.runkk.infra.avatar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * SD 부위 PNG 한 장의 저장 행(FacePartImageStoreImpl 안에서만 쓴다). 변환 결과만 저장하고 원본 셀카는 저장하지 않는다(D-59). 디스크 파일 대신
 * PostgreSQL에 두어 컨테이너 디스크에 이미지 파일이 생기지 않고 백업(NFR-018)에 함께 들어간다.
 */
@Entity
@Table(name = "face_part_image")
class FacePartImage {

    @Id
    @Column(name = "image_key", length = 200)
    private String imageKey;

    @Column(name = "member_id", nullable = false)
    private long memberId;

    @Column(name = "part", nullable = false, length = 10)
    private String part;

    @Column(name = "png", nullable = false)
    private byte[] png;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected FacePartImage() {}

    /** PNG 바이트의 사본. */
    byte[] png() {
        return png.clone();
    }

    /** 저장 키의 마지막 마디가 부위 이름이다("face/{회원}/{묶음}/{부위}"). */
    FacePartImage(String imageKey, long memberId, byte[] png, LocalDateTime createdAt) {
        this.imageKey = imageKey;
        this.memberId = memberId;
        this.part = imageKey.substring(imageKey.lastIndexOf('/') + 1);
        this.png = png.clone();
        this.createdAt = createdAt;
    }
}
