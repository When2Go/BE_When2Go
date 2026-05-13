# Monitoring Setup Design

**작성일:** 2026-05-13
**상태:** 승인됨

---

## 목표

Grafana + CloudWatch + Prometheus를 활용해 AWS 인프라, Lambda, SQS/DLQ, 애플리케이션 RED 지표를 통합 모니터링한다.
알림(Alert) 기능은 이번 범위에서 제외하고, 시각화 뼈대 구성에 집중한다.

---

## 전체 아키텍처

```
API Server EC2
├── [Docker] Spring Boot App       → :8080
│     └── /actuator/prometheus     (Prometheus scrape 대상)
├── [Docker] Prometheus            → :9090
│     └── scrapes app:8080/actuator/prometheus
└── [Docker] Grafana               → :3000
      ├── Data Source 1: Prometheus  (RED 지표)
      └── Data Source 2: CloudWatch  (인프라/Lambda/SQS 지표)

CloudWatch (AWS 관리형)
├── EC2: API Server, Scheduler
├── RDS: when2go-rds
├── SQS: when2go-queue, when2go-dlq
└── Lambda: when2go-alarm-notification, when2go-voice-parser
```

- Grafana, Prometheus는 기존 `docker-compose.yml`에 서비스로 추가
- Prometheus는 내부 Docker 네트워크에서 앱의 `/actuator/prometheus`를 scrape커밋
- Grafana는 CloudWatch API를 직접 호출해 인프라 지표를 가져옴
- 접근: admin 단일 계정으로 팀 공유

---

## 수집 지표

### CloudWatch 지표

| 리소스 | 지표 | 이유 |
|--------|------|------|
| EC2 API Server | CPUUtilization, NetworkIn, NetworkOut | 서버 부하 파악 |
| EC2 Scheduler | CPUUtilization | 스케줄러 이상 감지 |
| RDS | CPUUtilization, DatabaseConnections, FreeableMemory, ReadLatency, WriteLatency | DB 병목 감지 |
| SQS when2go-queue | ApproximateNumberOfMessagesVisible, ApproximateAgeOfOldestMessage, NumberOfMessagesSent | 큐 적체 여부 |
| DLQ when2go-dlq | ApproximateNumberOfMessagesVisible, NumberOfMessagesSent | 처리 실패 감지 (핵심 지표) |
| Lambda alarm-notification | Invocations, Errors, Duration, Throttles | 알림 전송 성공률/지연 |
| Lambda voice-parser | Invocations, Errors, Duration, Throttles | 음성 파싱 성공률/지연 |

> EC2 Memory 지표는 CloudWatch agent 설치가 필요하므로 이번 뼈대에서 제외. 필요 시 추가.

### Prometheus 지표 (Spring Boot actuator)

| 분류 | 지표 | 설명 |
|------|------|------|
| Rate | `http_server_requests_seconds_count` | 초당 요청 수 (RPS) |
| Errors | `http_server_requests_seconds_count{status=~"5.."}` | 5xx 에러율 |
| Duration | `http_server_requests_seconds` histogram | p50 / p95 / p99 레이턴시 |
| JVM Memory | `jvm_memory_used_bytes` | 힙 사용량 |
| JVM GC | `jvm_gc_pause_seconds` | GC 부하 |

---

## 대시보드 구성

### 대시보드 1 — Application RED (Prometheus)

| 패널 | 시각화 |
|------|--------|
| RPS (초당 요청 수) | Time series |
| 5xx 에러율 (%) | Time series |
| p50 / p95 / p99 레이턴시 | Time series |
| JVM 힙 사용량 | Gauge |
| GC pause 시간 | Time series |

### 대시보드 2 — Infrastructure (CloudWatch)

| 패널 | 대상 |
|------|------|
| CPU (API Server, Scheduler) | EC2 두 대 |
| Network In/Out | EC2 API Server |
| DB CPU / Connections / Latency | RDS |
| DB FreeableMemory | RDS |

### 대시보드 3 — Queue & Lambda (CloudWatch)

| 패널 | 대상 |
|------|------|
| SQS 큐 적체 (Visible Messages) | when2go-queue |
| SQS 메시지 최대 대기 시간 | when2go-queue |
| DLQ 메시지 수 (핵심) | when2go-dlq |
| Lambda Invocations / Errors / Duration | alarm-notification, voice-parser |
| Lambda Throttles | alarm-notification, voice-parser |

---

## 구성 파일

```
BE_When2Go/
├── docker-compose.yml                          # Grafana, Prometheus 서비스 추가
├── prometheus.yml                              # scrape 설정 (신규)
└── grafana/
    └── provisioning/
        ├── datasources/
        │   └── datasources.yml                # CloudWatch + Prometheus 자동 등록 (신규)
        └── dashboards/
            └── dashboards.yml                 # 대시보드 자동 로드 설정 (신규)
```

Spring Boot `application.yml`에서 actuator prometheus 엔드포인트 노출 확인 필요.

---

## 필요 환경변수

| 변수 | 설명 | 비고 |
|------|------|------|
| `GRAFANA_ADMIN_PASSWORD` | Grafana admin 비밀번호 | 신규 추가 |
| `AWS_ACCESS_KEY_ID` | CloudWatch 읽기용 IAM 키 | 기존 키 재사용 가능 |
| `AWS_SECRET_ACCESS_KEY` | CloudWatch 읽기용 IAM 시크릿 | 기존 키 재사용 가능 |

### IAM 필요 권한 (CloudWatch data source용)

```
cloudwatch:GetMetricData
cloudwatch:ListMetrics
cloudwatch:GetMetricStatistics
cloudwatch:DescribeAlarmsForMetric
ec2:DescribeTags
ec2:DescribeInstances
```

기존 deploy workflow에서 EC2에 주입되는 `AWS_ACCESS_KEY_ID` / `AWS_SECRET_ACCESS_KEY`를 재사용하되, 위 권한이 포함되어 있는지 확인 필요.

---

## 범위 외

- Alert / 알림 기능 (추후)
- EC2 Memory 지표 (CloudWatch agent 필요, 추후)
- Scheduler EC2에 별도 모니터링 스택 (API 서버에서 통합)
