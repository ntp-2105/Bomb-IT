# Bomb-It-inspired multiplayer game: architecture and delivery roadmap

Date: 7 October 2026. Status: proposal; no game repository or running service has been audited. The actionable tasks and module boundaries are in [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md) and [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Executive judgment

This is a strong **DevOps/SRE/AWS portfolio project** if the team ships a small playable game and demonstrates repeatable deployment, measurements, failure handling, and cost control. A long list of AWS services without a measured operational need will weaken the story. The game is the workload; the evidence of operating it is the differentiator.

The source documents describe different MVPs. [`STACK.md`](STACK.md) and [`CONTRACT.md`](CONTRACT.md) define a browser client with Phaser/TypeScript and a Java server; the Vietnamese event plan keeps a Java desktop client. The browser contract is the target for this release. **Do not combine the browser rewrite, accounts, database, and full operations program into a 10–12 week commitment.** No source repository is present here, so any estimate is provisional until source and asset rights are audited.

**Recommended sequence:** define the MVP wire contract and rules, build a minimal Phaser/Vite browser client and server-authoritative Java game as a local vertical slice, then deploy and operate that workload on a single EC2 instance. Use a small DOM lobby initially; add React only when the UI justifies it.

## Scope contract for the first release

- One map, 2–4 players per room, room codes, ready state, one match, result screen.
- Guest identity with expiring resume token. No passwords, leaderboard, ranked matchmaking, chat, payments or account recovery.
- Server receives input commands and owns collision, bomb timing, chain reactions, damage and winner. The client renders snapshots and events.
- Room state lives in one server process. A reconnect works only while that process survives; a restart ends active matches and clients receive a clear message.
- Document rules, tick order and protocol version. Bound rooms, sessions, payload size, input rate and outbound queues.
- Public gameplay over TLS/WSS, one reproducible deployment, one rollback drill, a dashboard, a load report and a runbook.

This is a **single-instance portfolio service**, not a high-availability or production-scale platform.

## Architecture decision

```text
TypeScript + Phaser browser client (MVP)
                   | HTTPS + WSS
                   v
           Caddy on one EC2 instance
                   |
                   v
       Spring Boot REST + raw WebSocket
       guest/room service | authoritative game core
       bounded room queues | in-memory match state
                   |
           optional results store after MVP
```

Serve the initial browser build through Caddy on the EC2 host, on the same origin as the API and WSS endpoint. S3/CloudFront is an optional static-hosting extension. The client sends intent (`move`, `placeBomb`), never position, damage or winner as facts. Keep the simulation core independent of Spring, networking, rendering and persistence; a headless deterministic test should run it.

**EC2 versus ECS:** use one EC2 instance first to practice VPC, security groups, IAM, SSM, patching, instance health, Docker, deployment and recovery. Containerize the Java server so ECS remains possible. ECS/Fargate is a valid later comparison for managed task operations even with one task; multiple game tasks introduce room ownership, reconnect routing and draining work. An ALB supports WebSockets, and ECS task protection can protect sessions during scale-in, but neither solves state transfer or crash recovery. [ALB WebSocket documentation](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-listeners.html), [ECS task protection](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-scale-in-protection.html).

**Data:** store live game state in memory. Start without a database if no result history is required. If match history becomes a real feature, choose either SQLite on a persistent EC2 volume plus tested backup/restore for the single-node design, or PostgreSQL if the team specifically wants database operations and accepts the extra cost and migration work. PostgreSQL and Redis are not prerequisites for realtime play. Do not use Redis as a substitute for designing room ownership; add it only for a measured multi-node need. The TypeScript `shared/` models in `STACK.md` cannot be directly shared with a Java server; use a versioned wire schema and contract tests.

## AWS service selection

| Use now, tied to a deliverable | Add only when justified | Defer |
| --- | --- | --- |
| EC2, VPC, security groups, EBS: host and isolate one server | S3/CloudFront: separate browser static delivery or tested result backups | ECS/Fargate + ALB: after an operational comparison and cost estimate |
| IAM instance role + Systems Manager Session Manager: administer without public SSH; CloudWatch Logs/alarms + SNS: observe and alert off-host | Additional tracing after a measured diagnosis need | ElastiCache/Redis, RDS, API Gateway, EKS, GameLift, multi-region |
| ECR: immutable images; GitHub Actions OIDC: short-lived publish credentials | Route 53 + ACM where the TLS architecture uses them; Caddy can use a domain and its own certificate flow | AWS WAF and other security services until threat model/traffic warrants them |
| AWS Budgets: spending alerts; Terraform: reproducible infrastructure; protected S3 state with locking and versioning | Separate browser asset bucket when delivery needs it | Any service introduced only to increase the AWS count |

For the first AWS release, send selected application and host metrics plus bounded-retention logs to CloudWatch, configure actionable alarms and SNS notification, and run a synthetic gameplay check outside the EC2 host. Micrometer/Prometheus/Grafana remains an alternative if the team can operate it and still detect host loss independently. Avoid duplicate dashboards and high-cardinality metrics. AWS guidance recommends application telemetry and alerting tied to service behavior. [AWS application telemetry guidance](https://docs.aws.amazon.com/wellarchitected/latest/framework/ops_observability_application_telemetry.html).

Terraform should own the VPC, subnet, routes, security groups, EC2, IAM, ECR, monitoring, budget-related resources and optional DNS/storage. Bootstrap protected S3 state separately with encryption, versioning and lockfile support; treat state and saved plans as sensitive. Review `terraform plan`, pin provider versions, and document bootstrap, apply and destroy. Restrict public ingress to HTTPS (and HTTP only if needed for certificate flow); use SSM for administration. Image deploys are separate from Terraform infrastructure changes.

## Delivery plan and gates

The event plan assumes **12–15 hours per week for one person** and a Java desktop client. With browser play in the first release, the detailed plan estimates **180–280 person-hours** and reserves roughly **16–24 calendar weeks** for one part-time contributor, including integration and repair. This is a planning range, not a commitment; re-estimate after the source audit and first browser/server vertical slice. For a team, account for integration and coordination rather than dividing calendar time by headcount.

| Stage | Order | Deliverable and exit gate |
| --- | --- | --- |
| 0. Audit and contract | First | Verify source/assets and license; freeze grid rules, guest API and versioned command/event schemas. |
| 1. Baseline and core | Next | CI builds both languages; headless deterministic Java simulation passes rule and replay tests. |
| 2. Browser/server slice | Next | Phaser browser and Spring REST/WebSocket server let two players finish a match locally; invalid commands cannot change authoritative state. |
| 3. Playability and failure | Next | Four humans can play; reconnect and snapshot resync work; queues/rate limits and restart behavior are tested. |
| 4. Operability | Alongside the slice | Docker, health/readiness, structured logs, gameplay/JVM metrics, off-host synthetic check and actionable alert. |
| 5. AWS IaC + delivery | After local match | Terraform creates a single EC2 environment; ECR image by digest; OIDC publish; scripted SSM deploy, drain, smoke and rollback; budget and teardown instructions. |
| 6. Evidence | Final gate | Cross-network play, bot load/soak, three recovery drills, capacity report, runbooks, architecture diagram, short demo and truthful CV bullets. |
| 7. Extension | Later | React shell, persistence, separate static CDN delivery or ECS only after a stated need. |

The critical path is **source audit → contract/rules → Java core → browser/server slice → AWS release → operational evidence**. CI, telemetry and cost work start early, while public cloud deployment follows a working local match.

## SRE evidence to collect

Define a demo target, not an invented capacity claim: attempt 10 rooms × 4 clients, then publish the largest stable measured load if lower. Record instance type, region, release SHA, bot behavior, test duration and raw output. Track active rooms/connections, rejected commands, disconnects by reason, queue depth, tick duration p95/p99 and overruns, JVM heap/GC, CPU, memory, network and HTTP/WebSocket failures. Measure input-to-observed-state latency at the client; ping alone is not gameplay latency.

Initial targets to validate after baseline: 10 complete four-player matches with consistent outcomes; reconnect after a 5–10 second network cut; p99 tick processing below the tick budget at the chosen demo load; 60-minute soak without unbounded retained heap; rollback of a deliberately unhealthy image with timestamps. These are hypotheses until tested. Define a small internal SLO around scheduled synthetic create/join/start attempts, including its denominator, measurement window, operating hours and response action. An HTTP health endpoint alone cannot prove gameplay works. [CloudWatch SLO documentation](https://docs.aws.amazon.com/en_en/AmazonCloudWatch/latest/monitoring/CloudWatch-ServiceLevelObjectives.html).

Run three recovery drills: kill the process, deploy a bad image, and replace the host. Save each alert, timeline, runbook actions, recovery measurement and a short postmortem. State explicitly that a crash loses in-memory matches. For each alert, name who receives it and what action they can take.

## Cost and risk controls

Build a region-specific estimate before `terraform apply`: compute hours, EBS, public IPv4, data transfer, ECR, logs, DNS, S3, and any ALB/NAT/RDS added later. Use the [AWS Pricing Calculator](https://docs.aws.amazon.com/pricing-calculator/latest/userguide/generate-estimate.html); do not rely on free tier. A public single EC2 deployment avoids a NAT Gateway in the MVP; if later moved to private subnets, model NAT or VPC endpoint charges. [AWS ECS networking cost guidance](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/networking-outbound.html). Set a budget and a weekly manual bill review; budget alerts follow billing refresh and are not a hard spending cap. [AWS Budgets guidance](https://docs.aws.amazon.com/cost-management/latest/userguide/budgets-best-practices.html).

The biggest schedule risks are unclear source/asset rights, a UI-bound game loop, differing movement rules, nondeterministic simulation, and multiplayer state ownership. The largest operations risks are silently dropped WebSocket events, unbounded queues, deployment killing matches, absent TLS, and cost left running after demos. Handle them at the gates above, not by adding more infrastructure.

## What belongs on a CV

Use evidence-based bullets after the measurements exist, for example:

> Built a server-authoritative Java multiplayer game for four players, with versioned WebSocket commands, reconnect handling, deterministic rule tests and measured [actual] latency at [actual] concurrent rooms.

> Provisioned a single-node AWS environment with Terraform, least-privilege IAM, SSM operations and digest-pinned releases; demonstrated deployment rollback in [actual] minutes.

> Instrumented tick latency, connection health and JVM resources; used bot load and a failure drill to establish [actual] capacity and write an incident runbook.

Do not claim high availability, autoscaling, zero downtime, production SLOs or database recovery unless those have been designed and verified.

## Decision required before coding

The browser is part of the first release because `CONTRACT.md` defines it as the client. During the first audit, confirm team size, weekly hours, a monthly AWS spend ceiling, and whether any original source or assets can be reused. Record decisions and adjust the estimate before committing to a public date.
