/** 
 * [planit 샘플 파일 - 레포지토리 샘플]
 * 이 클래스는 planit 글로벌 룰을 적용한 샘플 레포지토리입니다.
 * 데이터베이스와 스프링을 연결하기 위한 통로로 사용합니다.
 * 레포지토리 생성시 참고할 것.
 * **나중에 삭제할 것**
 * @since 2026-02-26
 */
package com.planit.basetemplate.sample;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SampleRepository extends JpaRepository<SampleData, Long> {

}
