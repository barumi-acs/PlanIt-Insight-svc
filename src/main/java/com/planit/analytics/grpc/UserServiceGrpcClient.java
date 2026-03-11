package com.planit.analytics.grpc;

import com.planit.grpc.user.CategoryInfo;
import com.planit.grpc.user.GetCategoriesRequest;
import com.planit.grpc.user.GetCategoriesResponse;
import com.planit.grpc.user.UserServiceGrpc;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * User-svc gRPC 클라이언트 (카테고리 동기화용)
 */
@Component
public class UserServiceGrpcClient {

    @GrpcClient("user-service")
    private UserServiceGrpc.UserServiceBlockingStub userServiceStub;

    /**
     * User-svc 로부터 전체 카테고리 목록을 조회합니다.
     */
    public List<CategoryInfo> getCategories() {
        GetCategoriesRequest request = GetCategoriesRequest.newBuilder().build();
        GetCategoriesResponse response = userServiceStub.getCategories(request);
        return response.getCategoriesList();
    }
}
