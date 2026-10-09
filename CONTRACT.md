# Bomb-It v1 network contract

Status: **normative for the first browser release**. Protocol version: **1**.
The schemas in `contracts/openapi.yaml`, `contracts/websocket/messages.schema.json`,
and `contracts/game/state.schema.json` are authoritative for wire fields and
types. This document defines behavior those schemas cannot express. Fixtures
under `contracts/fixtures/` are examples and compatibility checks.

## Scope and authority

One TypeScript/Phaser browser client connects to one Java/Spring Boot process
over same-origin HTTPS and WSS. The server owns rooms, movement, collision,
bombs, damage, and outcomes. Clients send intentions and render server state.
Rooms and guest sessions exist only in that process; a restart ends active
matches. Password accounts, `/api/auth/login`, `/api/users/me`, leaderboard,
persistence, Redis, ranked matchmaking, and spectators are **future scope**.
Java and TypeScript may use different internal types but must match the wire
schemas.

## Common conventions

- JSON objects reject unknown properties; unknown types, enum values,
  unsupported versions, missing required fields, non-integral numbers, and
  out-of-range coordinates are invalid.
- `gameId` is the room code: eight uppercase characters from
  `ABCDEFGHJKMNPQRSTVWXYZ23456789`. The server generates it uniquely among
  live rooms. `playerId` and `bombId` are opaque server-issued strings.
  `requestId` is a client-issued 1–64 character ASCII token using letters,
  digits, underscore, or hyphen.
- Coordinates are zero-based integer grid cells: `x` increases right from
  0 to 14; `y` increases down from 0 to 12. All wire times ending `Ms`
  are Unix epoch milliseconds except durations explicitly named as such.
  Tick numbers and room sequences are nonnegative integers.
- A browser must treat an HTTP or WebSocket `ERROR` code as the stable
  programmatic result; `message` is explanatory text.

## Guest and room REST API

The OpenAPI document defines the exact request/response objects and HTTP
statuses. All three endpoints are same-origin JSON; every mutation and
WebSocket upgrade requires the expected `Origin`. Missing or foreign origins
are rejected with HTTP 403. There is no browser-supplied `playerId`.

| Operation | Success | Behavior |
| --- | --- | --- |
| `POST /api/guest-sessions` | 201 new; 200 existing | Returns `playerId` and `expiresAtMs`. Sets `bombit_guest` as an opaque, `HttpOnly; Secure; SameSite=Strict; Path=/` cookie. A valid cookie retains the token and player identity and renews expiry to 24 hours from this call. An expired or unknown cookie creates a new identity; it cannot reclaim an old slot. |
| `POST /api/games` | 201 | Body `{"maxPlayers":2}` where the value is 2, 3, or 4. Returns a room summary. Creation does not join the creator; use `JOIN_GAME`. One fixed map is used, so there is no `mapId` request field. |
| `GET /api/games/{gameId}` | 200 | Returns room status, capacity, and players' slot, readiness, and connection state. Requires a valid guest cookie, including for room lookup. |

The server accepts at most 100 live rooms and 1,000 live sessions. Each
room's pending input queue holds at most 256 commands; overflow rejects
the new command with `SERVER_CAPACITY` without changing room state. Guest
issuance is limited to five new identities per minute per source address.
Use HTTP
400 for invalid JSON/fields, 401 for a missing or expired session, 403 for
origin failure, 404 for an unknown or expired room, 429 for rate limits, and
503 for server capacity. All error bodies are `{"code":"...","message":"..."}`.
The corresponding codes are `INVALID_REQUEST`, `UNAUTHORIZED`,
`ORIGIN_FORBIDDEN`, `GAME_NOT_FOUND`, `RATE_LIMITED`, and
`SERVER_CAPACITY`, respectively.
The guest-session endpoint has its own issuance rate limit; HTTP 429 applies
before creating another session. Clients show a clear "match ended after
server restart" message when their prior room is gone.

## WebSocket envelopes and command results

`/ws` authenticates the cookie at upgrade. HTTP 401, 403, and 503 reject
invalid sessions, origins, and capacity before upgrading. A complete incoming
UTF-8 text message, including reassembled fragments, is at most 4 KiB;
oversized messages close with code 1009. Only one active WebSocket may
control a guest session; a new valid connection
replaces the old one. One connection belongs to at most one room.

<!-- schema: websocket/client -->
```json
{"version":1,"type":"JOIN_GAME","requestId":"req-1","payload":{"gameId":"ABCD2345"}}
```

Every client command has `version`, `type`, `requestId`, and `payload`.
`JOIN_GAME` selects a room; `LEAVE_GAME`, `READY`, `PLACE_BOMB`, and
`RESYNC` use an empty payload. `PLAYER_MOVE` uses
`{"direction":"UP"|"DOWN"|"LEFT"|"RIGHT"|null}`; `null` releases movement.
The browser sends a new `PLAYER_MOVE` at press, direction change, release,
and at least every 250 ms while held; it also sends `null` on focus loss.
The server clears held movement after 500 ms without a valid refresh and
immediately on disconnect.

