import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import Ajv2020 from "ajv/dist/2020.js";
import YAML from "yaml";
import { decodeClient, decodeGameState, decodeServer } from "../src/protocol/validate.ts";
import { emptyReplica, reduceReplica } from "../src/state/reducer.ts";

const root = new URL("../../contracts/", import.meta.url);
const fixture = name => JSON.parse(readFileSync(new URL(`fixtures/${name}.json`, root), "utf8"));
const valid = fixture("valid");
const invalid = fixture("invalid");
const replay = fixture("replay");
const flows = fixture("flows");
const game = JSON.parse(readFileSync(new URL("game/state.schema.json", root), "utf8"));
const ws = JSON.parse(readFileSync(new URL("websocket/messages.schema.json", root), "utf8"));
const openapi = YAML.parse(readFileSync(new URL("openapi.yaml", root), "utf8"));
const ajv = new Ajv2020({ strict: true, allErrors: true });
ajv.addSchema(game);
ajv.addSchema(ws);

function rewriteRefs(value) {
  if (Array.isArray(value)) return value.map(rewriteRefs);
  if (value && typeof value === "object") return Object.fromEntries(Object.entries(value).map(([key, child]) => [key, key === "$ref" && typeof child === "string" ? child.replace("#/components/schemas/", "#/$defs/") : rewriteRefs(child)]));
  return value;
}
const restSchemas = rewriteRefs(openapi.components.schemas);
const restValidators = new Map();
function validateFixture(schema, value) {
  const [family, name] = schema.split("/");
  if (family === "websocket") {
    if (name === "client") return decodeClient(value);
    if (name === "server") return decodeServer(value);
  }
  if (family === "game") {
    const id = `${game.$id}#/$defs/${name}`;
    if (!ajv.getSchema(id)(value)) throw new Error(JSON.stringify(ajv.getSchema(id).errors));
    if (name === "State") decodeGameState(value);
    return value;
  }
  if (family === "rest") {
    let validate = restValidators.get(name);
    if (!validate) {
      validate = ajv.compile({ $schema: game.$schema, $defs: restSchemas, $ref: `#/$defs/${name}` });
      restValidators.set(name, validate);
    }
    if (!validate(value)) throw new Error(JSON.stringify(validate.errors));
    return value;
  }
  throw new Error(`Unknown fixture schema: ${schema}`);
}

test("the checked-in schemas accept and reject every shared fixture", () => {
  for (const { name, schema, value } of valid.cases) {
    assert.doesNotThrow(() => validateFixture(schema, value), name);
    if (schema.startsWith("websocket/")) assert.deepEqual(JSON.parse(JSON.stringify(validateFixture(schema, value))), value, name);
  }
  for (const { name, schema, value } of invalid.cases) {
    assert.throws(() => validateFixture(schema, value), undefined, name);
  }
});

test("snapshot plus ordered events equals the next server snapshot", () => {
  let replica = reduceReplica(emptyReplica, decodeServer(replay.initial));
  for (const event of replay.events) replica = reduceReplica(replica, decodeServer(event));
  assert.equal(replica.needsResync, false);
  assert.equal(replica.sequence, replay.expected.sequence);
  assert.deepEqual(replica.state, replay.expected.payload);
  decodeGameState(replica.state);
});

test("duplicates are ignored and out-of-order delivery freezes until resync", () => {
  const flow = flows.duplicateAndOutOfOrderEvents;
  let replica = reduceReplica(emptyReplica, decodeServer(replay.initial));
  const first = decodeServer(replay.events[flow.deliveredEventIndexes[0]]);
  replica = reduceReplica(replica, first);
  assert.equal(replica.sequence, flow.sequenceAfterFirst);
  const beforeDuplicate = replica;
  replica = reduceReplica(replica, decodeServer(replay.events[flow.deliveredEventIndexes[1]]));
  assert.strictEqual(replica, beforeDuplicate);
  replica = reduceReplica(replica, decodeServer(replay.events[flow.deliveredEventIndexes[2]]));
  assert.equal(replica.needsResync, flow.expectedResync);
  const frozen = replica.state;
  replica = reduceReplica(replica, decodeServer(replay.events[flow.deliveredEventIndexes[3]]));
  assert.strictEqual(replica.state, frozen);
  replica = reduceReplica(replica, decodeServer(replay.expected));
  assert.equal(replica.needsResync, false);
  assert.deepEqual(replica.state, replay.expected.payload);
  const restored = replica;
  replica = reduceReplica(replica, decodeServer(replay.events[flow.postSnapshotDuplicateIndex]));
  assert.strictEqual(replica, restored);
});

