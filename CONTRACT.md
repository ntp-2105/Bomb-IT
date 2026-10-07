# Bomb-It Browser Game — Contract Specification

## 1. Purpose

This document defines the communication contracts between the **Bomb-It browser client** and the **Java/Spring Boot game server**.

The client and server use different implementations:

```text
Browser                              Java Server
TypeScript + Phaser                  Java + Spring Boot
       │                                    │
       │        Shared Contract             │
       └──────────────┬─────────────────────┘
                      │
               HTTP / WebSocket
```

The contract defines:

* REST API request/response formats
* WebSocket client commands
* WebSocket server events
* Game state models
* Game configuration
* Enumerations and protocol rules

The contract does **not** define:

* Phaser implementation
* Java game-engine implementation
* Rendering
* Animation
* Internal classes
* Database entities
* Internal server architecture

---

# 2. Contract Design Principles

## 2.1 Share the contract, not the implementation

The TypeScript client and Java server should have independent implementations.

For example:

```text
Contract
PlayerState
    │
    ├───────────────┐
    ▼               ▼
PlayerState.ts   PlayerState.java
```

Both implementations must serialize and deserialize to the same wire format.

---

## 2.2 Server is authoritative

The client sends **commands** representing player intentions.

The server validates those commands and modifies the authoritative game state.

```text
Browser
   │
   │ ClientCommand
   ▼
Java Game Server
   │
   ├── Validate command
   ├── Apply game rules
   ├── Update authoritative state
   └── Generate events
   │
   ▼
Browser clients
```

The client must never be treated as authoritative for:

* Player position
* Bomb placement
* Explosion results
* Damage
* Death
* Power-up collection
* Victory
* Game completion

---

# 3. Communication Protocols

The system uses two communication mechanisms.

## 3.1 REST

REST is used for operations that do not require high-frequency communication.

Examples:

```text
POST /api/auth/login
GET  /api/users/me

POST /api/games
GET  /api/games/{gameId}

GET  /api/leaderboard
```

---

## 3.2 WebSocket

WebSocket is used for real-time multiplayer communication.

```text
Browser
   │
   │ WebSocket
   ▼
Java Game Server
```

Client-to-server messages are called **commands**.

Server-to-client messages are called **events**.

---

# 4. Common Conventions

## 4.1 JSON

The initial protocol uses JSON.

Example:

```json
{
  "type": "PLAYER_MOVED",
  "playerId": "p123",
  "x": 5,
  "y": 7
}
```

---

## 4.2 Identifiers

Identifiers are represented as strings.

Examples:

```text
playerId = "p123"
gameId   = "game-123"
bombId   = "bomb-42"
```

The server is responsible for generating unique identifiers.

---

## 4.3 Coordinates

The game uses grid coordinates.

```text
x → horizontal position
y → vertical position
```

Example:

```json
{
  "x": 5,
  "y": 7
}
```

Coordinates are integers unless explicitly specified otherwise.

---

# 5. Game Configuration Contract

`GameConfig` defines the rules required by both client and server.

Example:

```json
{
  "gridWidth": 15,
  "gridHeight": 13,
  "bombTimer": 3000,
  "maxBombs": 3,
  "explosionRange": 2
}
```

## Schema

```text
GameConfig
├── gridWidth: integer
├── gridHeight: integer
├── bombTimer: integer
├── maxBombs: integer
└── explosionRange: integer
```

### Fields

| Field            | Type    | Description                               |
| ---------------- | ------- | ----------------------------------------- |
| `gridWidth`      | integer | Number of cells horizontally              |
| `gridHeight`     | integer | Number of cells vertically                |
| `bombTimer`      | integer | Bomb countdown in milliseconds            |
| `maxBombs`       | integer | Maximum number of active bombs per player |
| `explosionRange` | integer | Default explosion range                   |

The server should be the source of truth for game configuration.

---

# 6. Player State Contract

`PlayerState` represents the authoritative state of a player.

```json
{
  "playerId": "p123",
  "x": 5,
  "y": 7,
  "alive": true,
  "bombsAvailable": 2
}
```

## Schema

```text
PlayerState
├── playerId: string
├── x: integer
├── y: integer
├── alive: boolean
└── bombsAvailable: integer
```