For each command, the server returns exactly one requester-only `ACK` or
`ERROR` after validation and application. `ACK` confirms the command's
state change; for `PLAYER_MOVE`, it confirms held input, **not** a successful
step. Position changes appear only as `PLAYER_MOVED`. A rejected command
cannot mutate room state. The server caches each outcome by
`(guest session, requestId)` for 60 seconds, including across reconnects.
An identical retry in that interval returns the cached outcome without
reapplying the command. Reusing the ID with different type or payload returns
`REQUEST_ID_CONFLICT`. The cache holds at least 2,048 recent outcomes per
session; clients do not retry after 60 seconds.

<!-- schema: websocket/server -->
```json
{"version":1,"type":"ACK","requestId":"req-1","serverTimeMs":1790000000000,"payload":{"commandType":"JOIN_GAME","appliedTick":0}}
```

<!-- schema: websocket/server -->
```json
{"version":1,"type":"ERROR","requestId":"req-2","serverTimeMs":1790000000000,"payload":{"code":"GAME_FULL","message":"The room is full."}}
```

Malformed/unsupported messages receive `ERROR` when a valid `requestId`
can be extracted; otherwise the server closes with code 1002. Unsupported or
missing `version` is `UNSUPPORTED_VERSION` when correlatable, then closes
with 1002. Rate-limited commands receive `RATE_LIMITED`; at most 20
commands per second per session are accepted. A connection's outbound queue
holds at most 128 messages. On overflow the server closes it with 1013;
the client reconnects and obtains a snapshot. Stable WebSocket error codes
include `INVALID_COMMAND`, `INVALID_REQUEST`, `UNSUPPORTED_VERSION`,
`UNAUTHORIZED`, `GAME_NOT_FOUND`, `GAME_FULL`, `GAME_NOT_JOINABLE`,
`PLAYER_NOT_IN_GAME`, `PLAYER_ALREADY_IN_GAME`, `PLAYER_DEAD`,
`INVALID_MOVEMENT`, `BOMB_PLACEMENT_NOT_ALLOWED`, `GAME_NOT_STARTED`,
`GAME_ALREADY_FINISHED`, `REQUEST_ID_CONFLICT`, `RATE_LIMITED`, and
`SERVER_CAPACITY`.

## Room lifecycle

Room status is exactly `WAITING`, `RUNNING`, or `FINISHED`. Joining a
waiting room assigns the first vacant slot among 1–`maxPlayers`; players
spawn in slot order. A new guest cannot join a running or finished room.
`READY` is valid once for a connected waiting player; it sets `ready=true`.
The match starts when exactly `maxPlayers` slots are filled **and all are
connected and ready**. Disconnect in `WAITING` retains the slot for 30
seconds but clears readiness; after expiry the slot is removed. Explicit
`LEAVE_GAME` removes a waiting player immediately. An empty waiting room
expires 60 seconds after it became empty.

Room creation installs the fixed map; waiting-room snapshots already include
it. A waiting disconnect emits connection and readiness changes. At start
the server sets tick 0 and broadcasts
`GAME_STARTED`. In `RUNNING`, only the same guest session may rejoin its
retained slot within 30 seconds. The match keeps ticking during a disconnect;
that player remains vulnerable and cannot move. Reconnect restores the slot
and sends a full snapshot. Expiry or explicit leave forfeits and eliminates
that player; explicit leave releases its room membership so it cannot
reclaim the slot. Bombs already placed by eliminated players remain active.
Dead players may observe the match through their existing
connection but cannot move or plant bombs. On completion, broadcast
`GAME_FINISHED`; the finished room remains readable and rejoinable by its
prior players for five minutes, then expires. `READY`, movement, and bomb
placement are invalid outside their stated states.

## Fixed map and simulation

`GameConfig` has `gridWidth=15`, `gridHeight=13`, `tickMs=50`,
`movementTicks=3`, `bombFuseTicks=60`, `explosionRange=2`,
`maxBombs=3`, `blastVisualTicks=6`, and `matchDurationTicks=3600`.
The canonical map is `contracts/fixtures/map.json`: 13 row strings of
15 characters, `#` = indestructible wall, `+` = breakable cell, `.` =
empty. Walls occupy the border and interior cells where both `x` and `y`
are even. Other cells are breakable where `(x + 2*y) % 4 == 0`, except each
spawn and its four orthogonal neighbors. Remaining cells are empty. Spawns
for slots 1–4 are `(1,1)`, `(13,1)`, `(1,11)`, `(13,11)`.

