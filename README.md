# 🚀 PlanIt Base Template

본 레포지토리는 PlanIt MSA 프로젝트의 **공통 기반(Base Template)** 입니다.
모든 마이크로서비스(User, Schedule 등)는 이 템플릿을 복사하여 개발을 시작합니다. 
아래의 **[팀원 필수 개발 규칙]** 을 반드시 숙지하고 개발해 주시기 바랍니다.

---

## 📚 1. 기술 스택 및 라이브러리 (Tech Stack)

공통으로 세팅된 라이브러리 목록입니다. 임의로 버전을 변경하거나 외부 라이브러리를 추가하기 전 반드시 팀과 논의하세요.

* **Spring Web**: REST API 통신 및 내장 톰캣 서버 엔진
* **Spring Data JPA**: SQL 작성 없이 DB를 조작하는 자바 표준 기술
* **MariaDB Driver**: 자바와 MariaDB를 연결하는 전용 통신 케이블
* **Lombok**: Getter, 생성자, 로그 변수 등 반복 코드를 줄여주는 자동화 툴
* **Spring Boot Actuator**: 서버 생존 여부(UP/DOWN)를 확인하는 헬스체크 기능
* **Micrometer Tracing**: 여러 서버를 거치는 로그를 하나로 묶어주는 추적기 (Trace ID 자동 부여)

---

## 📦 2. 공통 응답 및 에러 규격

### ✅ 공통 응답 구조 (ApiResponse)
모든 API 응답은 `ApiResponse` 객체로 감싸서 반환해야 합니다.

```json
{
  "code": "C2001",
  "message": "성공",
  "data": { "userId": 1, "name": "test" },
  "timestamp": "2026-02-23T10:30:00"
}
```

---

## 🚨 3. 공통 에러 규격 (ErrorCode & CustomException)
에러 발생 시 **throw new CustomException(ErrorCode.코드)** 형태로 던지면 **GlobalExceptionHandler**가 가로채서 공통 포맷으로 변환합니다.
### [ 도메인별 에러 코드 규칙 ]
* **C(Common)** : C4001 (파라미터 누락), C4011 (토큰 만료), C5001 (서버 에러)
* **U(Common)** : U4001 (중복된 아이디), U4041 (유저 찾을 수 없음)
* **S(Common)** : S4031 (권한 없는 할 일 접근), S4041 (할 일 없음)
* **AI(Common)** : AI5001 (Bedrock 타임아웃), AI4001 (잘못된 프롬프트)
* **IS(Common)** : IS4041 (분석할 통계 데이터 부족)

---

## 📡 4. gRPC 베이스 템플릿 사용 가이드

이 문서는 gRPC 베이스 템플릿을 다운받아 사용하는 방법을 간단명료하게 안내합니다. 설치·빌드·실행·테스트 절차와 주요 설정(Gradle, Protobuf, macOS 특이사항), 문제해결 팁을 담고 있습니다.

**대상**: 템플릿을 받아 빠르게 gRPC 서비스를 띄워보고 싶은 개발자

**전제(권장 환경)**
- Java 17 이상
- Gradle(Wrapper 사용 권장) — `./gradlew` 실행 가능
- macOS / Linux / Windows 중 하나
- Homebrew(테스트용 `grpcurl` 설치 시, macOS)

**빠른 시작 요약**
1. 리포지토리 클론

```bash
git clone <your-repo-url>
cd <repo-root>
```

2. 빌드 및 Protobuf 코드 생성

```bash
./gradlew clean build
```

생성된 Java 코드: `build/generated/source/proto/`

3. 애플리케이션 실행 (Spring Boot)

```bash
./gradlew bootRun
```

4. grpcurl로 서비스 확인 및 호출 (예: 포트 9090)

```bash
# 서버에 등록된 서비스 목록 확인
grpcurl -plaintext localhost:9090 list
# Ping 호출 테스트
grpcurl -plaintext -d '{"message":"Hello from grpcurl!"}' localhost:9090 BaseTestGrpcService/Ping
```

**상세 가이드**

**1) build.gradle 주요 설정 예시**

- 플러그인

```gradle
plugins {
    id 'com.google.protobuf' version '0.9.4'
}
```

- 의존성

```gradle
dependencies {
    implementation 'net.devh:grpc-spring-boot-starter:2.15.0.RELEASE'
    implementation 'io.grpc:grpc-stub:1.58.0'
    implementation 'io.grpc:grpc-protobuf:1.58.0'
    implementation 'io.grpc:grpc-services:1.58.0' // reflection 사용
    compileOnly 'org.apache.tomcat:annotations-api:6.0.53'
}
```

- sourceSets (생성된 코드 포함)

