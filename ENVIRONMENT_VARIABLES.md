# 환경 변수 설정 가이드

## 개요

PlanIt-Insight-svc는 환경별로 다른 설정을 적용할 수 있도록 환경 변수를 지원합니다.

## 환경 변수 목록

### 서버 설정

| 환경 변수 | 기본값 | 설명 |
|----------|--------|------|
| `SERVER_PORT` | `8084` | Insight 서비스 HTTP 포트 |
| `GRPC_SERVER_PORT` | `9094` | Insight 서비스 gRPC 서버 포트 (ActionLog 서비스) |

### 데이터베이스 설정

| 환경 변수 | 기본값 | 설명 |
|----------|--------|------|
| `SPRING_DATASOURCE_URL` | `jdbc:mariadb://localhost:3306/planit_insight_db` | MariaDB 연결 URL |
| `SPRING_DATASOURCE_USERNAME` | `root` | 데이터베이스 사용자명 |
| `SPRING_DATASOURCE_PASSWORD` | `root` | 데이터베이스 비밀번호 |
| `SHOW_SQL` | `false` | JPA SQL 로그 출력 여부 |

### InsightAI 서비스 연동 (REST API)

| 환경 변수 | 기본값 | 설명 |
|----------|--------|------|
| `SERVICE_B_BASE_URL` | `http://localhost:8085` | InsightAI FastAPI 서버 URL |
| `SERVICE_B_TIMEOUT` | `10000` | 타임아웃 (밀리초) |

### gRPC 클라이언트 설정

| 환경 변수 | 기본값 | 설명 |
|----------|--------|------|
| `GRPC_CHAT_SERVICE_ADDRESS` | `static://localhost:9095` | Chatbot gRPC 서버 주소 |
| `GRPC_REPORT_SERVICE_ADDRESS` | `static://localhost:9095` | Report gRPC 서버 주소 |

### AWS DynamoDB 설정

| 환경 변수 | 기본값 | 설명 |
|----------|--------|------|
| `DYNAMODB_TABLE_NAME` | `ai_reports` | DynamoDB 테이블명 |
| `AWS_REGION` | `us-east-1` | AWS 리전 |
| `DYNAMODB_ENDPOINT` | `http://localhost:8001` | DynamoDB 엔드포인트 (로컬 개발용) |

## 환경별 설정 예시

### 로컬 개발 환경

기본값을 사용하므로 별도 설정 불필요:

```bash
./gradlew bootRun
```

### 개발 서버 (Docker)

```bash
docker run -e SERVER_PORT=8084 \
  -e SPRING_DATASOURCE_URL=jdbc:mariadb://db-server:3306/planit_insight_db \
  -e SPRING_DATASOURCE_USERNAME=planit_user \
  -e SPRING_DATASOURCE_PASSWORD=secure_password \
  -e GRPC_CHAT_SERVICE_ADDRESS=static://insightai-service:9095 \
  -e GRPC_REPORT_SERVICE_ADDRESS=static://insightai-service:9095 \
  -e DYNAMODB_ENDPOINT=http://dynamodb-local:8000 \
  planit-insight-svc
```

### 프로덕션 환경

```bash
export SERVER_PORT=8084
export SPRING_DATASOURCE_URL=jdbc:mariadb://prod-db.example.com:3306/planit_insight_db
export SPRING_DATASOURCE_USERNAME=prod_user
export SPRING_DATASOURCE_PASSWORD=prod_secure_password
export GRPC_CHAT_SERVICE_ADDRESS=static://insightai-prod:9095
export GRPC_REPORT_SERVICE_ADDRESS=static://insightai-prod:9095
export DYNAMODB_ENDPOINT=  # 비워두면 AWS 클라우드 DynamoDB 사용
export AWS_REGION=ap-northeast-2

./gradlew bootRun
```

### Kubernetes ConfigMap/Secret

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: insight-config
data:
  SERVER_PORT: "8084"
  SPRING_DATASOURCE_URL: "jdbc:mariadb://mariadb-service:3306/planit_insight_db"
  GRPC_CHAT_SERVICE_ADDRESS: "static://insightai-service:9095"
  GRPC_REPORT_SERVICE_ADDRESS: "static://insightai-service:9095"
  AWS_REGION: "ap-northeast-2"
  DYNAMODB_TABLE_NAME: "ai_reports"
---
apiVersion: v1
kind: Secret
metadata:
  name: insight-secret
type: Opaque
stringData:
  SPRING_DATASOURCE_USERNAME: "planit_user"
  SPRING_DATASOURCE_PASSWORD: "secure_password"
```

## gRPC 주소 형식

gRPC 주소는 다음 형식을 지원합니다:

### Static (고정 주소)

```
static://hostname:port
```

예시:
- `static://localhost:9095` (로컬)
- `static://insightai-service:9095` (Docker/K8s 서비스명)
- `static://192.168.1.100:9095` (IP 주소)

### DNS (서비스 디스커버리)

```
dns:///hostname:port
```

예시:
- `dns:///insightai-service.default.svc.cluster.local:9095` (K8s DNS)

## 주의사항

### 1. DynamoDB 엔드포인트

- **로컬 개발**: `http://localhost:8001` (DynamoDB Local)
- **프로덕션**: 환경 변수를 비워두거나 설정하지 않으면 AWS 클라우드 DynamoDB 사용

### 2. gRPC 서비스 주소

- Chatbot과 Report 서비스는 동일한 포트(9095)를 사용
- gRPC Multiplexing으로 단일 포트에서 여러 서비스 제공
- 별도로 분리하려면 각각 다른 주소 설정 가능

### 3. 데이터베이스 비밀번호

- 프로덕션 환경에서는 반드시 Secret 관리 도구 사용 (K8s Secret, AWS Secrets Manager 등)
- 환경 변수에 직접 노출하지 않도록 주의

## 설정 우선순위

Spring Boot의 설정 우선순위:

1. 명령줄 인자 (`--server.port=8084`)
2. 환경 변수 (`SERVER_PORT=8084`)
3. `application.yml` 기본값

## 검증 방법

### 설정 확인

서버 시작 시 로그에서 확인:

```
Server started on port: 8084
DataSource URL: jdbc:mariadb://localhost:3306/planit_insight_db
gRPC Chat Service: static://localhost:9095
gRPC Report Service: static://localhost:9095
```

### Health Check

```bash
curl http://localhost:8084/api/v1/insight/actuator/health
```

### Swagger UI

```
http://localhost:8084/swagger-ui.html
```

## 문제 해결

### gRPC 연결 실패

```
io.grpc.StatusRuntimeException: UNAVAILABLE: io exception
```

해결:
1. InsightAI-svc gRPC 서버가 실행 중인지 확인
2. `GRPC_*_SERVICE_ADDRESS` 환경 변수 확인
3. 방화벽/네트워크 설정 확인

### 데이터베이스 연결 실패

```
Communications link failure
```

해결:
1. MariaDB 서버 실행 확인
2. `SPRING_DATASOURCE_*` 환경 변수 확인
3. 네트워크 연결 확인

## 참고

- `application.yml` - 전체 설정 파일
- `GRPC_UNIFIED_ARCHITECTURE.md` - gRPC 아키텍처 문서
- `GRPC_REPORT_INTEGRATION_GUIDE.md` - gRPC Report 통합 가이드
