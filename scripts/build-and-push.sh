#!/usr/bin/env bash
# 5개 서비스 이미지를 빌드해서 Docker Hub(public)에 푸시한다.
# docker login은 미리 1회 수동으로 해둔다(자격증명을 이 스크립트에 넣지 않음).
#
# 사용법:
#   DOCKERHUB_USER=<hub-user> ./scripts/build-and-push.sh
#   DOCKERHUB_USER=<hub-user> TAG=my-tag ./scripts/build-and-push.sh
set -euo pipefail
cd "$(dirname "$0")/.."

DOCKERHUB_USER="${DOCKERHUB_USER:?DOCKERHUB_USER 환경변수가 필요합니다 (예: DOCKERHUB_USER=yourhubuser ./scripts/build-and-push.sh)}"
TAG="${TAG:-$(git rev-parse --short HEAD)}"
SERVICES=(identity routine workout analytics report)

for svc in "${SERVICES[@]}"; do
  image="docker.io/${DOCKERHUB_USER}/barofit-${svc}:${TAG}"
  echo "==> building ${image}"
  docker build --build-arg SERVICE_NAME="${svc}" -t "${image}" .
  echo "==> pushing ${image}"
  docker push "${image}"
done

echo ""
echo "TAG=${TAG}"
echo "helm upgrade --install barofit infra/umbrella -n barofit \\"
echo "  --set global.imageTag=${TAG} \\"
for svc in "${SERVICES[@]}"; do
  echo "  --set ${svc}.image.repository=docker.io/${DOCKERHUB_USER}/barofit-${svc} \\"
done
