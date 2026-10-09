# Contracts

This directory holds the v1 API, WebSocket, and game wire schemas plus fixtures shared by the browser client and game server. `CONTRACT.md` defines behavior; these schemas define fields and types. The JSON Schema `$id` URLs identify local resources, not network dependencies; register the checked-in game schema when resolving WebSocket references. Install dependencies with `python -m pip install -r contracts/requirements.txt`, then run `python contracts/validate.py` from the repository root. Implementation code belongs in the client and server modules.

The Step 2 cross-language gate runs the same `fixtures/valid.json`, `fixtures/invalid.json`, `fixtures/replay.json`, and `fixtures/flows.json` in Java and TypeScript. With Java 21, Python 3, and Node 24 installed, run from the repository root:

```powershell
python -m pip install -r contracts/requirements.txt
python contracts/validate.py
.\gradlew.bat :protocol-java:test
npm --prefix client-web ci
npm --prefix client-web test
npm --prefix client-web run typecheck
npm --prefix client-web run build
```

On Unix, use `./gradlew` in place of `.\gradlew.bat`. The Java module packages the two checked-in JSON Schemas for runtime WebSocket decoding; its fixture tests project REST component schemas from `openapi.yaml`. The browser imports the checked-in JSON Schemas directly. No validator resolves `$id` over the network. A new wire field or event needs schema, DTO/type, and fixture updates together.
