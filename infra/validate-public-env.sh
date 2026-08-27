#!/bin/sh
set -eu

ENV_FILE="${1:-infra/.env}"

if [ ! -f "$ENV_FILE" ]; then
  echo "public deployment blocked: env file not found: $ENV_FILE" >&2
  exit 1
fi

value_of() {
  key="$1"
  sed -n "s/^${key}=//p" "$ENV_FILE" | tail -n 1 | tr -d '\r'
}

fail() {
  echo "public deployment blocked: $1" >&2
  exit 1
}

require_value() {
  key="$1"
  value="$(value_of "$key")"
  [ -n "$value" ] || fail "$key must be set"
}

for key in \
  PUBLIC_DOMAIN CADDY_ACME_EMAIL POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD \
  DEMO_OPERATOR_USERNAME DEMO_OPERATOR_PASSWORD SESSION_COOKIE_SECURE \
  DIAGNOSTIC_MODEL_PROVIDER DEEPSEEK_API_KEY
do
  require_value "$key"
done

domain="$(value_of PUBLIC_DOMAIN)"
case "$domain" in
  localhost|127.0.0.1|*.example.com|example.com|demo.example.com)
    fail "PUBLIC_DOMAIN must be the real public hostname"
    ;;
esac

email="$(value_of CADDY_ACME_EMAIL)"
case "$email" in
  *@example.com|ops@example.com)
    fail "CADDY_ACME_EMAIL must be a real operational email"
    ;;
esac

db_password="$(value_of POSTGRES_PASSWORD)"
case "$db_password" in
  replace-*|local-demo-*|ota|password|demo-password)
    fail "POSTGRES_PASSWORD is still a placeholder or local-only value"
    ;;
esac
[ "${#db_password}" -ge 16 ] || fail "POSTGRES_PASSWORD must be at least 16 characters"

demo_password="$(value_of DEMO_OPERATOR_PASSWORD)"
case "$demo_password" in
  replace-*|local-demo-*|demo-password|password)
    fail "DEMO_OPERATOR_PASSWORD is still a placeholder or local-only value"
    ;;
esac
[ "${#demo_password}" -ge 16 ] || fail "DEMO_OPERATOR_PASSWORD must be at least 16 characters"

[ "$(value_of SESSION_COOKIE_SECURE)" = "true" ] \
  || fail "SESSION_COOKIE_SECURE must be true for public HTTPS"

[ "$(value_of DIAGNOSTIC_MODEL_PROVIDER)" = "deepseek" ] \
  || fail "DIAGNOSTIC_MODEL_PROVIDER must be deepseek for the logged-in live public demo"

api_key="$(value_of DEEPSEEK_API_KEY)"
case "$api_key" in
  replace-*|test-*|ci-*|placeholder*)
    fail "DEEPSEEK_API_KEY is still a placeholder"
    ;;
esac
[ "${#api_key}" -ge 16 ] || fail "DEEPSEEK_API_KEY does not look configured"

echo "public deployment environment checks passed (secret values not printed)"
