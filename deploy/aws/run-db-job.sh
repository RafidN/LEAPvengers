#!/usr/bin/env bash
# Runs a one-off database job on AWS with the deployed market-data image, and waits for it to finish.
#
#   deploy/aws/run-db-job.sh backfill          reload price history from Yahoo Finance (safe any time)
#   deploy/aws/run-db-job.sh reset             wipe the database and rebuild it with demo data. Deletes every
#                                              client, order and trade record; asks you to type the project name.
set -euo pipefail
# shellcheck source=deploy/aws/common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

job="${1:?Usage: run-db-job.sh backfill|reset}"
case "$job" in
    backfill)
        overrides='{"containerOverrides":[{"name":"db-jobs","command":["backfill"]}]}'
        ;;
    reset)
        echo "This deletes ALL data in the $PROJECT_NAME database (clients, orders, trade records) and reloads demo data."
        read -r -p "Type the project name ($PROJECT_NAME) to continue: " answer
        if [ "$answer" != "$PROJECT_NAME" ]; then
            echo "Cancelled" >&2
            exit 1
        fi
        overrides='{"containerOverrides":[{"name":"db-jobs","command":["init-db"],"environment":[{"name":"RESET_DATABASE","value":"true"}]}]}'
        ;;
    *)
        echo "Unknown job: $job (use backfill or reset)" >&2
        exit 1
        ;;
esac

cluster="$(infra_output ClusterName)"
subnets="$(infra_output PublicSubnets)"
security_group="$(infra_output AppSecurityGroup)"

log "Starting the $job job"
task_arn="$(aws ecs run-task \
    --cluster "$cluster" \
    --task-definition "$PROJECT_NAME-db-jobs" \
    --launch-type FARGATE \
    --network-configuration "awsvpcConfiguration={subnets=[$subnets],securityGroups=[$security_group],assignPublicIp=ENABLED}" \
    --overrides "$overrides" \
    --query 'tasks[0].taskArn' --output text)"

log "Waiting for $task_arn (logs: CloudWatch /ecs/$PROJECT_NAME, stream prefix db-jobs)"
aws ecs wait tasks-stopped --cluster "$cluster" --tasks "$task_arn"
exit_code="$(aws ecs describe-tasks --cluster "$cluster" --tasks "$task_arn" \
    --query 'tasks[0].containers[0].exitCode' --output text)"
if [ "$exit_code" = "0" ]; then
    log "The $job job finished"
else
    echo "The $job job failed (exit code $exit_code); see the db-jobs logs in CloudWatch" >&2
    exit 1
fi
