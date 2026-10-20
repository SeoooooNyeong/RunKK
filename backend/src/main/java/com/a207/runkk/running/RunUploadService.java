package com.a207.runkk.running;

import com.a207.runkk.common.BusinessRuleException;
import com.a207.runkk.common.DomainEventPublisher;
import com.a207.runkk.common.GeoPoint;
import com.a207.runkk.common.PermissionDeniedException;
import com.a207.runkk.common.ReasonCode;
import com.a207.runkk.member.ConsentItem;
import com.a207.runkk.member.MemberQueryService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 러닝 기록 수신 유스케이스를 조정한다(설계 6.1.3, A-04, SQ-02). 날씨 조회(최대 3초) 동안 DB 연결을 잡지 않도록 저장 부분만 트랜잭션으로 묶는다. 같은
 * 회원의 업로드는 PersonalRecordBook 행 잠금으로 직렬화해 유효 러닝 순번이 겹치지 않는다(5.3, D-37).
 */
@Service
public class RunUploadService {

    private static final Logger log = LoggerFactory.getLogger(RunUploadService.class);
    private static final int WEATHER_DECIMALS = 2;

    private final RunRecordRepository runRecordRepository;
    private final PersonalRecordBookRepository personalRecordBookRepository;
    private final WeatherGateway weatherGateway;
    private final MemberQueryService memberQueryService;
    private final DomainEventPublisher eventPublisher;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public RunUploadService(
            RunRecordRepository runRecordRepository,
            PersonalRecordBookRepository personalRecordBookRepository,
            WeatherGateway weatherGateway,
            MemberQueryService memberQueryService,
            DomainEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.runRecordRepository = runRecordRepository;
        this.personalRecordBookRepository = personalRecordBookRepository;
        this.weatherGateway = weatherGateway;
        this.memberQueryService = memberQueryService;
        this.eventPublisher = eventPublisher;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * 같은 recordId면 alreadyStored. 수신 → 유효 판정 → 날씨(경로 있을 때, coarsen(2)) → [트랜잭션: 장부 행 잠금 → 순번 = 유효
     * 기록 수 + 1(무효면 0) → 최고 기록 갱신 → 저장 → RunRecordSaved 발행]. 심박 동의가 없으면 심박은 저장하지 않는다(NFR-023).
     */
    public UploadResult upload(long memberId, RunUploadCommand command) {
        RunUpload upload = command.toUpload();
        if (runRecordRepository.exists(upload.recordId())) {
            return UploadResult.alreadyStored(upload.recordId());
        }
        if (!upload.heartRates().isEmpty()
                && !memberQueryService.hasConsent(memberId, ConsentItem.HEART_RATE)) {
            upload = upload.withoutHeartRates();
        }
        RunRecord record = RunRecord.receive(memberId, upload);
        record.judgeValidity(ValidityRule.STANDARD);
        record.track()
                .firstPoint()
                .flatMap(p -> weatherAt(p, record))
                .ifPresent(record::attachWeather);
        return transaction.execute(status -> store(memberId, record));
    }

    /** 늦게 온 심박 덧붙이기(FR-043). 기록 주인만. 심박 동의가 없거나 원자료를 파기한 기록이면 버린다. */
    @Transactional
    public void appendLateHeartRates(long memberId, LateHeartRateCommand command) {
        RunRecord record =
                runRecordRepository
                        .findById(command.recordId())
                        .orElseThrow(() -> new BusinessRuleException(ReasonCode.RECORD_NOT_FOUND));
        if (record.getMemberId() != memberId) {
            throw new PermissionDeniedException("남의 기록");
        }
        if (!memberQueryService.hasConsent(memberId, ConsentItem.HEART_RATE)) {
            log.info("late_heart_rates_dropped reason=no_consent");
            return;
        }
        try {
            record.appendHeartRates(command.samples());
        } catch (IllegalStateException e) {
            log.info("late_heart_rates_dropped reason=purged");
            return;
        }
        runRecordRepository.save(record);
    }

    private UploadResult store(long memberId, RunRecord record) {
        PersonalRecordBook book = personalRecordBookRepository.findOrCreateForUpdate(memberId);
        if (runRecordRepository.exists(record.getRecordId())) {
            return UploadResult.alreadyStored(record.getRecordId());
        }
        int ordinal = record.isValid() ? runRecordRepository.countValid(memberId) + 1 : 0;
        record.markNewRecords(book.apply(record));
        runRecordRepository.save(record);
        personalRecordBookRepository.save(book);
        eventPublisher.publish(RunRecordSaved.of(record, ordinal, LocalDateTime.now(clock)));
        return UploadResult.stored(record.getRecordId(), record.isValid());
    }

    private Optional<Weather> weatherAt(GeoPoint firstPoint, RunRecord record) {
        try {
            return weatherGateway.lookup(
                    firstPoint.coarsen(WEATHER_DECIMALS), record.getStartedAt());
        } catch (RuntimeException e) {
            log.warn("weather_lookup_failed error={}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