```gradle
sourceSets {
    main {
        java {
            srcDirs 'build/generated/source/proto/main/grpc'
            srcDirs 'build/generated/source/proto/main/java'
        }
    }
}
```

**2) protobuf 플러그인 설정 (플랫폼별)**

- Windows / 일반

```gradle
protobuf {
    protoc { artifact = "com.google.protobuf:protoc:3.24.0" }
    plugins { grpc { artifact = "io.grpc:protoc-gen-grpc-java:1.58.0" } }
    generateProtoTasks { all()*.plugins { grpc {} } }
}
```

- macOS (Apple Silicon) — 기본 바이너리 문제 발생 시

```gradle
protobuf {
    protoc { artifact = "com.google.protobuf:protoc:3.24.0:osx-aarch_64" }
    plugins { grpc { artifact = "io.grpc:protoc-gen-grpc-java:1.58.0:osx-aarch_64" } }
    generateProtoTasks { all()*.plugins { grpc {} } }
}
```

**3) 예제 proto 파일**
- 위치: `src/main/proto/base_test.proto`

```proto
syntax = "proto3";
option java_package = "com.planit.basetemplate.grpc";
option java_multiple_files = true;

service BaseTestGrpcService {
  rpc Ping (PingRequest) returns (PongResponse);
}
message PingRequest { string message = 1; }
message PongResponse { string reply = 1; string trace_id = 2; }
```

**4) 샘플 gRPC 서비스 구현**
- 위치: `src/main/java/com/planit/basetemplate/grpc/BaseGrpcController.java`

간단한 Ping/Pong 예제로, `PongResponse`에 UUID 기반 `trace_id`를 포함합니다. Spring Boot gRPC starter의 `@GrpcService`로 서비스가 자동 등록됩니다.

**5) grpcurl을 활용한 테스트**
- 설치 (macOS):

```bash
brew install grpcurl
```

- 사용 예시:

```bash
grpcurl -plaintext localhost:9090 list
grpcurl -plaintext -d '{"message": "Hello from grpcurl!"}' localhost:9090 BaseTestGrpcService/Ping
```

출력 예시:

```json
{
  "reply": "Pong: Hello from grpcurl!",
  "trace_id": "<uuid>"
}
```

**6) 포트 및 설정**
- 기본적으로 Spring Boot의 `application.yml` / `application.properties`에서 서버 포트를 지정합니다. gRPC 포트는 보통 별도 구성(`grpc.server.port`)으로 관리됩니다. 템플릿의 기본 포트를 확인하고 필요시 변경하세요.

**7) Reflection(리플렉션) 활성화**
- `io.grpc:grpc-services` 의존성을 추가하면 grpcurl에서 reflection을 통해 서비스 목록을 쉽게 확인할 수 있습니다. Spring 설정에서 reflection을 활성화했는지 확인하세요.

**8) 문제해결(FAQ)**
- protoc 바이너리 오류(특히 macOS Apple Silicon): mac 전용 artifact(`osx-aarch_64`)를 지정하세요.
- generated 코드가 보이지 않음: `./gradlew clean build`로 강제 생성 후 `build/generated/source/proto/` 경로 확인.
- 포트 충돌: `grpc.server.port` 또는 Spring `server.port` 설정을 확인하고 변경.
- 서비스가 grpcurl에 보이지 않음: 애플리케이션이 정상 실행되었는지, reflection이 활성화되었는지 확인.

**9) 권장 커스터마이즈 항목**
- 인증: TLS/SSL 설정 또는 mTLS 적용(프로덕션 권장).
- 모니터링: gRPC 메트릭(예: Micrometer)과 연동.
- 에러 핸들링: gRPC Status 코드 매핑과 예외 변환 전략 수립.

---

## ⏰ 5. DB 시간 자동화 (JPA Auditing)
**[ 규칙 ]** 모든 엔티티(Entity)는 무조건 **extends BaseTimeEntity**를 상속받아야 합니다.
상속 시 DB 테이블에 **created_at(생성일)**, **updated_at(수정일)** 컬럼이 자동으로 생성되고 기록됩니다.
```java
@Entity
@Getter
@Table(name = "users")
public class UserEntity extends BaseTimeEntity { // 👈 필수 상속

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // createdAt, updatedAt 변수를 직접 적을 필요 없음!
}
```

---

## 🗑️ 6. Soft Delete 가이드라인 (Planit Global Rule)
프로젝트의 모든 데이터는 실수에 의한 삭제를 방지하고 데이터 복구 및 이력을 추적하기 위해 **물리적 삭제(Hard Delete)** 대신 **논리적 삭제(Soft Delete)** 방식을 채택합니다.

