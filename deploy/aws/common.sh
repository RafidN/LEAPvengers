#!/usr/bin/env bash
# Shared settings for the deploy scripts. Override with environment variables:
#   AWS_REGION    region to deploy to (default: the AWS CLI's configured region)
#   PROJECT_NAME  stack and repository prefix (default: leapvengers)

# The names below are used by the scripts that source this file
# shellcheck disable=SC2034
PROJECT_NAME="${PROJECT_NAME:-leapvengers}"
AWS_REGION="${AWS_REGION:-$(aws configure get region 2>/dev/null || true)}"
if [ -z "$AWS_REGION" ]; then
    echo "Set AWS_REGION (e.g. export AWS_REGION=eu-west-2) or configure a default region with 'aws configure'" >&2
    exit 1
fi
export AWS_REGION AWS_DEFAULT_REGION="$AWS_REGION"

INFRA_STACK="$PROJECT_NAME-infra"
SERVICES_STACK="$PROJECT_NAME-services"
DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$DEPLOY_DIR/../.." && pwd)"

# One output of the infrastructure stack, e.g. infra_output AppUrl
infra_output() {
    aws cloudformation describe-stacks --stack-name "$INFRA_STACK" \
        --query "Stacks[0].Outputs[?OutputKey=='$1'].OutputValue" --output text
}

log() {
    echo "[$(date -u '+%H:%M:%S')] $*"
}
