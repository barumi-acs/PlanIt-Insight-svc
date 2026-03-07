-- ShedLock 테이블 생성
-- 분산 환경에서 스케줄러 중복 실행 방지용

CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

-- 인덱스 생성 (성능 최적화)
CREATE INDEX idx_shedlock_lock_until ON shedlock(lock_until);
