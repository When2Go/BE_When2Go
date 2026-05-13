# Monitoring Setup Implementation Plan

**Goal:** Grafana를 API Server EC2 docker-compose에 올리고, CloudWatch datasource 연결로 EC2/RDS/SQS/DLQ/Lambda 지표가 실제로 조회되는지 확인한다.

**Architecture:** Grafana를 기존 docker-compose.yml에 서비스로 추가. CloudWatch datasource를 provisioning으로 자동 등록. Prometheus/actuator 연동은 2단계에서 진행.

**Tech Stack:** Docker Compose, Grafana 10.4.0, AWS CloudWatch

---

## 파일 구조

```
BE_When2Go/
├── docker-compose.yml                           # 수정: grafana 서비스 추가
├── grafana/
│   └── provisioning/
│       └── datasources/
│           └── datasources.yml                 # 신규: CloudWatch datasource 자동 등록
└── .github/workflows/
    └── deploy.yml                              # 수정: .env에 GRAFANA_ADMIN_PASSWORD 주입
```

---

## Task 1: Grafana datasource provisioning 설정

**Files:**
- Create: `grafana/provisioning/datasources/datasources.yml`

Grafana는 기동 시 `/etc/grafana/provisioning/datasources/` 아래 YAML 파일을 자동으로 읽어 datasource를 등록한다.

- [ ] **Step 1: 디렉토리 생성**

```bash
mkdir -p grafana/provisioning/datasources
```

- [ ] **Step 2: datasources.yml 생성**

`grafana/provisioning/datasources/datasources.yml`:

```yaml
apiVersion: 1

datasources:
  - name: CloudWatch
    type: cloudwatch
    access: proxy
    isDefault: true
    jsonData:
      authType: keys
      defaultRegion: ap-northeast-2
    secureJsonData:
      accessKey: ${GF_AWS_ACCESS_KEY_ID}
      secretKey: ${GF_AWS_SECRET_ACCESS_KEY}
    editable: false
```

`${GF_AWS_ACCESS_KEY_ID}`, `${GF_AWS_SECRET_ACCESS_KEY}`는 Grafana 기동 시 환경변수에서 읽는다. Task 2의 docker-compose에서 주입.

---

## Task 2: docker-compose.yml에 Grafana 서비스 추가

**Files:**
- Modify: `docker-compose.yml`

- [ ] **Step 1: grafana 서비스 추가**

`services:` 블록 하단에 추가:

```yaml
  grafana:
    image: grafana/grafana:10.4.0
    container_name: when2go-grafana
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_USER: admin
      GF_SECURITY_ADMIN_PASSWORD: ${GRAFANA_ADMIN_PASSWORD}
      GF_AWS_ACCESS_KEY_ID: ${AWS_ACCESS_KEY_ID}
      GF_AWS_SECRET_ACCESS_KEY: ${AWS_SECRET_ACCESS_KEY}
      GF_USERS_ALLOW_SIGN_UP: "false"
    volumes:
      - ./grafana/provisioning:/etc/grafana/provisioning:ro
      - grafana-data:/var/lib/grafana
    networks:
      - when2go-net
    restart: unless-stopped
```

- [ ] **Step 2: volumes 블록에 grafana-data 추가**

```yaml
volumes:
  redis-data:
  grafana-data:      # 추가
```

- [ ] **Step 3: compose 유효성 확인**

```bash
docker compose config
```

Expected: 에러 없이 전체 설정 출력.

---

## Task 3: deploy workflow에 GRAFANA_ADMIN_PASSWORD 주입

**Files:**
- Modify: `.github/workflows/deploy.yml`

- [ ] **Step 1: GitHub Secrets에 GRAFANA_ADMIN_PASSWORD 추가**

GitHub 레포 → Settings → Secrets and variables → Actions → New repository secret:
- Name: `GRAFANA_ADMIN_PASSWORD`
- Value: 팀이 사용할 비밀번호

- [ ] **Step 2: deploy.yml API EC2 .env 블록에 추가**

"Deploy to API EC2" step의 `cat > .env <<'EOF'` 블록에 한 줄 추가:

```yaml
            cat > .env <<'EOF'
            APP_IMAGE=${{ steps.login-ecr.outputs.registry }}/${{ secrets.ECR_REPOSITORY }}:${{ env.IMAGE_TAG }}
            AWS_REGION=${{ secrets.AWS_REGION }}
            DB_URL=${{ secrets.DB_URL }}
            DB_USERNAME=${{ secrets.DB_USERNAME }}
            DB_PWD=${{ secrets.DB_PWD }}
            GRAFANA_ADMIN_PASSWORD=${{ secrets.GRAFANA_ADMIN_PASSWORD }}
            EOF
```

> Scheduler EC2 `.env` 블록에는 추가하지 않는다. Grafana는 API Server EC2에만 올라간다.

---

## Task 4: IAM 권한 확인

Grafana CloudWatch datasource가 필요한 최소 권한:

```
cloudwatch:GetMetricData
cloudwatch:ListMetrics
cloudwatch:GetMetricStatistics
cloudwatch:DescribeAlarmsForMetric
ec2:DescribeTags
ec2:DescribeInstances
```

- [ ] **Step 1: IAM Console에서 해당 유저 정책 확인**

AWS Console → IAM → Users → 해당 유저 → Permissions 탭에서 위 권한 포함 여부 확인.

권한이 없다면 `CloudWatchReadOnlyAccess` managed policy를 추가하거나 위 권한을 인라인 정책으로 추가.

---

## Task 5: EC2 Security Group 포트 오픈

- [ ] **Step 1: 포트 3000 오픈**

AWS Console → EC2 → `when2go-api-server` → Security Groups → Inbound rules → Add rule:
- Type: Custom TCP, Port range: 3000, Source: 팀 IP (전체 오픈 지양)

---

## Task 6: 배포 후 CloudWatch 연결 검증

- [ ] **Step 1: 배포 완료 확인**

GitHub Actions 탭에서 워크플로우 성공 확인.

- [ ] **Step 2: Grafana 접속**

브라우저: `http://<API_SERVER_IP>:3000`
- Username: `admin`
- Password: 설정한 `GRAFANA_ADMIN_PASSWORD`

- [ ] **Step 3: CloudWatch datasource 연결 테스트**

Grafana → Connections → Data sources → `CloudWatch` → "Save & Test"

Expected: `"Data source is working"`

실패 시: IAM 권한 부족 가능성 → Task 4 재확인.

- [ ] **Step 4: 실제 메트릭 조회 확인**

Grafana → Explore → Data source: CloudWatch 선택 후 아래 조합으로 조회:

| Region | Namespace | Metric | Dimension |
|--------|-----------|--------|-----------|
| ap-northeast-2 | AWS/SQS | ApproximateNumberOfMessagesVisible | QueueName = when2go-dlq |
| ap-northeast-2 | AWS/EC2 | CPUUtilization | InstanceId = (API Server instance ID) |
| ap-northeast-2 | AWS/Lambda | Invocations | FunctionName = when2go-alarm-notification |

각 항목에서 "Run query" 클릭 후 데이터가 표시되면 성공.

---

## 2단계 (이후)

- Prometheus + actuator RED 지표 연동 (`prometheus.yml`, docker-compose prometheus 서비스 추가)
- 대시보드 3개 구성 (Application RED / Infrastructure / Queue & Lambda)
