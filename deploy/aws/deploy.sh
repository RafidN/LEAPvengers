#!/usr/bin/env bash
# Deploys a release: points the services stack (services.yml) at the images with the given tag. ECS replaces
# the containers one by one behind the load balancer and rolls back by itself if the new ones fail their
# health checks. Finishes once the release is healthy, then checks the API answers.
#
#   deploy/aws/deploy.sh <tag>                 the tag pushed by push-images.sh
#   BACKEND_COUNT=2 deploy/aws/deploy.sh <tag> run two backend tasks (after the first deployment)
set -euo pipefail
# shellcheck source=deploy/aws/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

TAG="${1:?Usage: deploy.sh <tag>}"

overrides=("ProjectName=$PROJECT_NAME" "ImageTag=$TAG")
[ -n "${BACKEND_COUNT:-}" ] && overrides+=("BackendCount=$BACKEND_COUNT")
[ -n "${FRONTEND_COUNT:-}" ] && overrides+=("FrontendCount=$FRONTEND_COUNT")
[ -n "${QUOTE_SOURCE:-}" ] && overrides+=("QuoteSource=$QUOTE_SOURCE")
[ -n "${ENFORCE_MARKET_HOURS:-}" ] && overrides+=("EnforceMarketHours=$ENFORCE_MARKET_HOURS")

log "Deploying $TAG to stack $SERVICES_STACK in $AWS_REGION (the first deployment also sets up the database)"
aws cloudformation deploy \
    --stack-name "$SERVICES_STACK" \
    --template-file "$DEPLOY_DIR/services.yml" \
    --parameter-overrides "${overrides[@]}" \
    --no-fail-on-empty-changeset

url="$(infra_output AppUrl)"
log "Checking $url/api/health"
for attempt in $(seq 1 30); do
    if curl -fsS --max-time 5 "$url/api/health" > /dev/null; then
        log "Release $TAG is live at $url"
        exit 0
    fi
    log "Not answering yet (attempt $attempt of 30)"
    sleep 10
done
echo "The stack deployed, but $url/api/health did not answer; see the logs in CloudWatch (/ecs/$PROJECT_NAME)" >&2
exit 1
