# State module

Owns the client-side snapshot and event reducer used to prepare state for
rendering. It follows server sequence information and is never authoritative.

`reducer.ts` applies contiguous, validated room events to a full snapshot.
Duplicates are ignored; a gap freezes deltas and sets `needsResync` until a
new snapshot arrives. Rendering and transport consume this state later.
