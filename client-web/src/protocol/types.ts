export interface GuestSession { playerId: string; expiresAtMs: number }
export interface CreateGame { maxPlayers: number }
export interface RoomPlayer { playerId: string; slot: number; ready: boolean; connected: boolean }
export interface RoomSummary { gameId: string; status: GameStatus; maxPlayers: number; players: RoomPlayer[] }
export interface RestError { code: "INVALID_REQUEST" | "UNAUTHORIZED" | "ORIGIN_FORBIDDEN" | "GAME_NOT_FOUND" | "RATE_LIMITED" | "SERVER_CAPACITY"; message: string }

export type GameStatus = "WAITING" | "RUNNING" | "FINISHED";
export type Direction = "UP" | "DOWN" | "LEFT" | "RIGHT";
export type ErrorCode = "INVALID_COMMAND" | "INVALID_REQUEST" | "UNSUPPORTED_VERSION" | "UNAUTHORIZED" | "GAME_NOT_FOUND" | "GAME_FULL" | "GAME_NOT_JOINABLE" | "PLAYER_NOT_IN_GAME" | "PLAYER_ALREADY_IN_GAME" | "PLAYER_DEAD" | "INVALID_MOVEMENT" | "BOMB_PLACEMENT_NOT_ALLOWED" | "GAME_NOT_STARTED" | "GAME_ALREADY_FINISHED" | "REQUEST_ID_CONFLICT" | "RATE_LIMITED" | "SERVER_CAPACITY";
export interface Coordinate { x: number; y: number }
export interface GameConfig { gridWidth: number; gridHeight: number; tickMs: number; movementTicks: number; bombFuseTicks: number; explosionRange: number; maxBombs: number; blastVisualTicks: number; matchDurationTicks: number }
export interface GameMap { mapId: string; rows: string[] }
export interface Player { playerId: string; slot: number; x: number; y: number; alive: boolean; ready: boolean; connected: boolean }
export interface Bomb { bombId: string; ownerId: string; x: number; y: number; explodeAtTick: number; range: number }
export type Result =
  | { outcome: "WIN"; winnerId: string; reason: "SOLE_SURVIVOR"; finishedAtTick: number }
  | { outcome: "DRAW"; winnerId: null; reason: "ALL_ELIMINATED" | "TIME_LIMIT"; finishedAtTick: number };
export interface GameState { gameId: string; status: GameStatus; maxPlayers: number; config: GameConfig; map: GameMap; players: Player[]; bombs: Bomb[]; tick: number; result: Result | null }

type Command<T extends string, P> = { version: 1; type: T; requestId: string; payload: P };
export type ClientMessage =
  | Command<"JOIN_GAME", { gameId: string }>
  | Command<"LEAVE_GAME" | "READY" | "PLACE_BOMB" | "RESYNC", Record<string, never>>
  | Command<"PLAYER_MOVE", { direction: Direction | null }>;

type Control<T extends string, P> = { version: 1; type: T; requestId: string; serverTimeMs: number; payload: P };
type Snapshot = { version: 1; type: "GAME_STATE"; gameId: string; sequence: number; serverTimeMs: number; payload: GameState };
type Event<T extends string, P> = { version: 1; type: T; gameId: string; sequence: number; tick: number; serverTimeMs: number; payload: P };
export type RoomEvent =
  | Event<"PLAYER_JOINED", { player: Player }>
  | Event<"PLAYER_LEFT", { playerId: string }>
  | Event<"PLAYER_READY_CHANGED", { playerId: string; ready: boolean }>
  | Event<"PLAYER_CONNECTION_CHANGED", { playerId: string; connected: boolean }>
  | Event<"GAME_STARTED", { startedAtTick: 0 }>
  | Event<"PLAYER_MOVED", { playerId: string; x: number; y: number }>
  | Event<"BOMB_PLACED", { bomb: Bomb }>
  | Event<"BOMB_EXPLODED", { bombId: string; affectedCells: Coordinate[]; visualUntilTick: number }>
  | Event<"CELL_DESTROYED", Coordinate>
  | Event<"PLAYER_DIED", { playerId: string; reason: "BLAST" | "FORFEIT" }>
  | Event<"GAME_FINISHED", { result: Result }>;
export type ServerMessage =
  | Control<"ACK", { commandType: ClientMessage["type"]; appliedTick: number }>
  | Control<"ERROR", { code: ErrorCode; message: string }>
  | Snapshot
  | RoomEvent;
export type GameSnapshot = Extract<ServerMessage, { type: "GAME_STATE" }>;