| Field            | Type    | Description                         |
| ---------------- | ------- | ----------------------------------- |
| `playerId`       | string  | Unique player identifier            |
| `x`              | integer | Grid X coordinate                   |
| `y`              | integer | Grid Y coordinate                   |
| `alive`          | boolean | Whether the player is alive         |
| `bombsAvailable` | integer | Number of bombs currently available |

---

# 7. Bomb State Contract

`BombState` represents an active bomb.

```json
{
  "bombId": "b42",
  "ownerId": "p123",
  "x": 5,
  "y": 7,
  "timer": 3000,
  "explosionRange": 2
}
```

## Schema

```text
BombState
├── bombId: string
├── ownerId: string
├── x: integer
├── y: integer
├── timer: integer
└── explosionRange: integer
```

| Field            | Type    | Description                     |
| ---------------- | ------- | ------------------------------- |
| `bombId`         | string  | Unique bomb identifier          |
| `ownerId`        | string  | Player who placed the bomb      |
| `x`              | integer | Bomb X coordinate               |
| `y`              | integer | Bomb Y coordinate               |
| `timer`          | integer | Remaining timer in milliseconds |
| `explosionRange` | integer | Explosion range                 |

---

# 8. Cell State Contract

`CellState` represents a map cell.

Example:

```json
{
  "x": 5,
  "y": 7,
  "type": "BREAKABLE"
}
```

## Cell types

```text
EMPTY
WALL
BREAKABLE
```

## Schema

```text
CellState
├── x: integer
├── y: integer
└── type: CellType
```

---

# 9. Game State Contract

`GameState` represents the authoritative state of a game.

Example:

```json
{
  "gameId": "game-123",
  "status": "RUNNING",
  "players": [
    {
      "playerId": "p1",
      "x": 5,
      "y": 7,
      "alive": true,
      "bombsAvailable": 2
    }
  ],
  "bombs": [
    {
      "bombId": "b42",
      "ownerId": "p1",
      "x": 6,
      "y": 7,
      "timer": 2500,
      "explosionRange": 2
    }
  ]
}
```

## Schema

```text
GameState
├── gameId: string
├── status: GameStatus
├── players: PlayerState[]
├── bombs: BombState[]
└── cells: CellState[]
```

---

# 10. Match State Contract

`MatchState` represents the state of a multiplayer match at a higher level.

```json
{
  "gameId": "game-123",
  "status": "WAITING",
  "players": 2,
  "maxPlayers": 4
}
```

## Schema

```text
MatchState
├── gameId: string
├── status: MatchStatus
├── players: integer
└── maxPlayers: integer
```

---

# 11. Client Commands

Client commands represent **requests from the browser to the server**.

The client must not assume that a command was successfully executed.

## Common command structure

```json
{
  "type": "COMMAND_TYPE",
  "requestId": "req-123",
  "payload": {}
}
```

`requestId` allows the client to correlate requests with responses or errors when necessary.

---

# 12. `JOIN_GAME`

Requests to join a game.

```json
{
  "type": "JOIN_GAME",
  "requestId": "req-1",
  "payload": {
    "gameId": "game-123"
  }
}
```

The server validates:

* Game exists
* Game accepts new players
* Player is not already in another game
* Game has available capacity

---

# 13. `LEAVE_GAME`

Requests to leave the current game.

```json
{
  "type": "LEAVE_GAME",
  "requestId": "req-2",
  "payload": {}
}
```

---

# 14. `READY`

Indicates that the player is ready.

```json
{
  "type": "READY",
  "requestId": "req-3",
  "payload": {}
}
```

---

# 15. `PLAYER_MOVE`

Requests a player movement.

```json
{
  "type": "PLAYER_MOVE",
  "requestId": "req-4",
  "payload": {
    "direction": "UP"
  }
}
```

## Directions

```text
UP
DOWN
LEFT
RIGHT
```

The client should send the player's intended movement.

The server determines whether the movement is valid.

The client must not send an arbitrary authoritative position such as:

```json
{
  "x": 9999,
  "y": 9999
}
```

and expect the server to accept it.

---

# 16. `PLACE_BOMB`

Requests to place a bomb.

```json
{
  "type": "PLACE_BOMB",
  "requestId": "req-5",
  "payload": {}
}
```

