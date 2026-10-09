import type { GameSnapshot, GameState, RoomEvent, ServerMessage } from "../protocol/types.ts";

export interface Replica {
  gameId: string | null;
  sequence: number;
  state: GameState | null;
  needsResync: boolean;
}

export const emptyReplica: Replica = { gameId: null, sequence: 0, state: null, needsResync: false };

function fromSnapshot(message: GameSnapshot): Replica {
  if (message.payload.gameId !== message.gameId) throw new Error("Snapshot game ID mismatch");
  return {
    gameId: message.gameId,
    sequence: message.sequence,
    state: structuredClone(message.payload),
    needsResync: false,
  };
}

function applyEvent(state: GameState, event: RoomEvent): GameState {
  const next = structuredClone(state);
  next.tick = event.tick;
  switch (event.type) {
    case "PLAYER_JOINED":
      next.players.push(structuredClone(event.payload.player));
      next.players.sort((a, b) => a.slot - b.slot);
      break;
    case "PLAYER_LEFT":
      next.players = next.players.filter(player => player.playerId !== event.payload.playerId);
      break;
    case "PLAYER_READY_CHANGED":
      next.players = next.players.map(player => player.playerId === event.payload.playerId ? { ...player, ready: event.payload.ready } : player);
      break;
    case "PLAYER_CONNECTION_CHANGED":
      next.players = next.players.map(player => player.playerId === event.payload.playerId ? { ...player, connected: event.payload.connected } : player);
      break;
    case "GAME_STARTED":
      next.status = "RUNNING";
      break;
    case "PLAYER_MOVED":
      next.players = next.players.map(player => player.playerId === event.payload.playerId ? { ...player, x: event.payload.x, y: event.payload.y } : player);
      break;
    case "BOMB_PLACED":
      next.bombs.push(structuredClone(event.payload.bomb));
      break;
    case "BOMB_EXPLODED":
      next.bombs = next.bombs.filter(bomb => bomb.bombId !== event.payload.bombId);
      break;
    case "CELL_DESTROYED": {
      const { x, y } = event.payload;
      const row = next.map.rows[y];
      if (row?.[x] !== "+") throw new Error("CELL_DESTROYED does not target a breakable cell");
      next.map.rows[y] = row.slice(0, x) + "." + row.slice(x + 1);
      break;
    }
    case "PLAYER_DIED":
      next.players = next.players.map(player => player.playerId === event.payload.playerId ? { ...player, alive: false } : player);
      break;
    case "GAME_FINISHED":
      next.status = "FINISHED";
      next.result = structuredClone(event.payload.result);
      break;
    default: {
      const unhandled: never = event;
      throw new Error(`Unhandled v1 event: ${String(unhandled)}`);
    }
  }
  return next;
}

/** ACK/ERROR never advance room sequence. A gap freezes deltas until a snapshot arrives. */
export function reduceReplica(replica: Replica, message: ServerMessage): Replica {
  if (message.type === "GAME_STATE") return fromSnapshot(message);
  if (message.type === "ACK" || message.type === "ERROR") return replica;
  if (!replica.state || !replica.gameId) return { ...replica, needsResync: true };
  if (message.gameId !== replica.gameId) return replica;
  if (replica.needsResync || message.sequence <= replica.sequence) return replica;
  if (message.sequence !== replica.sequence + 1) return { ...replica, needsResync: true };
  return { ...replica, sequence: message.sequence, state: applyEvent(replica.state, message) };
}