test("reconnect snapshot resets state and control replies do not advance sequence", () => {
  let replica = reduceReplica(emptyReplica, decodeServer(replay.initial));
  replica = reduceReplica(replica, decodeServer(replay.events[0]));
  replica = reduceReplica(replica, decodeServer(flows.duplicateRequest.firstOutcome));
  assert.equal(replica.sequence, 6);
  replica = reduceReplica(replica, decodeServer(replay.initial));
  assert.equal(replica.sequence, replay.initial.sequence);
  assert.deepEqual(replica.state, replay.initial.payload);
  replica = reduceReplica(replica, decodeServer(flows.reconnect.connectionEvent));
  assert.equal(replica.sequence, replay.initial.sequence);
});

test("duplicate PLACE_BOMB retries preserve the outcome and conflicting reuse is rejected", () => {
  const flow = flows.duplicateRequest;
  assert.deepEqual(decodeClient(flow.original), decodeClient(flow.retry));
  assert.deepEqual(decodeServer(flow.firstOutcome), decodeServer(flow.retryOutcome));
  assert.equal(flow.conflict.requestId, flow.original.requestId);
  assert.notDeepEqual(decodeClient(flow.conflict), decodeClient(flow.original));
  assert.equal(decodeServer(flow.conflictOutcome).payload.code, "REQUEST_ID_CONFLICT");
});

test("all shared flow messages and REST bodies match their schemas", () => {
  let messages = 0;
  const visit = value => {
    if (Array.isArray(value)) return value.forEach(visit);
    if (!value || typeof value !== "object") return;
    if (value.version === 1 && typeof value.type === "string") {
      validateFixture(["ACK", "ERROR"].includes(value.type) || "gameId" in value ? "websocket/server" : "websocket/client", value);
      messages++;
      return;
    }
    Object.values(value).forEach(visit);
  };
  visit(flows);
  assert.ok(messages >= 15);
  validateFixture("rest/CreateGame", flows.createJoinStart.createRequest);
  validateFixture("rest/RoomSummary", flows.createJoinStart.createdRoom);
  validateFixture("rest/Error", flows.expiredIdentity.error);
  validateFixture("rest/GuestSession", flows.expiredIdentity.newSession);
  validateFixture("game/Bomb", flows.chainReaction.triggerBomb);
  validateFixture("game/Bomb", flows.chainReaction.chainedBomb);
});

test("the reducer handles every lobby event independently", () => {
  const byType = type => valid.cases.find(item => item.value.type === type).value;
  const apply = (type, alter = value => value) => {
    const snapshot = structuredClone(replay.initial);
    snapshot.payload.status = "WAITING";
    let replica = reduceReplica(emptyReplica, decodeServer(snapshot));
    const event = alter(structuredClone(byType(type)));
    event.sequence = snapshot.sequence + 1;
    replica = reduceReplica(replica, decodeServer(event));
    return replica.state;
  };
  assert.equal(apply("PLAYER_JOINED", event => { event.payload.player.playerId = "p3"; event.payload.player.slot = 3; return event; }).players.length, 3);
  assert.equal(apply("PLAYER_LEFT").players.length, 1);
  assert.equal(apply("PLAYER_READY_CHANGED", event => { event.payload.ready = false; return event; }).players[0].ready, false);
  assert.equal(apply("PLAYER_CONNECTION_CHANGED").players[0].connected, false);
  assert.equal(apply("GAME_STARTED").status, "RUNNING");
});
