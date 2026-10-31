package com.a207.runkk.running;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 평가를 일으킨 기록에 그 판정의 Runimal 확정 유형을 적는다(v0.14 D-291, running 공개 서비스). runimal.RunimalService가 확정
 * 판정에서 부른다(기존 runimal → running 화살표). runimal 형식을 running에 들이지 않으려고 유형을 이름 문자열로 받는다. 한 열 갱신이라
 * TierApplier의 평가 표시와 순서가 바뀌어도 된다.
 */
@Service
public class RunEvaluationNote {

    private final RunRecordRepository runRecordRepository;

    public RunEvaluationNote(RunRecordRepository runRecordRepository) {
        this.runRecordRepository = runRecordRepository;
    }

    @Transactional
    public void noteRunimalType(String recordId, String runimalType) {
        runRecordRepository.noteRunimalType(recordId, runimalType);
    }
}
