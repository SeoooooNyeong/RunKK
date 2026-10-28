package com.a207.runkk.avatar;

import java.util.Optional;

/**
 * 변환된 SD 부위 이미지 저장소 포트(설계 6.1.8, D-59). 원본 셀카는 저장하지 않는다. 저장 키는 api가 만든다. 구현은
 * infra.avatar.FacePartImageStoreImpl.
 */
public interface FacePartImageStore {

    /** 부위 이미지 5개를 저장하고 저장 키 묶음을 돌려준다. */
    SdFaceParts save(long memberId, SdFacePartImages images);

    void deleteByMember(long memberId);

    /** 저장 키의 PNG. 없으면 비어 있다(GET /avatar/parts/…). */
    Optional<byte[]> find(String imageKey);
}
