# Frontend (Angular, served by nginx). Build context: frontend/
#   docker build -t leap-frontend frontend
# Listens on 8080 as a non-root user. Set BACKEND_URL (e.g. http://backend:8081) to have nginx pass /api/ to the
# backend; leave it unset where a load balancer routes /api/* itself (AWS).

FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

FROM nginxinc/nginx-unprivileged:1.27-alpine
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --chmod=755 nginx/40-api-proxy.sh /docker-entrypoint.d/40-api-proxy.sh
COPY --from=build /src/dist/frontend/browser /usr/share/nginx/html
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD wget -qO- http://localhost:8080/healthz > /dev/null || exit 1
