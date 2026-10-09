# Bomb-It-inspired multiplayer game

A browser multiplayer game backed by a Java server. The first release targets one map, 2–4 guest players, room codes, server-authoritative rules, and a single AWS EC2 deployment. Active matches are held in memory and end if the game process stops.

This repository is currently being scaffolded. The v1 wire contract, schemas, fixtures, and validator are present; game rules, networking, deployment automation, and AWS resources are not implemented yet.

## Project documents

- [Architecture and module responsibilities](docs/ARCHITECTURE.md)
- [Step-by-step implementation plan](docs/IMPLEMENTATION_PLAN.md)
- [Wire contract](CONTRACT.md)
- [Project roadmap](PROJECT_ROADMAP.md)
- [Initial stack proposal](STACK.md)

## Repository modules

- `contracts/`: REST and WebSocket schemas plus compatibility fixtures.
- `game-core/`: deterministic Java game rules, independent of Spring and transport.
- `protocol-java/`: Java wire DTOs and serialization mapping.
- `server/`: Spring Boot REST/WebSocket adapters, guest sessions, rooms, scheduling, and telemetry.
- `client-web/`: TypeScript lobby, network state, and Phaser rendering.
- `tests/`: contract checks, bots, load scenarios, and external synthetic checks.
- `infra/terraform/`: protected Terraform state bootstrap and the single-node AWS demo stack.
- `ops/`: container, Caddy, deploy/rollback, and runbook material.

## Toolchain

- Java 21 toolchain for the Java modules.
- Gradle 9.8.0 with Spring Boot 4.1.1 for the server.
- Node.js 24 LTS, Vite 8, TypeScript 6, and Phaser 3.90 for the browser client.
- Terraform version pinned in the infrastructure setup before the first apply.

Use maintained LTS runtimes and update exact patch versions through reviewed changes. The local environment used to create this scaffold has Java 17 and no installed Gradle. The Gradle wrapper must be generated and checked in before a clean checkout or CI can build the Java modules; Java compilation has not been run here.

## Current scope

No database, Redis, accounts, leaderboard, React shell, or multi-node game routing is required for the first playable release. The game sends commands from browsers to the server; clients render authoritative snapshots and events. See the implementation plan for the acceptance gates and operational evidence required before making CV claims.