### 1. 작동 원리
사용자가 **delete()** 명령을 내리면 내부적으로 **UPDATE** 쿼리가 실행되어 **deleted_at** 컬럼에 삭제 시간이 기록됩니다. 이후 모든 **조회(SELECT)** 시 삭제된 데이터는 자동으로 필터링됩니다.

### 2. 새로운 엔티티 생성 시 필수 체크리스트
새로운 도메인(기능)을 개발할 때 아래 3단계를 반드시 지켜야 Soft Delete가 정상 작동합니다.

1단계: 엔티티(Entity) 설정
**BaseTimeEntity**를 반드시 **상속(extends)** 받습니다.

**@SQLDelete**와 **@SQLRestriction**을 추가하고 테이블 이름을 본인 것에 맞게 수정합니다.
```java
@Entity
@Getter
@Setter
@Table(name = "your_table_name") // 1. 테이블명 확인
@SQLDelete(sql = "UPDATE your_table_name SET deleted_at = CURRENT_TIMESTAMP(6) WHERE id = ?") // 2. 테이블명 일치 필수
@SQLRestriction("deleted_at IS NULL") // 3. 조회 필터 고정
public class YourEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // 추가 필드 작성...
}
```
2단계: 레포지토리(Repository) 생성
**JpaRepository<엔티티명, ID타입>**을 상속받습니다.
```java
@Repository
public interface YourRepository extends JpaRepository<YourEntity, Long> {
}
```
3단계: 서비스(Service)에서 삭제 호출
평소와 동일하게 **delete** 메서드를 호출합니다.
```java
@Service
@Transactional
public class YourService {
    private final YourRepository yourRepository;

    public void removeData(Long id) {
        // 실제 삭제가 아닌 Soft Delete(Update)가 실행됩니다.
        yourRepository.deleteById(id);
    }
}
```

---

## 🏥 7. 헬스체크 및 모니터링 (Actuator)
서버 생존 여부 확인을 위한 Actuator가 연동되어 있습니다.
* **planit-base-template/build.gradle**에 라이브러리 추가
```
dependencies {
    ......
	  //헬스체크 및 모니터링 (Actuator)
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    ......
}
```
* **planit-base-template/src/main/resources/application.yml**에 추가
```yaml
# /actuator/health 로 접속하면 서버 생존 여부 반환
management:
  endpoints:
    web:
      base-path: /api/v1/base/actuator
      exposure:
        include: health, info
```
* **접속 경로** : http://localhost:8080/api/v1/base/actuator/health
* **정상 응답** : {"status":"UP"}

---

## 📝 8. 로깅 및 트레이싱 (Micrometer & Logback)
SA 환경에서 요청을 추적하기 위해 모든 로그에는 **고유 식별자(TraceID)** 가 자동으로 부여되며, **logback-spring.xml**을 통해 포맷이 통일되어 있습니다.
* **planit-base-template/build.gradle**에 라이브러리 추가
```

dependencies {
    ......
    //로깅 및 분산 추적 (Micrometer Tracing) - 로그에 [TraceID] 자동 부여
    implementation 'io.micrometer:micrometer-tracing-bridge-brave'
    ......
}
```
### [ 로깅 규칙 ]
* **System.out.println** 절대 금지 ❌
* 클래스 상단에 **@Slf4j**를 붙이고 **log.info()**, **log.error()**를 사용 ⭕
* 변수 바인딩 시 **+** 연산자 대신 **{}** 사용
### [ 사용 예시 ]
```java
@Slf4j
@RestController
public class UserController {
    @PostMapping("/signup")
    public String signup(String userId) {
        log.info("회원가입 요청 들어옴. 요청 아이디: {}", userId);
        return "OK";
    }
}
```
### [ 로그 출력 예시 (TraceID 포함) ]
```bash
2026-02-23 16:30:00 INFO  [a1b2c3d4e5f6g7h8] [http-nio-8080-exec-1] c.p.UserController : 회원가입 요청 들어옴. 요청 아이디: abc
```

---

## 🐳 9. 빌드 및 배포 환경 (Dockerfile)
각 서비스 컨테이너화를 위한 공통 **Dockerfile**이 프로젝트 루트에 구성되어 있습니다.
```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY build/libs/*SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 🔐 10. 환경 설정 및 보안 가이드 (External Secrets Operator 기준)

프로젝트는 소스 코드 내 민감 정보(예: DB 비밀번호) 하드코딩을 금지합니다. 로컬 개발과 EKS 운영 환경을 분리하고, 운영에서는 AWS Secrets Manager + External Secrets Operator(ESO)를 통해 Secret을 안전하게 주입합니다.

참고 파일:
- [src/main/resources/application.yml](src/main/resources/application.yml)
- [dockerfile](dockerfile)
- [src/main/java/com/planit/basetemplate/PlanitBaseTemplateApplication.java](src/main/java/com/planit/basetemplate/PlanitBaseTemplateApplication.java)

핵심 원칙
- 애플리케이션 설정 파일은 환경변수 우선 사용: ${VAR:default} 패턴 권장.
- 운영( EKS )에서는 절대 secrets를 deployment manifest에 하드코딩하지 않습니다.
- 운영 시 시크릿 저장소: AWS Secrets Manager, 읽기/동기화: External Secrets Operator.

샘플: application.yml(권장)
```yaml
server:
  port: ${SERVER_PORT:8080}

