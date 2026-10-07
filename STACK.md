# Bomb-It Browser Game — Technology Stack

## 1. Architecture

```text
                         Browser
                            │
                  ┌─────────┴─────────┐
                  │                   │
               React              Phaser
             (Web UI)          (Game Client)
                  │                   │
                  └─────────┬─────────┘
                            │
                      HTTP / WebSocket
                            │
                            ▼
                    Java Spring Boot
                       (Backend)
                            │
                 ┌──────────┴──────────┐
                 │                     │
             PostgreSQL              Redis
            (Persistent DB)       (Optional Cache)
```

---

## 2. Technology Stack

| Layer                           | Technology         | Purpose                                                 |
| ------------------------------- | ------------------ | ------------------------------------------------------- |
| Programming language — frontend | **TypeScript**     | Type-safe browser development                           |
| Game engine                     | **Phaser**         | 2D game rendering, input, animation, physics            |
| Web UI                          | **React**          | Login, menus, profile, lobby, leaderboard               |
| Frontend build tool             | **Vite**           | Development server and production bundling              |
| Backend language                | **Java**           | Server-side application and game logic                  |
| Backend framework               | **Spring Boot**    | REST API, WebSocket, authentication, game services      |
| Backend build tool              | **Gradle**         | Java dependency management and build automation         |
| Real-time communication         | **WebSocket**      | Multiplayer game-state communication                    |
| HTTP API                        | **REST**           | Authentication, users, games, leaderboard, etc.         |
| Database                        | **PostgreSQL**     | Persistent application data                             |
| Cache / temporary state         | **Redis**          | Optional; matchmaking, sessions, distributed game state |
| Containerization                | **Docker**         | Package and deploy backend/services                     |
| Version control                 | **Git + GitHub**   | Source code management and collaboration                |
| CI/CD                           | **GitHub Actions** | Automated testing and deployment                        |

---

## 3. Frontend

### TypeScript

TypeScript is used for the browser-side application.

It provides:

* Static typing
* Classes and interfaces
* Better IDE support
* Compile-time error detection
* Shared data models for API/WebSocket communication

---

### Phaser

Phaser is responsible for the actual game.

It handles:

* Game rendering
* Game loop
* Keyboard/mouse/touch input
* Sprites
* Animations
* Collision detection
* Audio
* Game scenes
* Asset loading

The existing Bomb-It classes can conceptually map to Phaser:

| Existing Java/libGDX | Browser implementation |
| -------------------- | ---------------------- |
| `GameScreen.java`    | `GameScene.ts`         |
| `Player.java`        | `Player.ts`            |
| `Bomb.java`          | `Bomb.ts`              |
| `CellActor.java`     | `Cell.ts`              |
| `GameManager.java`   | `GameManager.ts`       |
| `GameConfig.java`    | `GameConfig.ts`        |

---

### React

React handles UI that is outside the game canvas.

Examples:

* Login/register
* Main menu
* User profile
* Settings
* Lobby
* Matchmaking
* Leaderboard

React should **not** be responsible for rendering the game world.

The architecture should instead be:

```text
React
  │
  ├── Login
  ├── Profile
  ├── Lobby
  └── Leaderboard

Phaser
  │
  ├── Game world
  ├── Player
  ├── Bombs
  ├── Explosions
  └── Animations
```

---

### Vite

Vite is used to develop and build the frontend.

Development:

```bash
npm run dev
```

Production:

```bash
npm run build
```

The production build generates static browser assets that can be deployed to a CDN/static hosting service.

---

# 4. Java Backend

## Spring Boot

The backend remains Java-based.

Responsibilities include:

* Authentication
* User management
* Game/lobby management
* Matchmaking
* Game sessions
* Game rules
* Leaderboards
* REST APIs
* WebSocket communication

Conceptually:

```text
Spring Boot
│
├── auth/
├── player/
├── game/
├── matchmaking/
├── websocket/
└── leaderboard/
```

---

## Gradle

Gradle remains the Java build system.

The backend can be started with:

```bash
./gradlew bootRun
```

It handles:

* Java compilation
* Dependency management
* Testing
* Packaging
* Application execution

---

# 5. Communication

The browser and Java backend communicate using two mechanisms.

## REST

Use REST for relatively infrequent operations:

```text
POST /api/auth/login
GET  /api/users/me
GET  /api/leaderboard
POST /api/games
GET  /api/games/{id}
```

---

## WebSocket

Use WebSocket for real-time multiplayer communication:

```text
Browser                         Java Server
   │                                │
   │──── PLAYER_MOVED ─────────────>│
   │                                │
   │<──── GAME_STATE_UPDATE ────────│
   │                                │
   │──── BOMB_PLACED ──────────────>│
   │                                │
   │<──── EXPLOSION ────────────────│
   │                                │
   │<──── PLAYER_DIED ──────────────│
```

---

# 6. Multiplayer Architecture

The browser should not be the ultimate authority over game state.

Instead:

```text
Browser
   │
   │ Player input
   ▼
Java Game Server
   │
   ├── Validate movement
   ├── Validate bomb placement
   ├── Calculate explosions
   ├── Calculate damage
   ├── Update game state
   └── Determine winner
   │
   ▼
Broadcast state/events
   │
   ├───────────────┐
   ▼               ▼
Player A        Player B
```

