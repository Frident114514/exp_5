#!/usr/bin/env bash
# 临时数据库、随机端口和随机凭据只用于本次验证。
set -euo pipefail

module=$(cd -- "$(dirname -- "$0")/.." && pwd)
mysql_image=${M04_MYSQL_IMAGE:-mysql:8.4}
export MYSQL_ROOT_PASSWORD=$(openssl rand -hex 24)
export MYSQL_PASSWORD=$(openssl rand -hex 24)
container=$(docker run --detach --rm \
  --publish 127.0.0.1::3306 \
  --env MYSQL_ROOT_PASSWORD --env MYSQL_DATABASE=m04_schema_test \
  --env MYSQL_USER=m04_test --env MYSQL_PASSWORD \
  "$mysql_image" --sql-mode=STRICT_TRANS_TABLES,NO_ENGINE_SUBSTITUTION)
trap 'docker rm -f "$container" >/dev/null 2>&1 || true' EXIT

for ((attempt = 0; attempt < 90; attempt++)); do
  if docker exec "$container" sh -c '
    MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=tcp -h 127.0.0.1 -u "$MYSQL_USER" "$MYSQL_DATABASE" -Nse "SELECT VERSION()"
  ' 2>/dev/null; then
    break
  fi
  sleep 1
done
if ((attempt == 90)); then
  echo "临时 MySQL 启动超时。" >&2
  exit 1
fi
port=$(docker port "$container" 3306/tcp)
export M04_TEST_DB_URL="jdbc:mysql://${port}/m04_schema_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true&sslMode=DISABLED"
export M04_TEST_DB_USER=m04_test
export M04_TEST_DB_PASSWORD=$MYSQL_PASSWORD
unset MYSQL_ROOT_PASSWORD MYSQL_PASSWORD
mvn -version
mvn -B -ntp -f "$module/pom.xml" verify "$@"
