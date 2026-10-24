#!/bin/sh
# Inject BACKEND_URL into the nginx config at runtime.
# Fallback keeps local Docker Compose working without setting any env var.
BACKEND_URL="${BACKEND_URL:-http://host.docker.internal:8080}"

envsubst '${BACKEND_URL}' \
  < /etc/nginx/conf.d/default.conf.template \
  > /etc/nginx/conf.d/default.conf

exec nginx -g "daemon off;"
