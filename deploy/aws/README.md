# Deploying LEAPvengers to AWS

The platform runs as three containers on **ECS Fargate** behind an **Application Load Balancer**, with **PostgreSQL on RDS**.
Two CloudFormation stacks describe all of it, and a release is one command or one Jenkins build.

```
                    internet
                       │
          ┌────────────▼────────────┐
          │  Application Load       │   /api/*  ──► backend  (Spring Boot, port 8081)
          │  Balancer (HTTP/HTTPS)  │   other   ──► frontend (nginx + the built app, port 8080)
          └────────────┬────────────┘
                       │                       feed (live Yahoo Finance prices, always 1 task)
   public subnets ─────┼───────────────────────────────────┐
                       │                                   │
   private subnets ────▼───────────────────────────────────▼──
                 PostgreSQL 17 on RDS (encrypted, no internet access)
```

| File | What it is |
| --- | --- |
| `infra.yml` | Shared infrastructure, deployed once: network, load balancer, database, secrets, image repositories, ECS cluster, logs |
| `services.yml` | The containers, redeployed on every release with a new image tag |
| `deploy-infra.sh` | Creates or updates the infrastructure stack |
| `push-images.sh` | Builds the three images and pushes them to ECR |
| `deploy.sh` | Rolls out a release and checks it answers |
| `run-db-job.sh` | One-off database jobs: reload price history, or reset to demo data |

The same images run locally with `docker compose up --build` (see `SETUP_GUIDE.md`), so what you test is what you deploy.

## What you need

- An AWS account, and the **AWS CLI v2** signed in with rights to create the resources above (an administrator, for the first set-up)
- **Docker**, to build the images (or let Jenkins do it)
- A region, e.g. `export AWS_REGION=eu-west-2`

## First deployment (about 20 minutes, most of it waiting for the database)

```bash
export AWS_REGION=eu-west-2

# 1. Infrastructure. Optional: CERTIFICATE_ARN=<ACM certificate ARN> for HTTPS (see "HTTPS" below)
deploy/aws/deploy-infra.sh

# 2. Build and push the images, tagged with the current commit
TAG=$(git rev-parse --short=12 HEAD)
deploy/aws/push-images.sh "$TAG"

# 3. Start the containers. The first backend start sets up the empty database (schema, demo data and a year of
#    prices, a minute or two), then the API starts. Prints the app's address when it's live.
deploy/aws/deploy.sh "$TAG"
```

Then open the printed address and sign in as `joanna`, `david` or `priya` (password `Test123`).

**Before real clients use it:** the demo accounts all share the password `Test123`. Remove them or change their passwords, or reset the database without demo data.

## Releasing

Run the Jenkins job with **DEPLOY** ticked. It tests, builds and pushes the images, then runs `deploy.sh`. By hand:

```bash
TAG=$(git rev-parse --short=12 HEAD)
deploy/aws/push-images.sh "$TAG" && deploy/aws/deploy.sh "$TAG"
```

ECS starts the new containers next to the old ones, moves traffic once they pass their health checks, and rolls
back by itself if they don't. Clients aren't interrupted, and orders in flight finish (the backend is given 30
seconds to drain). The live price feed is replaced stop-then-start, so prices pause for a few seconds.

To run an earlier release, deploy its tag again: `deploy/aws/deploy.sh <older tag>`.

### Database changes

The backend checks the database matches its code at start-up (`ddl-auto=validate`) and refuses to start if not, so
a release that changes `database/enterprise-schema.sql` needs the matching change applied to the live database
first. The start-up job only sets up an **empty** database; it never alters an existing one. Options:

- **Demo environment:** `deploy/aws/run-db-job.sh reset` rebuilds the database from the new schema and demo data. Every
  client, order and trade record is deleted, and it asks you to type the project name first.
- **Real data:** write the change as SQL (e.g. `ALTER TABLE ... ADD COLUMN ...`) and run it against the database before
  deploying. A migration tool such as Flyway is the next step before running this with real clients.

## Day to day

