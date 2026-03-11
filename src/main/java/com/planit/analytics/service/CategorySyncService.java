package com.planit.analytics.service;

import com.planit.analytics.grpc.UserServiceGrpcClient;
import com.planit.analytics.repository.CategoryListRepository;
import com.planit.grpc.user.CategoryInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * User-svc의 category 테이블을 로컬 category_list 테이블로 동기화합니다.
 *
 * <p>동기화 시점:
 * <ul>
 *   <li>서비스 시작 시 (ApplicationReadyEvent)</li>
 *   <li>매 시간 정각 (@Scheduled cron)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategorySyncService {

    private final UserServiceGrpcClient userServiceGrpcClient;
    private final CategoryListRepository categoryListRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        log.info("🔄 [CategorySync] 서비스 시작 시 카테고리 동기화 시작...");
        sync();
    }

    @Scheduled(cron = "0 0 * * * *")
    public void syncPeriodically() {
        log.info("🔄 [CategorySync] 정기 카테고리 동기화 시작...");
        sync();
    }

    private void sync() {
        try {
            List<CategoryInfo> categories = userServiceGrpcClient.getCategories();

            for (CategoryInfo info : categories) {
                categoryListRepository.upsert(
                        info.getCategoryId(),
                        info.getName(),
                        info.getColorHex(),
                        info.getDescription()
                );
            }

            log.info("✅ [CategorySync] 카테고리 동기화 완료: {}개", categories.size());
        } catch (Exception e) {
            log.error("❌ [CategorySync] 카테고리 동기화 실패 (User-svc 연결 불가): {}", e.getMessage());
        }
    }
}
