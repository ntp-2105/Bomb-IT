"""Validate the v1 wire artifacts and representative state replay.

Install dependencies with: python -m pip install -r contracts/requirements.txt
Run from any directory: python contracts/validate.py
"""

from __future__ import annotations

import copy
import json
import re
from pathlib import Path

import yaml
from jsonschema import Draft202012Validator
from referencing import Registry, Resource


ROOT = Path(__file__).resolve().parent
GAME = json.loads((ROOT / "game/state.schema.json").read_text(encoding="utf-8"))
WS = json.loads((ROOT / "websocket/messages.schema.json").read_text(encoding="utf-8"))
API = yaml.safe_load((ROOT / "openapi.yaml").read_text(encoding="utf-8"))
REGISTRY = Registry().with_resource(GAME["$id"], Resource.from_contents(GAME))


def validator(name: str) -> Draft202012Validator:
    family, definition = name.split("/", 1)
    if family == "game":
        document = {**GAME, "$ref": f"#/$defs/{definition}"}
        return Draft202012Validator(document, registry=REGISTRY)
    if family == "websocket":
        document = {**WS, "$ref": f"#/$defs/{definition.title()}Message"}
        return Draft202012Validator(document, registry=REGISTRY)
    if family == "rest":
        document = {**API, "$ref": f"#/components/schemas/{definition}"}
        return Draft202012Validator(document)
    raise AssertionError(f"Unknown fixture schema: {name}")


def assert_valid(name: str, value: object) -> None:
    problems = list(validator(name).iter_errors(value))
    if problems:
        first = problems[0]
        raise AssertionError(f"{name}: {first.message} at {list(first.absolute_path)}")


def check_openapi() -> None:
    assert API["openapi"] == "3.1.0"
    assert set(API["paths"]) == {
        "/api/guest-sessions",
        "/api/games",
        "/api/games/{gameId}",
    }
    seen_operation_ids: set[str] = set()
    for path in API["paths"].values():
        for operation in path.values():
            operation_id = operation["operationId"]
            assert operation_id not in seen_operation_ids
            seen_operation_ids.add(operation_id)
            assert operation["responses"]

    def inspect(value: object) -> None:
        if isinstance(value, dict):
            if "$ref" in value:
                pointer = value["$ref"]
                assert pointer.startswith("#/"), pointer
                target: object = API
                for component in pointer[2:].split("/"):
                    target = target[component.replace("~1", "/").replace("~0", "~")]
                assert target is not None
            for child in value.values():
                inspect(child)
        elif isinstance(value, list):
            for child in value:
                inspect(child)

    inspect(API)
    for schema in API["components"]["schemas"].values():
        Draft202012Validator.check_schema(schema)


def check_map() -> None:
    map_state = json.loads((ROOT / "fixtures/map.json").read_text(encoding="utf-8"))
    assert_valid("game/Map", map_state)
    spawns = ((1, 1), (13, 1), (1, 11), (13, 11))
    rows = []
    for y in range(13):
        row = ""
        for x in range(15):
            safe = any(abs(x - sx) + abs(y - sy) <= 1 for sx, sy in spawns)
            if x in (0, 14) or y in (0, 12) or (x % 2 == 0 and y % 2 == 0):
                row += "#"
            elif not safe and (x + 2 * y) % 4 == 0:
                row += "+"
            else:
                row += "."
        rows.append(row)
    assert map_state == {"mapId": "classic-01", "rows": rows}


def check_cases() -> tuple[int, int]:
    counts = []
    for filename, should_pass in (("valid.json", True), ("invalid.json", False)):
        data = json.loads((ROOT / "fixtures" / filename).read_text(encoding="utf-8"))
        names: set[str] = set()
        for case in data["cases"]:
            assert case["name"] not in names, case["name"]
            names.add(case["name"])
            errors = list(validator(case["schema"]).iter_errors(case["value"]))
            if should_pass:
                assert not errors, f"{case['name']}: {errors[0].message if errors else ''}"
            else:
                assert errors, f"Invalid fixture passed: {case['name']}"
        counts.append(len(data["cases"]))
    return counts[0], counts[1]


