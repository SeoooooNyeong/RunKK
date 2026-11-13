package com.a207.runkk.crew;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 크루 로고 한 장(피그마 F-04, S15P31A207-295). 게시 사진과 같은 검사(PhotoUpload: 1MB, JPEG, 위치 메타데이터 제거)를 거친 바이트만
 * 갖는다. 크루를 만들기 전에 올리므로 크루 ID가 없고, 크루는 logo_url(/api/v1/crew-logos/{id})로 가리킨다. 한 번 저장하면 바뀌지 않는다.
 */
@Entity
@Table(name = "crew_logo")
public class CrewLogo {

    /** 크루가 업로드 로고를 가리키는 주소 앞부분(logo_url = URL_PREFIX + id, contract-W4 6·8절). */
    public static final String URL_PREFIX = "/api/v1/crew-logos/";

    private static final Pattern URL = Pattern.compile("^/api/v1/crew-logos/([0-9]{1,18})$");

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

    /** 업로드 로고 주소면 로고 ID, https 주소·null이면 비어 있다. */
    public static OptionalLong idOf(String logoUrl) {
        if (logoUrl == null) {
            return OptionalLong.empty();
        }
        Matcher m = URL.matcher(logoUrl);
        return m.matches() ? OptionalLong.of(Long.parseLong(m.group(1))) : OptionalLong.empty();
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
