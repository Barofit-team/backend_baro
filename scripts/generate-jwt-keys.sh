#!/usr/bin/env bash
# 로컬 개발용 바로핏 JWT 서명 키를 만든다. 결과는 gitignore된 keys/ 폴더에 저장된다.
#   keys/jwt-private.pem : 개인키 (PKCS#8 PEM)
#   keys/jwt-jwks.json   : 공개키 목록 (JWK Set)
# 운영 키는 이 스크립트로 만들지 않는다(k8s Secret, 시크릿 매니저 사용).
set -euo pipefail

cd "$(dirname "$0")/.."
KEY_DIR=keys
KID="${1:-local-$(date +%Y%m%d%H%M%S)}"

if [[ -e "$KEY_DIR/jwt-private.pem" ]]; then
  echo "이미 $KEY_DIR/jwt-private.pem 이 있습니다. 다시 만들려면 $KEY_DIR/ 를 지우고 실행하세요." >&2
  exit 1
fi

mkdir -p "$KEY_DIR"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$KEY_DIR/jwt-private.pem" 2>/dev/null
chmod 600 "$KEY_DIR/jwt-private.pem"

base64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
MODULUS_HEX=$(openssl pkey -in "$KEY_DIR/jwt-private.pem" -pubout | openssl rsa -pubin -noout -modulus | cut -d= -f2)
N=$(printf '%s' "$MODULUS_HEX" | xxd -r -p | base64url)
E=AQAB # 65537

cat > "$KEY_DIR/jwt-jwks.json" <<JSON
{"keys":[{"kty":"RSA","use":"sig","alg":"RS256","kid":"$KID","n":"$N","e":"$E"}]}
JSON

echo "키를 만들었습니다. .env에 다음 값을 설정하세요:"
echo "JWT_PRIVATE_KEY_PATH=./$KEY_DIR/jwt-private.pem"
echo "JWT_PUBLIC_JWKS_PATH=./$KEY_DIR/jwt-jwks.json"
echo "JWT_CURRENT_KID=$KID"