def check_document_examples() -> int:
    content = (ROOT.parent / "CONTRACT.md").read_text(encoding="utf-8")
    all_blocks = re.findall(r"```json\s*([\s\S]*?)```", content)
    tagged = re.findall(
        r"<!-- schema: ([\w/-]+) -->\s*```json\s*([\s\S]*?)```",
        content,
    )
    assert len(all_blocks) == len(tagged), "Every JSON example needs a schema tag"
    for schema_name, raw in tagged:
        assert_valid(schema_name, json.loads(raw))
    return len(tagged)


def check_replay() -> int:
    replay = json.loads((ROOT / "fixtures/replay.json").read_text(encoding="utf-8"))
    initial, expected = replay["initial"], replay["expected"]
    assert_valid("websocket/server", initial)
    assert_valid("websocket/server", expected)
    state = copy.deepcopy(initial["payload"])
    sequence = initial["sequence"]
    for event in replay["events"]:
        assert_valid("websocket/server", event)
        assert event["gameId"] == initial["gameId"]
        assert event["sequence"] == sequence + 1, "Gap or duplicate in replay"
        sequence = event["sequence"]
        state["tick"] = event["tick"]
        payload = event["payload"]
        kind = event["type"]
        if kind == "PLAYER_MOVED":
            player = next(p for p in state["players"] if p["playerId"] == payload["playerId"])
            player["x"], player["y"] = payload["x"], payload["y"]
        elif kind == "BOMB_PLACED":
            state["bombs"].append(payload["bomb"])
        elif kind == "BOMB_EXPLODED":
            state["bombs"] = [b for b in state["bombs"] if b["bombId"] != payload["bombId"]]
        elif kind == "CELL_DESTROYED":
            y, x = payload["y"], payload["x"]
            row = state["map"]["rows"][y]
            assert row[x] == "+"
            state["map"]["rows"][y] = row[:x] + "." + row[x + 1 :]
        elif kind == "PLAYER_DIED":
            player = next(p for p in state["players"] if p["playerId"] == payload["playerId"])
            player["alive"] = False
        elif kind == "GAME_FINISHED":
            state["status"] = "FINISHED"
            state["result"] = payload["result"]
        else:
            raise AssertionError(f"Replay reducer lacks {kind}")
    assert sequence == expected["sequence"]
    assert state == expected["payload"], "Snapshot plus deltas differs from server snapshot"
    assert_valid("game/State", state)
    return len(replay["events"])


