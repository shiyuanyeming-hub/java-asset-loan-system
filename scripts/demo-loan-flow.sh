#!/usr/bin/env bash
#
# README に載せている「申請 → 承認 → 返却」のデモを実際に実行し、
# リクエストとレスポンスを docs/demo-output/ に記録する。
#
# 前提: アプリが起動していること（./scripts/run-local.sh など）+ 初期データ投入済み
#
# 使い方:
#   ./scripts/demo-loan-flow.sh
#
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
REQUESTER="E1001"
ADMIN="E9001"
OUT_DIR="${OUT_DIR:-docs/demo-output}"

mkdir -p "$OUT_DIR"
LOG="${OUT_DIR}/loan-flow.txt"
: > "$LOG"

# 表示用のユーティリティ
pretty() {
  if command -v jq >/dev/null 2>&1; then
    jq .
  else
    python3 -m json.tool 2>/dev/null || cat
  fi
}

call() {
  local title="$1"; shift
  local method="$1"; shift
  local path="$1"; shift
  local actor="$1"; shift

  {
    echo "### ${title}"
    echo "\$ curl -i -X ${method} ${BASE_URL}${path} -H 'X-Employee-Number: ${actor}' $*"
  } >> "$LOG"

  local response
  response=$(curl -sS -X "${method}" "${BASE_URL}${path}" \
      -H "X-Employee-Number: ${actor}" \
      -H "Content-Type: application/json" "$@" -w $'\n---HTTP_STATUS:%{http_code}')

  local status="${response##*---HTTP_STATUS:}"
  local body="${response%---HTTP_STATUS:*}"

  {
    echo "HTTP ${status}"
    echo "$body" | pretty
    echo
  } >> "$LOG"

  echo "[$status] ${title}"
  echo "$body"
}

echo "== デモ: 備品の貸出申請から返却まで ==" 

echo
echo "-- 1. 備品一覧（PC で検索） --"
call "備品一覧の検索" GET "/api/assets?keyword=PC&size=5" "$REQUESTER"

echo
echo "-- 2. 貸出申請 --"
ASSET_ID=$(curl -sS "${BASE_URL}/api/assets?keyword=PC-0001" | python3 -c 'import json,sys; print(json.load(sys.stdin)["items"][0]["id"])')
LOAN_JSON=$(curl -sS -X POST "${BASE_URL}/api/loans" -H "X-Employee-Number: ${REQUESTER}" \
  -H "Content-Type: application/json" -d "{\"assetId\": ${ASSET_ID}}")
LOAN_ID=$(echo "$LOAN_JSON" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
{
  echo "### 貸出申請"
  echo "\$ curl -i -X POST ${BASE_URL}/api/loans -H 'X-Employee-Number: ${REQUESTER}' -d '{\"assetId\": ${ASSET_ID}}'"
  echo "HTTP 201"
  echo "$LOAN_JSON" | pretty
  echo
} >> "$LOG"
echo "loanId=${LOAN_ID}"

echo
echo "-- 3. 承認待ち一覧（管理者） --"
call "承認待ち一覧" GET "/api/loans/pending" "$ADMIN"

echo
echo "-- 4. 承認（管理者） --"
call "承認" POST "/api/loans/${LOAN_ID}/approve" "$ADMIN"

echo
echo "-- 5. 備品の状態を確認（貸出中になっている） --"
call "備品詳細" GET "/api/assets/${ASSET_ID}" "$REQUESTER"

echo
echo "-- 6. 同じ備品に再申請（409 になる想定） --"
call "重複申請の拒否" POST "/api/loans" "$REQUESTER" -d "{\"assetId\": ${ASSET_ID}}"

echo
echo "-- 7. 返却（管理者） --"
call "返却" POST "/api/loans/${LOAN_ID}/return" "$ADMIN" -d '{"note":"キズなし"}'

echo
echo "-- 8. 履歴（申請 → 承認 → 返却） --"
call "操作履歴" GET "/api/loans/${LOAN_ID}/history" "$REQUESTER"

echo
echo "-- 9. 備品が貸出可能に戻っている --"
call "備品詳細（返却後）" GET "/api/assets/${ASSET_ID}" "$REQUESTER"

echo
echo "記録しました: ${LOG}"
