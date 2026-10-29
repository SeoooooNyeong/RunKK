package com.a207.runkk.runimal;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 16유형 목록, 유형 상세, 유형 비율 재계산(설계 5.6.2, SQ-34, FR-226, D-245·D-246). 비율은 매일 0시
 * batch.runimal.RunimalShareJob이 다시 쓰고, 조회는 그 스냅숏만 읽는다.
 */
@Service
public class RunimalTypeService {

    private static final Logger log = LoggerFactory.getLogger(RunimalTypeService.class);

    private final RunimalProfileRepository runimalProfileRepository;
    private final RunimalTypeShareRepository runimalTypeShareRepository;
    private final Clock clock;

    public RunimalTypeService(
            RunimalProfileRepository runimalProfileRepository,
            RunimalTypeShareRepository runimalTypeShareRepository,
            Clock clock) {
        this.runimalProfileRepository = runimalProfileRepository;
        this.runimalTypeShareRepository = runimalTypeShareRepository;
        this.clock = clock;
    }

    /** 16유형과 유형마다 확정받은 적이 있는지, 지금 확정 유형. */
    @Transactional(readOnly = true)
    public RunimalTypeCatalog catalog(long memberId) {
        RunimalProfile profile = runimalProfileRepository.findOrCreate(memberId);
        List<RunimalTypeCatalog.Entry> entries =
                Arrays.stream(RunimalType.values())
                        .map(t -> new RunimalTypeCatalog.Entry(t, profile.hasEverConfirmed(t)))
                        .toList();
        return new RunimalTypeCatalog(profile.getConfirmedType().orElse(null), entries);
    }

    /** 유형 상세. 비율은 마지막 0시 스냅숏이고 확정자가 없으면 null이다. */
    @Transactional(readOnly = true)
    public RunimalTypeDetail detail(RunimalType type) {
        Optional<RunimalTypeShare> share =
                Optional.ofNullable(runimalTypeShareRepository.findAll().get(type));
        Integer percent =
                share.flatMap(
                                s ->
                                        s.percent().isPresent()
                                                ? Optional.of(s.percent().getAsInt())
                                                : Optional.empty())
                        .orElse(null);
        return new RunimalTypeDetail(
                type, type.traits(), percent, share.map(RunimalTypeShare::computedAt).orElse(null));
    }

    /** 지금 확정 유형별 수로 16행을 다시 쓴다. 확정자 0인 유형도 행을 둔다. */
    @Transactional
    public void recomputeShares(LocalDateTime now) {
        Map<RunimalType, Integer> counts = runimalTypeShareRepository.countConfirmedByType();
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        List<RunimalTypeShare> shares = new ArrayList<>();
        for (RunimalType type : RunimalType.values()) {
            shares.add(RunimalTypeShare.of(type, counts.getOrDefault(type, 0), total, now));
        }
        runimalTypeShareRepository.replaceAll(shares);
        log.info("runimal_share_recomputed total={}", total);
    }

    /** 지금 시각으로 다시 계산한다(작업용). */
    public void recomputeShares() {
        recomputeShares(LocalDateTime.now(clock));
    }
}
