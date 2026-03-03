/** 
 * [planit 글로벌 룰 - DB 생성/수정 시간 자동화]
 * 모든 엔티티는 이 클레스를 상속받아야 합니다.
 * 상속시에 DB에 created_at, updated_at, deleted_at 컬럼이 자동으로 생성됩니다.
 * 단 deleted_at은 엔티티(Entity), 레포지토리(Repository), 서비스(Service)를 정의해야함.
 * (sample 폴더 참고할 것)
 * @since 2026-02-23
 */

package com.planit.basetemplate.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import java.time.LocalDateTime;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {
    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}