/** 
 * [planit 샘플 파일 - 서비스 샘플]
 * 이 클래스는 planit 글로벌 룰을 적용한 샘플 서비스입니다.
 * 들어오는 데이터를(비즈니스 로직)을 처리하는 클래스로 사용합니다.
 * 서비스 생성 시 참고할 것.
 * **나중에 삭제할 것**
 * @since 2026-02-26
 */
package com.planit.basetemplate.sample;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SampleService {

    private final SampleRepository sampleRepository;

    // 1. 데이터 생성 실행
    @Transactional
    public SampleData createSample(String name) {
        SampleData data = new SampleData();
        data.setName(name);
        return sampleRepository.save(data); // DB에 INSERT
    }

    @Transactional
    public SampleData updateSample(Long id, String newName) {
        // 1. DB에서 기존 데이터를 꺼내옴 (없으면 에러 발생)
        SampleData data = sampleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("데이터가 없습니다. id=" + id));

        // 2. 값만 바꿈 (save() 호출 안 함!)
        data.setName(newName);

        return data; // 트랜잭션이 끝날 때 JPA가 알아서 DB에 UPDATE 쿼리를 날림
    }

    // 3. 데이터 삭제 실행 (Soft Delete)
    @Transactional
    public String deleteSample(Long id) {
        sampleRepository.deleteById(id); // @SQLDelete 작동하여 UPDATE 실행됨
        return id + "번 데이터 Soft Delete(삭제) 완료!";
    }
}