The server determines:

* Player is alive
* Player is currently in a game
* Player has an available bomb
* Current position allows bomb placement
* Other game rules are satisfied

The client does not determine whether the bomb was successfully placed.

---

# 17. Server Events

Server events represent **facts about the authoritative game state**.

Common event structure:

```json
{
  "type": "EVENT_TYPE",
  "timestamp": 1790000000000,
  "payload": {}
}
```

The `timestamp` is generated by the server.

---

# 18. `GAME_STARTED`

```json
{
  "type": "GAME_STARTED",
  "timestamp": 1790000000000,
  "payload": {
    "gameId": "game-123",
    "config": {
      "gridWidth": 15,
      "gridHeight": 13,
      "bombTimer": 3000,
      "maxBombs": 3,
      "explosionRange": 2
    }
  }
}
```

---

# 19. `PLAYER_JOINED`

```json
{
  "type": "PLAYER_JOINED",
  "timestamp": 1790000000000,
  "payload": {
    "player": {
      "playerId": "p123",
      "x": 1,
      "y": 1,
      "alive": true,
      "bombsAvailable": 3
    }
  }
}
```

---

# 20. `PLAYER_LEFT`

```json
{
  "type": "PLAYER_LEFT",
  "timestamp": 1790000000000,
  "payload": {
    "playerId": "p123"
  }
}
```

---

# 21. `PLAYER_MOVED`

```json
{
  "type": "PLAYER_MOVED",
  "timestamp": 1790000000000,
  "payload": {
    "playerId": "p123",
    "x": 5,
    "y": 7
  }
}
```

This event represents a **server-approved movement**.

---

# 22. `BOMB_PLACED`

```json
{
  "type": "BOMB_PLACED",
  "timestamp": 1790000000000,
  "payload": {
    "bomb": {
      "bombId": "b42",
      "ownerId": "p123",
      "x": 5,
      "y": 7,
      "timer": 3000,
      "explosionRange": 2
    }
  }
}
```

---

# 23. `BOMB_EXPLODED`

```json
{
  "type": "BOMB_EXPLODED",
  "timestamp": 1790000000000,
  "payload": {
    "bombId": "b42",
    "affectedCells": [
      {
        "x": 5,
        "y": 7
      },
      {
        "x": 4,
        "y": 7
      },
      {
        "x": 6,
        "y": 7
      },
      {
        "x": 5,
        "y": 6
      }
    ]
  }
}
```

The server calculates the explosion.

The client only renders the result.

---

# 24. `CELL_DESTROYED`

```json
{
  "type": "CELL_DESTROYED",
  "timestamp": 1790000000000,
  "payload": {
    "x": 6,
    "y": 7
  }
}
```

---

# 25. `PLAYER_DAMAGED`

```json
{
  "type": "PLAYER_DAMAGED",
  "timestamp": 1790000000000,
  "payload": {
    "playerId": "p123"
  }
}
```

---

# 26. `PLAYER_DIED`

```json
{
  "type": "PLAYER_DIED",
  "timestamp": 1790000000000,
  "payload": {
    "playerId": "p123"
  }
}
```

---

# 27. `GAME_FINISHED`

```json
{
  "type": "GAME_FINISHED",
  "timestamp": 1790000000000,
  "payload": {
    "gameId": "game-123",
    "winnerId": "p456",
    "results": [
      {
        "playerId": "p123",
        "rank": 2
      },
      {
        "playerId": "p456",
        "rank": 1
      }
    ]
  }
}
```

---

# 28. Error Contract

The server should provide a consistent error format.

```json
{
  "type": "ERROR",
  "requestId": "req-5",
  "code": "BOMB_PLACEMENT_NOT_ALLOWED",
  "message": "Player cannot place a bomb at this time."
}
```

## Example error codes

```text
INVALID_COMMAND
INVALID_REQUEST
UNAUTHORIZED
GAME_NOT_FOUND
GAME_FULL
PLAYER_NOT_IN_GAME
PLAYER_ALREADY_IN_GAME
PLAYER_DEAD
INVALID_MOVEMENT
BOMB_PLACEMENT_NOT_ALLOWED
GAME_NOT_STARTED
GAME_ALREADY_FINISHED
```

