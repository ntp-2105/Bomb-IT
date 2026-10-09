package com.bombit.protocol;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Schema-first decoder and encoder for the v1 WebSocket and game-state boundary. */
public final class WireCodec {
    private static final String GAME_ID = "https://bomb-it.example/contracts/v1/game/state.schema.json";
    private static final String WS_ID = "https://bomb-it.example/contracts/v1/websocket/messages.schema.json";

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final SchemaRegistry registry;

    public WireCodec() {
        registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
            builder -> builder.schemas(Map.of(
                GAME_ID, resource("contracts/game/state.schema.json"),
                WS_ID, resource("contracts/websocket/messages.schema.json"))));
    }

    private static String resource(String name) {
        try (var stream = WireCodec.class.getClassLoader().getResourceAsStream(name)) {
            if (stream == null) throw new IllegalStateException("Missing schema resource: " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read schema resource: " + name, e);
        }
    }

    public void validate(String schemaName, String json) {
        String uri = switch (schemaName) {
            case "websocket/client" -> WS_ID + "#/$defs/ClientMessage";
            case "websocket/server" -> WS_ID + "#/$defs/ServerMessage";
            case "game/State" -> GAME_ID + "#/$defs/State";
            case "game/Map" -> GAME_ID + "#/$defs/Map";
            case "game/Bomb" -> GAME_ID + "#/$defs/Bomb";
            case "game/Result" -> GAME_ID + "#/$defs/Result";
            default -> throw new IllegalArgumentException("Unknown schema: " + schemaName);
        };
        Schema schema = registry.getSchema(SchemaLocation.of(uri));
        schema.initializeValidators();
        var errors = schema.validate(json, InputFormat.JSON);
        if (!errors.isEmpty()) throw new IllegalArgumentException("Invalid " + schemaName + ": " + errors);
    }

    public WireModels.ClientMessage decodeClient(String json) {
        validate("websocket/client", json);
        JsonNode node = mapper.readTree(json);
        String type = node.path("type").asText();
        Class<? extends WireModels.CommandPayload> payloadType = switch (type) {
            case "JOIN_GAME" -> WireModels.JoinGame.class;
            case "PLAYER_MOVE" -> WireModels.Move.class;
            default -> WireModels.Empty.class;
        };
        return new WireModels.ClientMessage(1, type, node.path("requestId").asText(),
            mapper.treeToValue(node.path("payload"), payloadType));
    }

    public WireModels.ServerMessage decodeServer(String json) {
        validate("websocket/server", json);
        JsonNode node = mapper.readTree(json);
        String type = node.path("type").asText();
        Class<? extends WireModels.ServerPayload> payloadType = switch (type) {
            case "ACK" -> WireModels.Ack.class;
            case "ERROR" -> WireModels.Error.class;
            case "GAME_STATE" -> WireModels.GameState.class;
            case "PLAYER_JOINED" -> WireModels.PlayerJoined.class;
            case "PLAYER_LEFT" -> WireModels.PlayerLeft.class;
            case "PLAYER_READY_CHANGED" -> WireModels.ReadyChanged.class;
            case "PLAYER_CONNECTION_CHANGED" -> WireModels.ConnectionChanged.class;
            case "GAME_STARTED" -> WireModels.GameStarted.class;
            case "PLAYER_MOVED" -> WireModels.PlayerMoved.class;
            case "BOMB_PLACED" -> WireModels.BombPlaced.class;
            case "BOMB_EXPLODED" -> WireModels.BombExploded.class;
            case "CELL_DESTROYED" -> WireModels.CellDestroyed.class;
            case "PLAYER_DIED" -> WireModels.PlayerDied.class;
            case "GAME_FINISHED" -> WireModels.GameFinished.class;
            default -> throw new IllegalArgumentException("Unknown server type: " + type);
        };
        return new WireModels.ServerMessage(1, type,
            node.has("requestId") ? node.path("requestId").asText() : null,
            node.has("gameId") ? node.path("gameId").asText() : null,
            node.has("sequence") ? node.path("sequence").asInt() : null,
            node.has("tick") ? node.path("tick").asInt() : null,
            node.path("serverTimeMs").asLong(), mapper.treeToValue(node.path("payload"), payloadType));
    }

    public WireModels.GameState decodeGameState(String json) {
        validate("game/State", json);
        return mapper.readValue(json, WireModels.GameState.class);
    }

    public String encodeClient(WireModels.ClientMessage message) {
        var value = new LinkedHashMap<String, Object>();
        value.put("version", message.version());
        value.put("type", message.type());
        value.put("requestId", message.requestId());
        value.put("payload", message.payload());
        String json = mapper.writeValueAsString(value);
        validate("websocket/client", json);
        return json;
    }

    public String encodeServer(WireModels.ServerMessage message) {
        var value = new LinkedHashMap<String, Object>();
        value.put("version", message.version());
        value.put("type", message.type());
        if (message.requestId() != null) value.put("requestId", message.requestId());
        if (message.gameId() != null) value.put("gameId", message.gameId());
        if (message.sequence() != null) value.put("sequence", message.sequence());
        if (message.tick() != null) value.put("tick", message.tick());
        value.put("serverTimeMs", message.serverTimeMs());
        value.put("payload", message.payload());
        String json = mapper.writeValueAsString(value);
        validate("websocket/server", json);
        return json;
    }
}