| Task | How |
| --- | --- |
| Logs | CloudWatch Logs, group `/ecs/leapvengers`, streams `backend/`, `frontend/`, `feed/`, `db-init/`, `db-jobs/` |
| Reload price history (e.g. after Yahoo was unavailable) | `deploy/aws/run-db-job.sh backfill` |
| Reset to demo data (**deletes everything**) | `deploy/aws/run-db-job.sh reset` |
| Two backend tasks, for availability | `BACKEND_COUNT=2 deploy/aws/deploy.sh <tag>` (keep 1 until the first deployment has set up the database) |
| Simulated prices out of hours (demo) | `QUOTE_SOURCE=simulated ENFORCE_MARKET_HOURS=false deploy/aws/deploy.sh <tag>` |
| Database password, JWT secret | Secrets Manager: `leapvengers/database`, `leapvengers/jwt-secret` |
| Backups | RDS takes daily snapshots, kept 7 days; restore from the RDS console |

Changing a secret only reaches the containers when they restart, so redeploy afterwards (`deploy.sh` with the
same tag, or **Force new deployment** in the ECS console). The database password isn't rotated automatically for
this reason. To rotate it, change it in RDS and in the secret together, then redeploy.

## HTTPS

Without a certificate the load balancer serves plain HTTP on its AWS address, which is fine for a demo. For a real domain:

1. Request a certificate for the domain in **AWS Certificate Manager**, in the same region, and validate it.
2. `CERTIFICATE_ARN=arn:aws:acm:... deploy/aws/deploy-infra.sh`. HTTP then redirects to HTTPS.
3. Point the domain at the load balancer (a Route 53 alias record, or a CNAME to the `AppUrl` address).

## Jenkins

The `Jenkinsfile` at the repository root runs on every build:

1. **Checks and tests**, in parallel:
   - ShellCheck on the shell scripts.
   - The Python scripts compile.
   - cfn-lint on both templates.
   - Backend unit and integration tests, with results published to Jenkins.
   - Frontend tests and the production build.
2. **Images**: backend, frontend and market-data, tagged `<commit>-<build number>`.
3. **Push to ECR**, when `PUSH_IMAGES` or `DEPLOY` is ticked.
4. **Deploy**, when `DEPLOY` is ticked. Multibranch jobs only deploy `main`.

**Agent requirements:**
- A Linux agent with **Docker**. Every build tool runs in a container (Maven with Java 21, Node 22, Python 3.12), so Jenkins needs no JDK, Maven or Node set-up. Docker must run on the agent itself, because the workspace is mounted into those containers.
- The **AWS CLI v2** on the agent, for the push and deploy stages.

**AWS access**, one of:
- An IAM role on the agent, if Jenkins runs on EC2.
- AWS credentials stored in Jenkins (needs the CloudBees AWS Credentials plugin), with their ID entered in the job's `AWS_CREDENTIALS_ID` parameter.

The job needs rights to push to the three ECR repositories, update the `leapvengers-services` stack, register task definitions and update the ECS services. It also needs to pass the task execution role.

## Cost

At the default sizes in a region like eu-west-2, roughly **$100–130 a month**:

| Item | Approximate monthly cost |
| --- | --- |
| Backend task (1 vCPU, 2 GB) | ~$36 |
| Frontend and feed tasks | ~$20 |
| Load balancer | ~$20 |
| Public IPv4 addresses | ~$15–20 |
| Database (db.t4g.micro, 20 GB) | ~$15 |
| Logs and secrets | a few dollars |

Free-tier credits reduce this. To stop paying while not in use:
- Scale the services to 0: `BACKEND_COUNT=0 FRONTEND_COUNT=0 deploy/aws/deploy.sh <tag>`. The feed stays at 1 unless you also stop it in the console.
- Stop the database in the RDS console; it restarts itself after 7 days.
- To remove everything, delete the `leapvengers-services` stack, then `leapvengers-infra`.
  - Deletion protection on the database must be turned off first.
  - A final snapshot of the database is kept.
  - The image repositories must be emptied before they can be deleted.

## Things to know

- **Yahoo Finance and cloud addresses.** The free Yahoo feed sometimes limits or blocks requests from cloud providers' IP ranges more readily than home or office connections.
  - If live prices stop on AWS (`feed` logs show repeated reconnects), orders are rejected rather than mispriced.
  - For a demo you can switch to simulated prices.
  - For real trading, use a licensed market data feed.
- **One region, one database.** Containers run in two availability zones. The database is single-zone unless you deploy the infrastructure with `DATABASE_MULTI_AZ=true`, which doubles its cost.
- **Untested here.** The templates pass cfn-lint, and the scripts pass ShellCheck. The images, the nginx config and the start-up jobs were exercised locally (see `SETUP_GUIDE.md`). The stacks themselves still need a first run in your AWS account.