This makes the server authoritative and makes basic cheating more difficult.

---

# 7. Database

## PostgreSQL

Use PostgreSQL for persistent data.

Possible tables:

```text
users
players
games
matches
game_results
leaderboards
player_statistics
```

PostgreSQL should **not** be used for high-frequency game-state updates.

For example, avoid:

```text
Player position
     ↓
PostgreSQL
     ↓
every 16 ms
```

Instead:

```text
Real-time state
      ↓
Game Server
      ↓
Game finished
      ↓
PostgreSQL
```

---

# 8. Redis

Redis is optional and should be introduced when the application needs it.

Potential uses:

* Matchmaking
* Lobby state
* Temporary game state
* Sessions
* Pub/Sub
* Communication between multiple game servers

Initial version:

```text
Spring Boot
    │
    └── PostgreSQL
```

Later:

```text
Spring Boot
    │
    ├── PostgreSQL
    │
    └── Redis
```

---

# 9. Containerization

Use Docker to package the backend.

Example:

```text
Docker
│
├── Spring Boot application
├── Java runtime
└── Application dependencies
```

The frontend can be built separately:

```text
TypeScript
    ↓
Vite
    ↓
dist/
    ↓
CDN / Static Hosting
```

---

# 10. Project Structure

A possible project structure is:

```text
bomb-it/
│
├── client/
│   ├── src/
│   │   ├── game/
│   │   │   ├── scenes/
│   │   │   │   ├── BootScene.ts
│   │   │   │   ├── MenuScene.ts
│   │   │   │   └── GameScene.ts
│   │   │   │
│   │   │   ├── entities/
│   │   │   │   ├── Player.ts
│   │   │   │   ├── Bomb.ts
│   │   │   │   └── Cell.ts
│   │   │   │
│   │   │   └── GameManager.ts
│   │   │
│   │   ├── ui/
│   │   └── network/
│   │
│   └── package.json
│
├── server/
│   ├── build.gradle
│   ├── settings.gradle
│   │
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com/bombit/
│           │       ├── BombItApplication.java
│           │       ├── auth/
│           │       ├── game/
│           │       ├── player/
│           │       ├── matchmaking/
│           │       ├── websocket/
│           │       └── leaderboard/
│           │
│           └── resources/
│
├── shared/
│   └── src/
│       ├── GameEvent.ts
│       ├── PlayerState.ts
│       └── GameConfig.ts
│
├── docker-compose.yml
└── README.md
```

---

# 11. Migration from the Existing Bomb-It Project

The existing project uses:

```text
Java
  │
  └── libGDX
       │
       └── LWJGL3
            │
            └── Desktop
```

The new architecture replaces the desktop rendering layer:

```text
Java/libGDX                       Browser
─────────────                     ──────────────
GameScreen.java             →     GameScene.ts
Player.java                 →     Player.ts
Bomb.java                   →     Bomb.ts
CellActor.java              →     Cell.ts
GameManager.java            →     GameManager.ts
GameConfig.java             →     GameConfig.ts
```

The Java backend becomes:

```text
Java
 │
 └── Spring Boot
      ├── REST API
      ├── WebSocket
      ├── Game Server
      ├── Authentication
      ├── Matchmaking
      └── Leaderboard
```

---

# 12. Development Roadmap

## Phase 1 — Understand Existing Game

Study the current Java/libGDX implementation:

```text
BombIt
GameScreen
GameManager
Player
Bomb
CellActor
GameConfig
```

Understand the game rules before rewriting them.

## Phase 2 — Browser Game

Implement:

```text
TypeScript
    +
Phaser
    +
Vite
```

Start with a single-player version.

## Phase 3 — Java Backend

Implement:

```text
Spring Boot
    +
REST API
```

Add:

* Authentication
* Users
* Game creation
* Leaderboard

## Phase 4 — Multiplayer

Add:

```text
WebSocket
```

Move authoritative game-state validation to the Java server.

## Phase 5 — Persistence

Add:

```text
PostgreSQL
```

Store:

* Users
* Matches
* Results
* Statistics
* Leaderboards

## Phase 6 — Scaling

Only if necessary, introduce:

```text
Redis
```

for:

* Matchmaking
* Distributed game state
* Pub/Sub
* Multiple game-server instances

---

# Final Architecture

```text
                         INTERNET
                            │
                            ▼
                    ┌───────────────┐
                    │    Browser    │
                    │               │
                    │ React         │
                    │      +        │
                    │ Phaser        │
                    │      +        │
                    │ TypeScript    │
                    └───────┬───────┘
                            │
                       HTTP / WS
                            │
                            ▼
                 ┌─────────────────────┐
                 │   Java Backend      │
                 │    Spring Boot      │
                 │                     │
                 │ REST API            │
                 │ WebSocket           │
                 │ Authentication      │
                 │ Game Server         │
                 │ Matchmaking         │
                 │ Leaderboard         │
                 └──────────┬──────────┘
                            │
                    ┌───────┴───────┐
                    │               │
                    ▼               ▼
              PostgreSQL          Redis
              Persistent          Optional
                 Data              Cache
```

**Core principle:** Phaser/TypeScript owns **rendering and client interaction**, while Java/Spring Boot owns **backend services and authoritative multiplayer game logic**.