def check_flows() -> None:
    flows = json.loads((ROOT / "fixtures/flows.json").read_text(encoding="utf-8"))
    start = flows["createJoinStart"]
    assert_valid("rest/CreateGame", start["createRequest"])
    assert_valid("rest/RoomSummary", start["createdRoom"])
    assert start["createdRoom"]["players"] == []
    for command in start["joins"] + start["ready"]:
        assert_valid("websocket/client", command)
    assert_valid("websocket/server", start["start"])
    assert len(start["joins"]) == start["createdRoom"]["maxPlayers"]
    assert len(start["ready"]) == start["createdRoom"]["maxPlayers"]

    duplicate = flows["duplicateRequest"]
    for key in ("original", "retry", "conflict"):
        assert_valid("websocket/client", duplicate[key])
    for key in ("firstOutcome", "retryOutcome", "conflictOutcome"):
        assert_valid("websocket/server", duplicate[key])
    assert duplicate["original"] == duplicate["retry"]
    assert duplicate["firstOutcome"] == duplicate["retryOutcome"]
    assert duplicate["conflict"]["requestId"] == duplicate["original"]["requestId"]
    assert duplicate["conflict"] != duplicate["original"]
    assert duplicate["conflictOutcome"]["payload"]["code"] == "REQUEST_ID_CONFLICT"

    gap = flows["sequenceGap"]
    assert_valid("websocket/server", gap["received"])
    assert_valid("websocket/client", gap["resync"])
    assert gap["received"]["sequence"] > gap["snapshotSequence"] + 1
    assert_valid("websocket/server", gap["replacementSnapshot"])
    assert gap["replacementSnapshot"]["type"] == "GAME_STATE"
    assert gap["replacementSnapshot"]["sequence"] >= gap["received"]["sequence"]
    assert gap["replacementSnapshot"]["payload"]["bombs"] == [gap["received"]["payload"]["bomb"]]

    delivery = flows["duplicateAndOutOfOrderEvents"]
    assert delivery["snapshotFixture"] == "replay.initial"
    assert delivery["replacementFixture"] == "replay.expected"
    replay = json.loads((ROOT / "fixtures/replay.json").read_text(encoding="utf-8"))
    delivered = [replay["events"][index] for index in delivery["deliveredEventIndexes"]]
    for event in delivered:
        assert_valid("websocket/server", event)
    assert [event["sequence"] for event in delivered] == [6, 6, 8, 7]
    assert delivery["sequenceAfterFirst"] == delivered[0]["sequence"]
    assert delivery["expectedResync"] is True
    assert_valid("websocket/server", replay["expected"])
    assert replay["events"][delivery["postSnapshotDuplicateIndex"]]["sequence"] <= replay["expected"]["sequence"]

    reconnect = flows["reconnect"]
    assert reconnect["retainedForMs"] == 30000
    assert_valid("websocket/client", reconnect["sameGuestJoin"])
    assert_valid("websocket/server", reconnect["connectionEvent"])
    assert reconnect["snapshotFixture"] == "replay.initial"
    replay = json.loads((ROOT / "fixtures/replay.json").read_text(encoding="utf-8"))
    assert reconnect["connectionEvent"]["sequence"] == replay["initial"]["sequence"]
    assert replay["initial"]["payload"]["players"][0]["connected"] is True

    expired = flows["expiredIdentity"]
    assert expired["upgradeStatus"] == 401
    assert_valid("rest/Error", expired["error"])
    assert_valid("rest/GuestSession", expired["newSession"])
    assert expired["oldPlayerId"] != expired["newSession"]["playerId"]
    assert expired["oldSlotReclaimable"] is False

    chain = flows["chainReaction"]
    for bomb in (chain["triggerBomb"], chain["chainedBomb"]):
        assert_valid("game/Bomb", bomb)
    for event in chain["events"]:
        assert_valid("websocket/server", event)
    assert chain["events"][0]["payload"]["bombId"] == chain["triggerBomb"]["bombId"]
    assert chain["events"][1]["payload"]["bombId"] == chain["chainedBomb"]["bombId"]
    chained_cell = {"x": chain["chainedBomb"]["x"], "y": chain["chainedBomb"]["y"]}
    assert chained_cell in chain["events"][0]["payload"]["affectedCells"]
    assert chain["chainedBomb"]["explodeAtTick"] > chain["events"][1]["tick"]

    draw = flows["simultaneousDeathDraw"]["events"]
    for event in draw:
        assert_valid("websocket/server", event)
    assert [event["sequence"] for event in draw] == [10, 11, 12]
    assert draw[0]["type"] == draw[1]["type"] == "PLAYER_DIED"
    assert draw[0]["tick"] == draw[1]["tick"] == draw[2]["tick"]
    assert draw[2]["payload"]["result"]["outcome"] == "DRAW"


def main() -> None:
    Draft202012Validator.check_schema(GAME)
    Draft202012Validator.check_schema(WS)
    check_openapi()
    check_map()
    valid, invalid = check_cases()
    examples = check_document_examples()
    deltas = check_replay()
    check_flows()
    print(
        f"v1 contract valid: {valid} valid fixtures, {invalid} invalid fixtures, "
        f"{examples} document examples, {deltas} replay deltas, 8 flows"
    )


if __name__ == "__main__":
    main()
