#!/usr/bin/env bash
# Builds the three images and pushes them to the project's ECR repositories with the given tag.
#
#   deploy/aws/push-images.sh <tag>                     build and push, e.g. a git commit: $(git rev-parse --short HEAD)
#   SKIP_BUILD=true deploy/aws/push-images.sh <tag>     push images already built as leap-<name>:<tag> (Jenkins)
set -euo pipefail
# shellcheck source=deploy/aws/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

TAG="${1:?Usage: push-images.sh <tag>}"

if [ "${SKIP_BUILD:-false}" != "true" ]; then
    log "Building images tagged $TAG"
    docker build -t "leap-backend:$TAG" "$REPO_ROOT/backend"
    docker build -t "leap-frontend:$TAG" "$REPO_ROOT/frontend"
    docker build -t "leap-market-data:$TAG" -f "$REPO_ROOT/scripts/Dockerfile" "$REPO_ROOT"
fi

account="$(aws sts get-caller-identity --query Account --output text)"
registry="$account.dkr.ecr.$AWS_REGION.amazonaws.com"
log "Signing in to $registry"
aws ecr get-login-password | docker login --username AWS --password-stdin "$registry"

for name in backend frontend market-data; do
    remote="$registry/$PROJECT_NAME/$name:$TAG"
    log "Pushing $remote"
    docker tag "leap-$name:$TAG" "$remote"
    docker push "$remote"
done
log "Pushed images tagged $TAG"
