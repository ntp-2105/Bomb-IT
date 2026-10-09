import Ajv2020 from "ajv/dist/2020.js";
import gameSchema from "../../../contracts/game/state.schema.json" with { type: "json" };
import messageSchema from "../../../contracts/websocket/messages.schema.json" with { type: "json" };
import type { ClientMessage, GameState, ServerMessage } from "./types.ts";

const ajv = new Ajv2020({ strict: true, allErrors: true });
ajv.addSchema(gameSchema);
ajv.addSchema(messageSchema);
function requiredSchema(id: string) {
  const validate = ajv.getSchema(id);
  if (!validate) throw new Error(`Missing v1 schema: ${id}`);
  return validate;
}
const client = requiredSchema(`${messageSchema.$id}#/$defs/ClientMessage`);
const server = requiredSchema(`${messageSchema.$id}#/$defs/ServerMessage`);
const state = requiredSchema(`${gameSchema.$id}#/$defs/State`);

function decode<T>(raw: unknown, validate: (value: unknown) => boolean, errors: () => unknown): T {
  const value: unknown = typeof raw === "string" ? JSON.parse(raw) : raw;
  if (!validate(value)) throw new Error(`Invalid v1 wire value: ${JSON.stringify(errors())}`);
  return value as T;
}

export function decodeClient(raw: unknown): ClientMessage {
  return decode<ClientMessage>(raw, client, () => client.errors);
}
export function decodeServer(raw: unknown): ServerMessage {
  return decode<ServerMessage>(raw, server, () => server.errors);
}
export function decodeGameState(raw: unknown): GameState {
  return decode<GameState>(raw, state, () => state.errors);
}
