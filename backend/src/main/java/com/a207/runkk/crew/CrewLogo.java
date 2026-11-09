package com.a207.runkk.crew;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * 크루 로고 한 장(피그마 F-04, S15P31A207-295). 게시 사진과 같은 검사(PhotoUpload: 1MB, JPEG, 위치 메타데이터 제거)를 거친 바이트만
 * 갖는다. 크루를 만들기 전에 올리므로 크루 ID가 없고, 크루는 logo_url(/api/v1/crew-logos/{id})로 가리킨다. 한 번 저장하면 바뀌지 않는다.
 */
@Entity
@Table(name = "crew_logo")
public class CrewLogo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uploader_id", nullable = false)
    private long uploaderId;

    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;

    @Column(name = "jpeg", nullable = false, length = PhotoUpload.MAX_BYTES)
    private byte[] jpeg;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected CrewLogo() {}

    CrewLogo(long uploaderId, PhotoUpload upload, LocalDateTime now) {
        this.uploaderId = uploaderId;
        this.jpeg = upload.jpeg();
        this.sizeBytes = jpeg.length;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public long uploaderId() {
        return uploaderId;
    }

    public int sizeBytes() {
        return sizeBytes;
    }

    /** 위치 메타데이터를 지운 JPEG 바이트(사본). */
    public byte[] jpeg() {
        return jpeg.clone();
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }
}