The client should use `code` for programmatic handling rather than parsing `message`.

---

# 29. REST API Contracts

## Authentication

### `POST /api/auth/login`

Request:

```json
{
  "username": "player",
  "password": "password"
}
```

Response:

```json
{
  "accessToken": "...",
  "expiresIn": 3600,
  "player": {
    "playerId": "p123"
  }
}
```

---

## Current User

### `GET /api/users/me`

Response:

```json
{
  "playerId": "p123",
  "username": "player"
}
```

---

## Create Game

### `POST /api/games`

Request:

```json
{
  "maxPlayers": 4,
  "mapId": "classic-01"
}
```

Response:

```json
{
  "gameId": "game-123",
  "status": "WAITING",
  "maxPlayers": 4
}
```

---

## Get Game

### `GET /api/games/{gameId}`

Response:

```json
{
  "gameId": "game-123",
  "status": "RUNNING",
  "players": 3,
  "maxPlayers": 4
}
```

---

## Leaderboard

### `GET /api/leaderboard`

Response:

```json
{
  "entries": [
    {
      "playerId": "p123",
      "username": "PlayerOne",
      "wins": 42,
      "games": 100
    }
  ]
}
```

---

# 30. Enumerations

## GameStatus

```text
WAITING
STARTING
RUNNING
FINISHED
CANCELLED
```

## MatchStatus

```text
WAITING
READY
RUNNING
FINISHED
```

## CellType

```text
EMPTY
WALL
BREAKABLE
```

## Direction

```text
UP
DOWN
LEFT
RIGHT
```

---

# 31. WebSocket Message Flow

A typical bomb-placement sequence:

```text
Browser                                  Server
   │                                       │
   │ PLACE_BOMB                            │
   │──────────────────────────────────────>│
   │                                       │
   │                                Validate command
   │                                       │
   │                                Create bomb
   │                                       │
   │ BOMB_PLACED                           │
   │<──────────────────────────────────────│
   │                                       │
   │                                Wait for timer
   │                                       │
   │ BOMB_EXPLODED                         │
   │<──────────────────────────────────────│
   │                                       │
   │ PLAYER_DAMAGED                        │
   │<──────────────────────────────────────│
   │                                       │
   │ PLAYER_DIED                           │
   │<──────────────────────────────────────│
```

The browser does not directly tell the server:

```text
"the bomb exploded"
"the player died"
"the player is at X/Y"
```

It only sends commands representing player actions.

---

# 32. Initial Full Game State

When a player joins an active game, the server may send a complete snapshot:

```json
{
  "type": "GAME_STATE",
  "timestamp": 1790000000000,
  "payload": {
    "gameId": "game-123",
    "status": "RUNNING",
    "players": [],
    "bombs": [],
    "cells": []
  }
}
```

This allows a newly connected client to reconstruct the current game.

After initialization, the client can process incremental events.

```text
Initial GAME_STATE
        │
        ▼
Client builds local state
        │
        ▼
PLAYER_MOVED
BOMB_PLACED
BOMB_EXPLODED
PLAYER_DIED
...
```

---

# 33. State Synchronization

The client maintains a **local representation** of the game state for rendering.

```text
Server authoritative state
            │
            │ events / snapshots
            ▼
Client local state
            │
            ▼
         Phaser
            │
            ▼
       Screen output
```

The local client state is not authoritative.

If the server sends:

```json
{
  "type": "PLAYER_MOVED",
  "payload": {
    "playerId": "p123",
    "x": 6,
    "y": 7
  }
}
```

the client updates its local representation.

---

# 34. Contract Versioning

Messages should contain a protocol version.

Example:

```json
{
  "version": 1,
  "type": "PLAYER_MOVED",
  "timestamp": 1790000000000,
  "payload": {
    "playerId": "p123",
    "x": 6,
    "y": 7
  }
}
```

When the protocol changes incompatibly:

```text
v1
v2
```

should be treated as separate protocol versions.

This prevents an updated client and old server from silently interpreting messages differently.

---

# 35. Recommended Contract Files

The eventual repository can organize contracts as:

