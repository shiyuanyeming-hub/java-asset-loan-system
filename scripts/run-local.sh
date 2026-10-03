#!/usr/bin/env bash
#
# H2（PostgreSQL 互換モード）でアプリを起動する。Docker 不要。
# 動作確認だけしたいときに使う。
#
set -euo pipefail

export SPRING_PROFILES_ACTIVE=test
echo "起動中: http://localhost:8080 （Ctrl+C で停止）"
echo "操作者ヘッダの例: -H 'X-Employee-Number: E9001'"
exec ./mvnw spring-boot:run "$@"
