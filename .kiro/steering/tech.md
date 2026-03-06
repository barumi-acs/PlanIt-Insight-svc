# 기술 스택

## 빌드 시스템

- Gradle (Wrapper 사용)
- Java 17

## 핵심 프레임워크 및 라이브러리

- Spring Boot 3.5.11
- Spring Web (REST API)
- Spring Data JPA (ORM)
- MariaDB Driver
- Lombok (코드 자동 생성)
- Spring Boot Actuator (헬스체크)
- Micrometer Tracing (분산 추적)

## gRPC 관련

- net.devh:grpc-spring-boot-starter:2.15.0.RELEASE
- io.grpc:grpc-stub:1.58.0
- io.grpc:grpc-protobuf:1.58.0
- io.grpc:grpc-services:1.58.0 (reflection)
- com.google.protobuf 플러그인 0.9.4

## 주요 명령어

### 빌드 및 Protobuf 코드 생성
```bash
./gradlew clean build
```

### 애플리케이션 실행
```bash
./gradlew bootRun
```

### 테스트 실행
```bash
./gradlew test
```

### Docker 이미지 빌드
```bash
docker build -t planit-base-template .
```

### gRPC 테스트 (grpcurl 필요)
```bash
# 서비스 목록 확인
grpcurl -plaintext localhost:9090 list

# Ping 호출
grpcurl -plaintext -d '{"message":"Hello"}' localhost:9090 BaseTestGrpcService/Ping
```

## 플랫폼별 주의사항

### macOS (Apple Silicon)
build.gradle의 protobuf 설정에서 osx-aarch_64 아키텍처 지정 필요:
```gradle
protobuf {
    protoc { artifact = "com.google.protobuf:protoc:3.24.0:osx-aarch_64" }
    plugins { grpc { artifact = "io.grpc:protoc-gen-grpc-java:1.58.0:osx-aarch_64" } }
}
```

### Windows / 일반 환경
기본 artifact 사용:
```gradle
protobuf {
    protoc { artifact = "com.google.protobuf:protoc:3.24.0" }
    plugins { grpc { artifact = "io.grpc:protoc-gen-grpc-java:1.58.0" } }
}
```
