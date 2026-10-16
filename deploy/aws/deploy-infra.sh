#!/usr/bin/env bash
# Creates or updates the shared infrastructure (infra.yml): network, load balancer, database, secrets, image
# repositories and cluster. Run once before the first deployment, then only when infra.yml changes.
# Creating it takes about 10-15 minutes, mostly the database.
#
#   deploy/aws/deploy-infra.sh
#   CERTIFICATE_ARN=arn:aws:acm:... deploy/aws/deploy-infra.sh     # serve HTTPS with an ACM certificate
#   DATABASE_INSTANCE_CLASS=db.t4g.small DATABASE_MULTI_AZ=true deploy/aws/deploy-infra.sh
set -euo pipefail
# shellcheck source=deploy/aws/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

overrides=("ProjectName=$PROJECT_NAME")
[ -n "${CERTIFICATE_ARN:-}" ] && overrides+=("CertificateArn=$CERTIFICATE_ARN")
[ -n "${DATABASE_INSTANCE_CLASS:-}" ] && overrides+=("DatabaseInstanceClass=$DATABASE_INSTANCE_CLASS")
[ -n "${DATABASE_MULTI_AZ:-}" ] && overrides+=("DatabaseMultiAz=$DATABASE_MULTI_AZ")

log "Deploying stack $INFRA_STACK to $AWS_REGION"
aws cloudformation deploy \
    --stack-name "$INFRA_STACK" \
    --template-file "$DEPLOY_DIR/infra.yml" \
    --capabilities CAPABILITY_IAM \
    --parameter-overrides "${overrides[@]}" \
    --no-fail-on-empty-changeset

log "Infrastructure ready. The app will be served at: $(infra_output AppUrl)"
log "Next: deploy/aws/push-images.sh <tag> and deploy/aws/deploy.sh <tag> (or run the Jenkins job with DEPLOY ticked)"
