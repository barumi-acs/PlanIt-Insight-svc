/** 
 * [planit 샘플 파일 - 컨트롤러 샘플]
 * 이 클래스는 planit 글로벌 룰을 적용한 샘플 컨트롤러입니다.
 * API생성 및 soft delete생성 시 참고할 것.
 * **나중에 삭제할 것**
 * @since 2026-02-26
 */
package com.planit.basetemplate.sample;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sample")
@RequiredArgsConstructor
public class SampleController {

    private final SampleService sampleService;

    // 브라우저 접속: http://localhost:8080/sample/create?name=jybill01
    @GetMapping("/create")
    public SampleData create(@RequestParam(defaultValue = "기본유저") String name) {
        return sampleService.createSample(name);
    }

    // 수정: http://localhost:8080/sample/update/1?newName=업데이트완료
    @GetMapping("/update/{id}")
    public SampleData update(@PathVariable Long id, @RequestParam String newName) {
        return sampleService.updateSample(id, newName);
    }

    // 브라우저 접속: http://localhost:8080/sample/delete/1
    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        return sampleService.deleteSample(id);
    }
}