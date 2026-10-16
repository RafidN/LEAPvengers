#!/bin/sh
# Runs before nginx starts (nginx image /docker-entrypoint.d/). With BACKEND_URL set, e.g. http://backend:8081,
# /api/ requests are passed to the backend; without it nothing is proxied (on AWS the load balancer routes /api/*).
set -eu

mkdir -p /tmp/nginx
rm -f /tmp/nginx/api-proxy.conf

if [ -n "${BACKEND_URL:-}" ]; then
    cat > /tmp/nginx/api-proxy.conf <<EOF
location /api/ {
    proxy_pass ${BACKEND_URL};
    proxy_http_version 1.1;
    proxy_set_header Host \$host;
    proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto \$scheme;
    proxy_read_timeout 60s;
}
EOF
    echo "40-api-proxy.sh: passing /api/ to ${BACKEND_URL}"
else
    echo "40-api-proxy.sh: BACKEND_URL not set, /api/ is not proxied"
fi
