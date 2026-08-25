#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
cd "$repo_root"

for command in bash curl unzip; do
    command -v "$command" >/dev/null || {
        printf 'Missing required command: %s\n' "$command" >&2
        exit 1
    }
done

# Gradle's Foojay resolver downloads JDK 25 when JAVA_HOME is not configured.
if command -v docker >/dev/null && docker info >/dev/null 2>&1; then
    ./gradlew clean :test
else
    printf 'Docker daemon is unavailable; skipping Testcontainers integration tests.\n' >&2
    ./gradlew clean :test -PskipContainerTests
fi
