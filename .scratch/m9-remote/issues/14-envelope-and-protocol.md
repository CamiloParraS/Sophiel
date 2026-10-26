# 14: Envelope and protocol (Kotlin)

**What to build:** SPEC M9 deliverable 3 (D79). Pure Kotlin in `src/remote/kotlin/dev/sophiel/remote/`, test-first in `src/testRemote`: AES-256-GCM seal/open (12-byte random nonce + ciphertext, pairing id as associated data, `javax.crypto`); the plaintext JSON for `log`, `rules`, `start`, `stop` and their replies (`ok`, `reason`, `protecting`, `captureMode`, `accessibilityOff`); gzip for the `log` reply; the replay guard (older than 2 min, or an id seen in the last 2 min, rejected; fake clock); the QR text `sophiel:1:<claimCode>:<base64url key>`. No Android or network code.

**Blocked by:** 10

**Type:** AFK

**Status:** resolved

- [ ] JVM: round trip; flipped bit, wrong pairing id, wrong key rejected
- [ ] JVM: replayed id and stale message rejected; the seen-id set never grows past the 2-min window
- [ ] JVM: QR text round trip; malformed text rejected
- [ ] JSON with what the app already uses (no new library unless there is none)
- [ ] Ends buildable
