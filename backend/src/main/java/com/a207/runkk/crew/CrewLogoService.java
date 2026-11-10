package com.a207.runkk.crew;

import com.a207.runkk.common.NotFoundRuleException;
import com.a207.runkk.common.ReasonCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 크루 로고 올리기·읽기(피그마 F-04, S15P31A207-295). 크기·형식·메타데이터 검사는 PhotoUpload가 이미 했다(D-100, D-142). 로고는 공개
 * 크루 목록과 초대 코드 미리보기에서 크루 밖 사람도 보므로 로그인 회원 누구나 읽는다. 업로드 횟수 제한은 web.crew가 건다. 어느 크루도 가리키지 않는 로고는 회원당
 * 최근 MAX_UNLINKED장만 남기고, 크루 만들기·변경의 로고 경로는 올린 사람이 요청자이거나 그 크루에 이미 연결된 것만 받는다(코드 리뷰 M2).
 */
@Service
public class CrewLogoService {

    /** 회원당 남기는 미연결 로고 수(고르기만 하고 쓰지 않은 로고로 저장소를 채우는 남용 방지, 코드 리뷰 M2). */
    static final int MAX_UNLINKED = 3;

    private final CrewLogoRepository crewLogoRepository;
    private final Clock clock;

    CrewLogoService(CrewLogoRepository crewLogoRepository, Clock clock) {
        this.crewLogoRepository = crewLogoRepository;
        this.clock = clock;
    }

    /**
     * 로고 한 장을 저장하고 ID를 준다. 그 회원의 미연결 로고가 MAX_UNLINKED장을 넘으면 가장 오래된 것부터 지운다(새 로고는 남는다). 크루에 연결된 로고는
     * 세지도 지우지도 않는다.
     */
    @Transactional
    public long upload(long memberId, PhotoUpload upload) {
        long id =
                crewLogoRepository
                        .save(new CrewLogo(memberId, upload, LocalDateTime.now(clock)))
                        .getId();
        List<Long> unlinked = crewLogoRepository.findUnlinkedIds(memberId);
        if (unlinked.size() > MAX_UNLINKED) {
            crewLogoRepository.removeAll(unlinked.subList(MAX_UNLINKED, unlinked.size()));
        }
        return id;
    }

    /**
     * 크루 만들기·정보 변경의 logoUrl 확인. 업로드 로고 경로가 아니면(https·null) 그대로 둔다. 경로면 그 크루에 이미 연결된 값(linkedUrl)이거나
     * 요청자가 올린 있는 로고여야 하고, 아니면 IllegalArgumentException(400 BAD_REQUEST, contract-W4 8절과 같은 응답).
     */
    void requireUsable(long requesterId, String logoUrl, String linkedUrl) {
        if (CrewLogo.idOf(logoUrl).isEmpty() || Objects.equals(logoUrl, linkedUrl)) {
            return;
        }
        boolean own =
                crewLogoRepository
                        .findUploaderId(CrewLogo.idOf(logoUrl).getAsLong())
                        .filter(uploader -> uploader == requesterId)
                        .isPresent();
        if (!own) {
            throw new IllegalArgumentException("crew logo is not the requester's upload");
        }
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