spring:
  application:
    name: planit-base-service

  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:mariadb://localhost:3306/base_db}
    username: ${SPRING_DATASOURCE_USERNAME:root}
    password: ${SPRING_DATASOURCE_PASSWORD:root}
```

환경별 실행
- 로컬: 기본값 이용 또는 환경변수로 오버라이드
  - 예: export SPRING_DATASOURCE_URL=jdbc:... && ./gradlew bootRun
- Docker / Docker Compose: image 실행 시 environment로 주입
- EKS: AWS Secrets Manager에 시크릿 저장 → ESO가 Kubernetes Secret으로 동기화 → Deployment에서 env로 주입

예시: deployment.yaml (운영 시는 secret 이름만 참조)
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: planit-base
spec:
  replicas: 2
  template:
    spec:
      containers:
        - name: planit-base
          image: your-registry/planit-base:latest
          env:
            - name: SPRING_DATASOURCE_URL
              value: jdbc:mariadb://mydb:3306/base_db
            - name: SPRING_DATASOURCE_USERNAME
              valueFrom:
                secretKeyRef:
                  name: planit-db-secret
                  key: username
            - name: SPRING_DATASOURCE_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: planit-db-secret
                  key: password
```

예시: ExternalSecret 리소스 (ESO)
```yaml
apiVersion: external-secrets.io/v1beta1
kind: ExternalSecret
metadata:
  name: planit-db-external-secret
spec:
  refreshInterval: "1h"
  secretStoreRef:
    name: aws-secrets-manager
    kind: SecretStore
  target:
    name: planit-db-secret
    creationPolicy: Owner
  data:
    - secretKey: username
      remoteRef:
        key: /planit/prod/db
        property: username
    - secretKey: password
      remoteRef:
        key: /planit/prod/db
        property: password
```

### ✅ 최근 변경 — 로깅/예외 처리 보강 사항

운영 시 민감 정보 노출을 방지하고 내부 오류를 안전하게 로깅하도록 전역 예외 처리 및 gRPC 컨트롤러의 로깅 정책을 업데이트했습니다.

- 전역 예외 처리: 내부 예외는 스택트레이스로 서버 로그에만 기록하고, 클라이언트에는 일반화된 메시지(`C5001`)만 반환하도록 변경되었습니다. 관련 구현: [`GlobalExceptionHandler`](src/main/java/com/planit/basetemplate/common/GlobalExceptionHandler.java).  
- gRPC 컨트롤러: 요청 로그는 필요한 정보만 남기고 민감 데이터는 마스킹하도록 권장하며, 기본 Ping 로그는 남깁니다. 관련 구현: [`BaseGrpcController`](src/main/java/com/planit/basetemplate/grpc/BaseGrpcController.java).  
- 참조 설정 파일: [src/main/resources/application.yml](src/main/resources/application.yml)

변경된 코드(참조)
- 전역 예외 처리 내부 로깅 적용: 로그는 `log.error("Unhandled error", e)`로 남기고 응답 메시지는 `ErrorCode.C5001.getMessage()` 사용.
- gRPC 기본 로그: `log.info("gRPC Ping 요청 수신: {}", request.getMessage());`

보안/운영 지침
- 로그에 민감값(비밀번호, 토큰 등)을 남기지 마세요. 필요 시 마스킹 함수를 사용하십시오.
- 이미 커밋된 시크릿은 즉시 회수하고 교체하세요.

운영 체크리스트
1. AWS Secrets Manager에 시크릿 생성 (예: /planit/prod/db).  
2. ESO 구성(SecretStore) 및 권한(IAM) 설정.  
3. ExternalSecret 생성으로 K8s Secret 자동 생성 확인.  
4. Deployment에서 valueFrom.secretKeyRef로 참조.  
5. CI/CD와 이미지 빌드 시 시크릿 포함 금지(환경변수 주입 방식 사용).  
6. Git에 민감 파일이 커밋된 경우 즉시 교체 및 자격증명 회수.

간단 요약: 설정은 [src/main/resources/application.yml](src/main/resources/application.yml) 에서 환경변수 우선으로 유지하고, EKS 운영 시에는 AWS Secrets Manager + ESO로 안전하게 시크릿을 주입하세요.
