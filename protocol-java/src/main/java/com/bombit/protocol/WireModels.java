package com.bombit.protocol;

import java.util.List;

/** Validated v1 wire values. Constraints such as room-code syntax come from the schemas. */
public final class WireModels {
    private WireModels() {}

    public record GuestSession(String playerId, long expiresAtMs) {}
    public record CreateGame(int maxPlayers) {}
    public record RoomPlayer(String playerId, int slot, boolean ready, boolean connected) {}
    public record RoomSummary(String gameId, String status, int maxPlayers, List<RoomPlayer> players) {}
    public record RestError(String code, String message) {}

    public record Coordinate(int x, int y) {}
    public record Config(int gridWidth, int gridHeight, int tickMs, int movementTicks,
                         int bombFuseTicks, int explosionRange, int maxBombs,
                         int blastVisualTicks, int matchDurationTicks) {}
    public record GameMap(String mapId, List<String> rows) {}
    public record Player(String playerId, int slot, int x, int y, boolean alive,
                         boolean ready, boolean connected) {}
    public record Bomb(String bombId, String ownerId, int x, int y, int explodeAtTick, int range) {}
    public record Result(String outcome, String winnerId, String reason, int finishedAtTick) {}
    public record GameState(String gameId, String status, int maxPlayers, Config config,
                            GameMap map, List<Player> players, List<Bomb> bombs,
                            int tick, Result result) implements ServerPayload {}

    public sealed interface CommandPayload permits JoinGame, Empty, Move {}
    public record JoinGame(String gameId) implements CommandPayload {}
    public record Empty() implements CommandPayload, ServerPayload {}
    public record Move(String direction) implements CommandPayload {}
    public record ClientMessage(int version, String type, String requestId, CommandPayload payload) {}

    public sealed interface ServerPayload permits Empty, Ack, Error, GameState, PlayerJoined,
            PlayerLeft, ReadyChanged, ConnectionChanged, GameStarted, PlayerMoved,
            BombPlaced, BombExploded, CellDestroyed, PlayerDied, GameFinished {}
    public record Ack(String commandType, int appliedTick) implements ServerPayload {}
    public record Error(String code, String message) implements ServerPayload {}
    public record PlayerJoined(Player player) implements ServerPayload {}
    public record PlayerLeft(String playerId) implements ServerPayload {}
    public record ReadyChanged(String playerId, boolean ready) implements ServerPayload {}
    public record ConnectionChanged(String playerId, boolean connected) implements ServerPayload {}
    public record GameStarted(int startedAtTick) implements ServerPayload {}
    public record PlayerMoved(String playerId, int x, int y) implements ServerPayload {}
    public record BombPlaced(Bomb bomb) implements ServerPayload {}
    public record BombExploded(String bombId, List<Coordinate> affectedCells,
                               int visualUntilTick) implements ServerPayload {}
    public record CellDestroyed(int x, int y) implements ServerPayload {}
    public record PlayerDied(String playerId, String reason) implements ServerPayload {}
    public record GameFinished(Result result) implements ServerPayload {}
    public record ServerMessage(int version, String type, String requestId, String gameId,
                                Integer sequence, Integer tick, long serverTimeMs,
                                ServerPayload payload) {}
}
