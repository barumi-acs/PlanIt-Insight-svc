/** 
 * [planit 샘플 파일 - 엔티티 샘플]
 * 이 클래스는 planit 글로벌 룰을 적용한 샘플 엔티티입니다.
 * 데이터베이스의 테이블을 자바로 정의하기 위하서 사용합니다.
 * 엔티티 생성 및 soft delete생성 시 참고할 것.
 * **나중에 삭제할 것**
 * @since 2026-02-26
 */
package com.planit.basetemplate.sample;

import com.planit.basetemplate.common.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "sample_table")
@SQLDelete(sql = "UPDATE sample_table SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?") // soft delete를 위한 가로채기 설정
@SQLRestriction("deleted_at IS NULL") // soft delete를 위한 설정
public class SampleData extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
}