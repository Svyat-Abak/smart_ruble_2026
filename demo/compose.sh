#!/usr/bin/env sh
set -eu
project_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
exec docker compose --project-directory "$project_root" -p "${SMART_RUBLE_PROJECT:-smartruble-mvp}" -f "$project_root/docker-compose.yml" -f "$project_root/docker-compose.demo.yml" "$@"
