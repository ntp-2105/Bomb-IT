package com.bombit.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

final class ContractFixtureTest {
    private static final Path ROOT = Path.of(System.getProperty("contractRoot"));
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final WireCodec codec = new WireCodec();
    private final JsonNode api = new YAMLMapper().readTree(read("openapi.yaml"));

    private static String read(String path) {
        try {
            return Files.readString(ROOT.resolve(path));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode fixture(String name) {
        return mapper.readTree(read("fixtures/" + name + ".json"));
    }

    private Object rewriteRefs(Object value) {
        if (value instanceof Map<?, ?> source) {
            var result = new LinkedHashMap<String, Object>();
            source.forEach((key, child) -> {
                Object rewritten = child;
                if ("$ref".equals(key) && child instanceof String ref) {
                    rewritten = ref.replace("#/components/schemas/", "#/$defs/");
                } else {
                    rewritten = rewriteRefs(child);
                }
                result.put(key.toString(), rewritten);
            });
            return result;
        }
        if (value instanceof java.util.List<?> list) return list.stream().map(this::rewriteRefs).toList();
        return value;
    }

    private void validateFixture(String name, String json) {
        if (!name.startsWith("rest/")) {
            codec.validate(name, json);
            return;
        }
        var definition = name.substring(5);
        Object components = mapper.convertValue(api.path("components").path("schemas"), Map.class);
        var projection = new LinkedHashMap<String, Object>();
        projection.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        projection.put("$defs", rewriteRefs(components));
        projection.put("$ref", "#/$defs/" + definition);
        var schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
            .getSchema(mapper.writeValueAsString(projection));
        schema.initializeValidators();
        if (!schema.validate(json, InputFormat.JSON).isEmpty()) {
            throw new IllegalArgumentException("Invalid " + name);
        }
    }

    private int validateFlowMessages(JsonNode value) {
        if (value.isArray()) {
            int count = 0;
            for (JsonNode child : value) count += validateFlowMessages(child);
            return count;
        }
        if (!value.isObject()) return 0;
        if (value.path("version").asInt() == 1 && value.has("type")) {
            String type = value.path("type").asText();
            String schema = value.has("gameId") || type.equals("ACK") || type.equals("ERROR")
                ? "websocket/server" : "websocket/client";
            validateFixture(schema, value.toString());
            return 1;
        }
        int count = 0;
        for (JsonNode child : value) count += validateFlowMessages(child);
        return count;
    }

    @Test
    void sharedValidAndInvalidFixtures() {
        for (JsonNode item : fixture("valid").path("cases")) {
            String name = item.path("name").asText();
            String schema = item.path("schema").asText();
            String json = item.path("value").toString();
            validateFixture(schema, json);
            if (schema.equals("websocket/client")) {
                assertEquals(item.path("value"), mapper.readTree(codec.encodeClient(codec.decodeClient(json))), name);
            } else if (schema.equals("websocket/server")) {
                assertEquals(item.path("value"), mapper.readTree(codec.encodeServer(codec.decodeServer(json))), name);
            } else if (schema.startsWith("rest/")) {
                Class<?> type = switch (schema) {
                    case "rest/GuestSession" -> WireModels.GuestSession.class;
                    case "rest/CreateGame" -> WireModels.CreateGame.class;
                    case "rest/RoomSummary" -> WireModels.RoomSummary.class;
                    case "rest/Error" -> WireModels.RestError.class;
                    default -> throw new AssertionError(schema);
                };
                assertEquals(item.path("value"), mapper.valueToTree(mapper.readValue(json, type)), name);
            }
        }
        for (JsonNode item : fixture("invalid").path("cases")) {
            String schema = item.path("schema").asText();
            String json = item.path("value").toString();
            assertThrows(IllegalArgumentException.class, () -> validateFixture(schema, json), item.path("name").asText());
        }
    }

    @Test
    void replayAndFlowsDecodeUsingSharedDtos() {
        JsonNode replay = fixture("replay");
        var snapshot = codec.decodeServer(replay.path("initial").toString());
        assertEquals("GAME_STATE", snapshot.type());
        assertTrue(snapshot.payload() instanceof WireModels.GameState);
        int sequence = snapshot.sequence();
        for (JsonNode raw : replay.path("events")) {
            var event = codec.decodeServer(raw.toString());
            assertEquals(++sequence, event.sequence());
            assertEquals(snapshot.gameId(), event.gameId());
        }
        assertEquals(sequence, replay.path("expected").path("sequence").asInt());
        codec.decodeServer(replay.path("expected").toString());

        JsonNode flows = fixture("flows");
        assertTrue(validateFlowMessages(flows) >= 15);
        validateFixture("rest/CreateGame", flows.path("createJoinStart").path("createRequest").toString());
        validateFixture("rest/RoomSummary", flows.path("createJoinStart").path("createdRoom").toString());
        validateFixture("rest/Error", flows.path("expiredIdentity").path("error").toString());
        validateFixture("rest/GuestSession", flows.path("expiredIdentity").path("newSession").toString());
        validateFixture("game/Bomb", flows.path("chainReaction").path("triggerBomb").toString());
        validateFixture("game/Bomb", flows.path("chainReaction").path("chainedBomb").toString());
        JsonNode duplicate = flows.path("duplicateRequest");
        assertEquals(codec.decodeClient(duplicate.path("original").toString()),
            codec.decodeClient(duplicate.path("retry").toString()));
        assertEquals(codec.decodeServer(duplicate.path("firstOutcome").toString()),
            codec.decodeServer(duplicate.path("retryOutcome").toString()));
        assertNotEquals(codec.decodeClient(duplicate.path("original").toString()),
            codec.decodeClient(duplicate.path("conflict").toString()));
        assertEquals(duplicate.path("original").path("requestId").asText(),
            duplicate.path("conflict").path("requestId").asText());
        assertEquals("REQUEST_ID_CONFLICT", ((WireModels.Error) codec.decodeServer(
            duplicate.path("conflictOutcome").toString()).payload()).code());
        assertEquals("RESYNC", codec.decodeClient(flows.path("sequenceGap").path("resync").toString()).type());
        assertTrue(flows.path("sequenceGap").path("received").path("sequence").asInt()
            > flows.path("sequenceGap").path("snapshotSequence").asInt() + 1);
        assertEquals("JOIN_GAME", codec.decodeClient(flows.path("reconnect").path("sameGuestJoin").toString()).type());
        assertEquals("GAME_STATE", codec.decodeServer(flows.path("sequenceGap").path("replacementSnapshot").toString()).type());
        assertEquals("replay.expected", flows.path("duplicateAndOutOfOrderEvents").path("replacementFixture").asText());
        var delivered = flows.path("duplicateAndOutOfOrderEvents").path("deliveredEventIndexes");
        var sequences = new java.util.ArrayList<Integer>();
        for (JsonNode index : delivered) {
            sequences.add(codec.decodeServer(replay.path("events").path(index.asInt()).toString()).sequence());
        }
        assertEquals(java.util.List.of(6, 6, 8, 7), sequences);
        assertTrue(flows.path("duplicateAndOutOfOrderEvents").path("expectedResync").asBoolean());
        assertFalse(flows.path("expiredIdentity").path("oldSlotReclaimable").asBoolean());
    }
}