```text
shared/
│
├── api/
│   └── openapi.yaml
│
├── websocket/
│   ├── client-commands.schema.json
│   └── server-events.schema.json
│
└── game/
    ├── game-config.schema.json
    ├── player-state.schema.json
    ├── bomb-state.schema.json
    ├── cell-state.schema.json
    ├── game-state.schema.json
    └── match-state.schema.json
```

For the initial implementation, these can also be kept in one document:

```text
shared/
└── CONTRACTS.md
```

and split into schemas later.

---

# 36. Mapping to TypeScript

The TypeScript client should create its own types based on this contract.

Example:

```ts
export interface PlayerState {
  playerId: string;
  x: number;
  y: number;
  alive: boolean;
  bombsAvailable: number;
}
```

Commands:

```ts
export type ClientCommand =
  | JoinGameCommand
  | LeaveGameCommand
  | ReadyCommand
  | PlayerMoveCommand
  | PlaceBombCommand;
```

Events:

```ts
export type ServerEvent =
  | GameStartedEvent
  | PlayerJoinedEvent
  | PlayerMovedEvent
  | BombPlacedEvent
  | BombExplodedEvent
  | PlayerDiedEvent
  | GameFinishedEvent
  | ErrorEvent;
```

These are client-side implementations of the contract.

---

# 37. Mapping to Java

The Java server should independently create DTOs.

For example:

```java
public record PlayerState(
    String playerId,
    int x,
    int y,
    boolean alive,
    int bombsAvailable
) {}
```

Command:

```java
public record PlaceBombCommand(
    String type,
    String requestId
) {}
```

Event:

```java
public record BombPlacedEvent(
    String type,
    long timestamp,
    BombState bomb
) {}
```

These classes are not required to have the same structure internally as the TypeScript classes.

They only need to satisfy the network contract.

---

# 38. What Must Not Be Part of the Shared Contract

The following should remain implementation-specific.

## Client-only

```text
Phaser.Scene
Sprite
Animation
KeyboardInput
Camera
Sound
ParticleEffect
React components
```

## Server-only

```text
JPA Entity
Repository
Service
GameEngine implementation
CollisionService
MatchmakingService
Database transaction
Redis implementation
```

For example:

```text
PlayerState
     │
     ├── TypeScript → Player.ts → Phaser
     │
     └── Java       → Player.java → GameEngine
```

`PlayerState` is shared conceptually.

`Player.ts` and `Player.java` are not.

---

# 39. Source of Truth

The contract should have a clearly defined source of truth.

Recommended:

```text
                    Contract
                       │
            ┌──────────┴──────────┐
            ▼                     ▼
       TypeScript              Java
        Client                 Server
```

For REST:

```text
OpenAPI
   │
   ├── TypeScript API types
   └── Java DTO/API definitions
```

For WebSocket:

```text
JSON Schema / protocol specification
   │
   ├── TypeScript event types
   └── Java DTOs
```

The implementations should not independently redefine the protocol.

---

# 40. Minimal Initial Contract

For the first multiplayer implementation, the project does not need every possible event.

Start with:

```text
GameConfig
PlayerState
BombState
GameState

ClientCommand
├── JOIN_GAME
├── READY
├── PLAYER_MOVE
├── PLACE_BOMB
└── LEAVE_GAME

ServerEvent
├── GAME_STARTED
├── PLAYER_JOINED
├── PLAYER_LEFT
├── PLAYER_MOVED
├── BOMB_PLACED
├── BOMB_EXPLODED
├── PLAYER_DIED
└── GAME_FINISHED

ERROR
```

Then add additional contracts when the corresponding game mechanics are implemented.

---

# 41. Final Architecture

```text
                       CONTRACT
                          │
         ┌────────────────┴────────────────┐
         │                                 │
         ▼                                 ▼
   Browser Client                    Java Server
 TypeScript + Phaser               Spring Boot + Game Engine
         │                                 │
         │                                 │
         ├──── ClientCommand ─────────────►│
         │                                 │
         │◄──── ServerEvent ──────────────┤
         │                                 │
         │◄──── GameState ────────────────┤
         │                                 │
         └──────── REST API ──────────────►│
```

The key rule is:

> **The contract defines what crosses the network. It should not attempt to define how either side implements the game.**

This allows the existing Java/libGDX game logic to be progressively rewritten while the Java Spring Boot server and TypeScript/Phaser client remain compatible.
