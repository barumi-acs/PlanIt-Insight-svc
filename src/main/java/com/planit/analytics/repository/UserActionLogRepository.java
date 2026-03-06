package com.planit.analytics.repository;

import com.planit.analytics.entity.UserActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * UserActionLog Repository
 * 
 * 사용자 행동 로그 CRUD
 */
@Repository
public interface UserActionLogRepository extends JpaRepository<UserActionLog, Long> {
    // 기본 CRUD 메서드만 사용
    // 배치 분석용 커스텀 쿼리는 필요 시 추가
}