Each room has a serial command queue. At each 50 ms tick, the server takes
queued commands in receive order, updates held directions, attempts eligible
movement, attempts bomb placement, advances fuses, resolves blasts and chain
reactions, applies damage simultaneously, then checks the result. Players
may share a cell. Movement attempts at most one cell every three ticks;
the first held direction can move on the next tick, and each attempt sets
a three-tick cooldown even when blocked. Walls, breakable cells, and
bombs block entry. A player may leave its own newly planted bomb cell but
cannot re-enter it; no other player may enter it.

A living player may plant a bomb on its current cell if there is no bomb
there and it owns fewer than three active bombs. A bomb placed on tick `t`
detonates at tick `t+60`, or earlier if reached by another blast. Blast
cells include the bomb's cell and up to two cells in each cardinal
direction. A wall stops a ray without being affected. The first breakable
cell is affected, destroyed, and stops that ray. A reached bomb detonates
in the same tick; process chain reactions until none remain, then apply one
hit of damage to every living player in any affected cell. A blast is
damaging only on that tick; clients render it until tick `t+6`. Destroyed
cells become `.`. `BOMB_EXPLODED` removes the bomb; remaining bomb
capacity is derived from `maxBombs` minus active owned bombs.
All blasts in one tick read the map as it stood at that tick's start;
destroyed cells become empty only after all rays are calculated. Emit
`BOMB_EXPLODED` events in bomb-ID order, `CELL_DESTROYED` in row-major
order, `PLAYER_DIED` in slot order, and `GAME_FINISHED` last. Each
snapshot has unique player IDs, slots, and bomb IDs.

The match finishes when at most one player remains alive or tick 3600 is
reached. One survivor is `WIN` with `winnerId` and reason
`SOLE_SURVIVOR`; zero survivors is `DRAW` with null winner and reason
`ALL_ELIMINATED`; multiple survivors at the time limit is `DRAW` with
null winner and reason `TIME_LIMIT`. Simultaneous blast deaths are
calculated before outcome selection.

## Events, snapshots, and recovery

Each room-state event has `gameId`, `sequence`, `tick`, `serverTimeMs`, and
`payload`. Sequence begins at 1 and increases by one for each state
event, including `GAME_STARTED` and `GAME_FINISHED`. `ACK` and `ERROR`
are requester-only control messages and have no sequence. A
`GAME_STATE` snapshot carries the latest applied sequence (0 if none)
and does not increment it. Every new or returning `JOIN_GAME` gets
`ACK`, then a full `GAME_STATE`, before any later deltas on that
connection. `RESYNC` returns `ACK`, then a snapshot. The client discards
events at or below the snapshot sequence, applies subsequent contiguous
events, and requests `RESYNC` on a gap. Timestamps do not repair gaps.

State deltas are `PLAYER_JOINED`, `PLAYER_LEFT` (waiting-room removal),
`PLAYER_READY_CHANGED`, `PLAYER_CONNECTION_CHANGED`,
`GAME_STARTED`, `PLAYER_MOVED`, `BOMB_PLACED`,
`BOMB_EXPLODED`, `CELL_DESTROYED`, `PLAYER_DIED`, and
`GAME_FINISHED`. `PLAYER_DIED` has reason `BLAST` or `FORFEIT`.
There is no health or `PLAYER_DAMAGED` event in v1. `GAME_STATE` holds
the full map, configuration, roster, bombs, current tick and terminal
result when finished. Clients cannot infer authoritative damage or
victory from an effect animation.

<!-- schema: websocket/server -->
```json
{"version":1,"type":"GAME_FINISHED","gameId":"ABCD2345","sequence":19,"tick":240,"serverTimeMs":1790000000000,"payload":{"result":{"outcome":"DRAW","winnerId":null,"reason":"ALL_ELIMINATED","finishedAtTick":240}}}
```

The server process keeps no durable match state. After a process restart
the old cookie first fails authentication with HTTP 401 (or a rejected
WebSocket upgrade). After creating a new guest session, lookup of the old
room returns HTTP 404 or `GAME_NOT_FOUND`; the client shows a match-lost
message and offers to create/join a new room.

## Future scope

`POST /api/auth/login`, `GET /api/users/me`, and `GET /api/leaderboard`
are not v1 endpoints. Password accounts, persistent results, rankings,
power-ups, health beyond one-hit elimination, and multi-node room routing
require later contracts and a version or capability decision before use.

## Compatibility and verification

Changing a required field, enum, rule, or event meaning requires a new
protocol version; v1 messages are never silently reinterpreted. Keep
`CONTRACT.md` examples, OpenAPI, JSON Schemas, and fixtures in one change.
Validate both Java and TypeScript wire implementations against the same
fixtures when those implementations are added. The fixture validator
checks schema acceptance/rejection, the canonical map, and a snapshot plus
delta replay.
