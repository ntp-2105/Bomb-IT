# Protocol module

Owns the browser's wire types, runtime validation, and REST/WebSocket adapter.
The shared contract under the repository's `contracts/` directory is the source
of truth. The v1 wire schemas are now defined; implement client definitions
against them without game rules or Phaser types here.

`types.ts` defines the v1 REST, game, and WebSocket values. `validate.ts`
decodes unknown messages and game state against the checked-in JSON Schemas
before they enter client state. `npm test` checks the shared fixtures and
projects REST component schemas from OpenAPI. Network adapters are a later step.
