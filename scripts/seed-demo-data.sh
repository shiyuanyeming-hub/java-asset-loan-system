#!/usr/bin/env bash
#
# デモ用の初期データを投入する。
# アプリを起動した状態（既定: http://localhost:8080）で実行する。
#
# 使い方:
#   ./scripts/seed-demo-data.sh
#   BASE_URL=http://localhost:8080 ./scripts/seed-demo-data.sh
#
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN="E9001"

post_json() {
  local path="$1"
  local body="$2"
  local status
  status=$(curl -sS -X POST "${BASE_URL}${path}" \
    -H "Content-Type: application/json" \
    -H "X-Employee-Number: ${ADMIN}" \
    -d "${body}" -o /tmp/seed-response.json -w "%{http_code}" 2>/dev/null || echo "000")
  if [ "${status}" != "201" ] && [ "${status}" != "409" ]; then
    echo "  ! ${status} ${path} $(head -c 200 /tmp/seed-response.json)"
  fi
  echo "  ${status} ${path}"
}

echo "== 部署を登録 =="
for dept in '{"code":"SALES","name":"営業部"}' '{"code":"DEV","name":"開発部"}' '{"code":"HR","name":"人事部"}'; do
  post_json /api/departments "$dept"
done

echo "== カテゴリを登録 =="
for category in '{"name":"ノートPC","defaultLoanDays":14}' '{"name":"モニター","defaultLoanDays":30}' \
                '{"name":"タブレット","defaultLoanDays":14}' '{"name":"プロジェクター","defaultLoanDays":7}' \
                '{"name":"周辺機器","defaultLoanDays":30}'; do
  post_json /api/categories "$category"
done

echo "== 社員を登録 =="
# 部署 ID: 1=情報システム部（V2 で投入）、以降 2=営業部, 3=開発部, 4=人事部 の順に採番される
post_json /api/employees '{"employeeNumber":"E1001","name":"佐藤 花子","email":"hanako.sato@example.com","departmentId":2,"role":"EMPLOYEE"}'
post_json /api/employees '{"employeeNumber":"E1002","name":"鈴木 一郎","email":"ichiro.suzuki@example.com","departmentId":3,"role":"EMPLOYEE"}'
# E9001（管理者 太郎）は Flyway の V2__seed_admin.sql で投入済み

echo "== 備品を登録 =="
post_json /api/assets '{"managementNumber":"PC-0001","name":"ThinkPad X1 Carbon","categoryId":1,"manufacturer":"Lenovo","model":"21HM","purchasedOn":"2024-04-01","note":"営業部 共有機"}'
post_json /api/assets '{"managementNumber":"PC-0002","name":"MacBook Pro 14","categoryId":1,"manufacturer":"Apple","model":"M4 Pro","purchasedOn":"2025-06-15"}'
post_json /api/assets '{"managementNumber":"MON-0001","name":"27インチ 4K モニター","categoryId":2,"manufacturer":"Dell","model":"U2723QE","purchasedOn":"2024-09-10"}'
post_json /api/assets '{"managementNumber":"MON-0002","name":"24インチ モニター","categoryId":2,"manufacturer":"BenQ","model":"GW2480","purchasedOn":"2022-03-05","note":"予備機"}'
post_json /api/assets '{"managementNumber":"TAB-0001","name":"iPad Pro 11","categoryId":3,"manufacturer":"Apple","model":"M2","purchasedOn":"2024-11-20"}'
post_json /api/assets '{"managementNumber":"PRJ-0001","name":"モバイルプロジェクター","categoryId":4,"manufacturer":"Epson","model":"EB-W06","purchasedOn":"2023-07-01"}'
post_json /api/assets '{"managementNumber":"ACC-0001","name":"USB-C ドッキングステーション","categoryId":5,"manufacturer":"Anker","model":"A8392","purchasedOn":"2025-01-08"}'

echo
echo "登録結果:"
curl -sS "${BASE_URL}/api/assets/stats"
echo
curl -sS "${BASE_URL}/api/assets?size=20" | head -c 400
echo
