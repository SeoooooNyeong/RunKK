package com.a207.runkk.crew;

import com.a207.runkk.common.NotFoundRuleException;
import com.a207.runkk.common.ReasonCode;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 크루 로고 올리기·읽기(피그마 F-04, S15P31A207-295). 크기·형식·메타데이터 검사는 PhotoUpload가 이미 했다(D-100, D-142). 로고는 공개
 * 크루 목록과 초대 코드 미리보기에서 크루 밖 사람도 보므로 로그인 회원 누구나 읽는다. 업로드 횟수 제한은 web.crew가 건다.
 */
@Service
public class CrewLogoService {

    private final CrewLogoRepository crewLogoRepository;
    private final Clock clock;

    CrewLogoService(CrewLogoRepository crewLogoRepository, Clock clock) {
        this.crewLogoRepository = crewLogoRepository;
        this.clock = clock;
    }

    /** 로고 한 장을 저장하고 ID를 준다. */
    @Transactional
    public long upload(long memberId, PhotoUpload upload) {
        return crewLogoRepository
                .save(new CrewLogo(memberId, upload, LocalDateTime.now(clock)))
                .getId();
    }

    /** 로고 바이트. 없으면 NotFoundRuleException(CREW_LOGO_NOT_FOUND, 404). */
    @Transactional(readOnly = true)
    public byte[] load(long logoId) {
        return crewLogoRepository
                .findById(logoId)
                .map(CrewLogo::jpeg)
                .orElseThrow(() -> new NotFoundRuleException(ReasonCode.CREW_LOGO_NOT_FOUND));
    }
}
