#!/usr/bin/env bash

set -euo pipefail

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
env_file="${SONAR_ENV_FILE:-${project_dir}/.env}"

if [[ -f "${env_file}" ]]; then
	source "${env_file}"
elif [[ -z "${SONAR_TOKEN:-}" && -z "${SONA_TOKEN:-}" ]]; then
	printf 'Defina SONAR_TOKEN no ambiente ou crie %s a partir de .env.example.\n' "${env_file}" >&2
	exit 1
fi

SONAR_TOKEN="${SONAR_TOKEN:-${SONA_TOKEN:-}}"
unset SONA_TOKEN SONAR_PASS

if [[ -z "${SONAR_TOKEN}" ]]; then
	printf 'Defina SONAR_TOKEN no ambiente ou no arquivo %s.\n' "${env_file}" >&2
	exit 1
fi

export SONAR_TOKEN
export SONAR_HOST_URL="${SONAR_HOST_URL:-http://sonarqube:9000}"
export LOCAL_UID="$(id -u)"
export LOCAL_GID="$(id -g)"

mkdir -p "${project_dir}/.m2-cache"

cd "${project_dir}"
exec docker compose --profile analysis run --rm --env SONAR_TOKEN sonar-scanner
