# EC2 테스트 서버 (t3.small)

- 목적: EKS로 옮기기 전에 서비스 전체를 한 서버에서 Docker Compose로 띄워 연동을 확인한다.
- 인스턴스: t3.small(2 vCPU, 2GiB). 운영용이 아니다.
- 구성: `compose.yaml` 위에 `compose.ec2.yaml`(리소스 제한·로그 로테이션)을 겹쳐 쓴다.

## 1. 리소스 제한 (`compose.ec2.yaml`)

| 컨테이너 | 메모리 한도 | CPU 한도 | 실측 최대(부하 테스트) |
|---|---|---|---|
| identity-service | 320MiB | 1.0 | 271MiB |
| routine-service | 320MiB | 1.0 | 286MiB |
| analytics-service | 288MiB | 1.0 | 242MiB |
| workout-service | 256MiB | 1.0 | 227MiB |
| report-service | 256MiB | 1.0 | 234MiB |
| postgres | 192MiB | 0.5 | 약 60MiB |
| gateway (nginx) | 32MiB | 0.25 | 약 13MiB |
| **합계** | **1664MiB** | | |

- 실측: 로컬 Docker에서 같은 한도로 띄우고 게이트웨이에 동시 32개로 요청 9,000건을 보낸 뒤 cgroup `memory.peak`를 읽었다. OOM·재시작은 없었다.
- JVM 옵션(`JAVA_TOOL_OPTIONS`): 힙 = 한도의 33%, SerialGC, C1만 사용, 메타스페이스 상한 112MiB, OOM 시 즉시 종료(재시작 정책으로 복구). 힙 외 고정분(메타스페이스·심볼·클래스·코드 캐시)이 서비스당 약 175MiB라 힙을 크게 줄 수 없다.
- DB 연결: 서비스당 최대 4개(합계 20개), PostgreSQL `max_connections=30`. Tomcat 스레드 최대 32개.
- 로그: 컨테이너마다 10MB x 3개까지만 남긴다.
- 실제 사용자 부하(사진 업로드, 코칭 WebSocket, 긴 기록 조회)는 측정하지 않았다. 서버에서 `docker stats`로 확인하고, 재시작이 보이면 해당 서비스 한도를 올린다.

EKS로 옮길 때: `mem_limit` → `resources.limits.memory`, `cpus` → `resources.limits.cpu`. `requests`는 실측 최대치 근처로 둔다. 힙을 비율(`MaxRAMPercentage`)로 잡았으므로 한도만 바꾸면 힙도 따라 바뀐다.

## 2. 서버 준비

### 2.1 스왑 (필수)
메모리 한도 합계(1664MiB)와 OS·Docker를 더하면 2GiB에 거의 꽉 찬다. 순간 초과로 커널 OOM이 나지 않도록 스왑을 만든다.

```bash
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

### 2.2 이미지는 서버에서 빌드하지 않는다
`docker compose up --build`는 Gradle로 5개 서비스를 빌드해 t3.small에서는 메모리가 모자란다. 로컬·CI에서 빌드해 ECR에 올리고 서버는 받아서 실행만 한다(EKS에서도 같은 이미지를 쓴다).

### 2.3 보안 그룹
- 인바운드: `22`(내 IP만), 게이트웨이 `PORT`(기본 3002, 팀원 IP만 권장).
- 3004~3008, 5432는 열지 않는다(Compose에서 `127.0.0.1`에만 바인딩).
- 퍼블릭 IP가 바뀌지 않도록 Elastic IP를 붙인다.
- HTTPS가 없으므로 토큰이 평문으로 오간다. 테스트 기간에만 쓰고, 공개 전 도메인과 TLS를 붙인다.

### 2.4 비밀값
- `.env`, `keys/`는 서버에서 만들고 `chmod 600`. 저장소에 커밋하지 않는다.
- JWT 키는 서버에서 새로 만든다(`./scripts/generate-jwt-keys.sh`). 로컬 키를 복사하지 않는다.
- `ANALYTICS_INTERNAL_TOKEN=$(openssl rand -hex 32)`. 비어 있으면 운동 기록이 Analytics로 가지 않는다.
- `DB_PASSWORD`는 기본값(`postgres`)을 쓰지 않는다.

## 3. 프로필 사진 S3

```dotenv
PROFILE_IMAGE_STORAGE=s3
PROFILE_IMAGE_S3_BUCKET=<버킷 이름>
PROFILE_IMAGE_S3_REGION=ap-northeast-2
PROFILE_IMAGE_PUBLIC_BASE_URL=https://<버킷 이름>.s3.ap-northeast-2.amazonaws.com
```

- 자격 증명: EC2에 IAM 역할(인스턴스 프로파일)을 붙인다. 액세스 키를 `.env`에 넣지 않는다.
  - 권한: `s3:PutObject`, `s3:DeleteObject` on `arn:aws:s3:::<버킷 이름>/profile-images/*`
  - **IMDSv2 hop limit을 2 이상으로 둔다.** 컨테이너는 Docker 브리지를 한 번 더 거치므로 1이면 자격 증명을 받지 못한다.
    `aws ec2 modify-instance-metadata-options --instance-id <id> --http-tokens required --http-put-response-hop-limit 2`
- 읽기 공개: 버킷 정책으로 `profile-images/*`의 `s3:GetObject`만 공개한다. 나머지 경로와 쓰기는 공개하지 않는다. 운영(EKS)에서는 CloudFront(OAC)로 바꾸고 `PROFILE_IMAGE_PUBLIC_BASE_URL`만 CloudFront 주소로 바꾼다.

## 4. 실행

```bash
docker compose -f compose.yaml -f compose.ec2.yaml up -d
docker compose -f compose.yaml -f compose.ec2.yaml ps
docker stats --no-stream
curl -s http://127.0.0.1:3004/actuator/health   # 서비스별 직접 포트(3004~3008)
```
