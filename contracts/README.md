# Contracts

This directory holds the v1 API, WebSocket, and game wire schemas plus fixtures shared by the browser client and game server. `CONTRACT.md` defines behavior; these schemas define fields and types. The JSON Schema `$id` URLs identify local resources, not network dependencies; register the checked-in game schema when resolving WebSocket references. Install dependencies with `python -m pip install -r contracts/requirements.txt`, then run `python contracts/validate.py` from the repository root. Implementation code belongs in the client and server modules.
