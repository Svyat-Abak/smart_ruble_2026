#!/usr/bin/env sh
set -eu
project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
for service in auth-service api-gateway billing-service abs-service notification-service; do
  echo "Testing $service"
  (cd "$project_root/$service" && ./mvnw -B test)
done
(cd "$project_root/web-client" && npm ci --no-audit --no-fund && npm test && npm run build)
