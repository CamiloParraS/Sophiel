# 04: Protocol, accounts and end-to-end encryption

Type: grilling
Status: resolved
Blocked by: 02

## Question

Fix the wire protocol between Parent phone, server and Child phone so the server can route but never read.

- Accounts: email + password sign-up/login, password hashing (`Bun.password`), session token lifetime, where the Parent phone keeps it.
- Pairing: QR contents (pairing id, key, server URL?), how the Child phone authenticates to the server afterwards (a device token minted at pairing?), naming the Child phone, unpair from either side, and what the server row holds.
- Envelope: AES-GCM with the pairing key (`javax.crypto`), nonce handling, replay protection, request/response ids, message types for the four capabilities, size limits (a 7-day Log as scalars).
- Relay semantics: Child phone offline → immediate "Teléfono sin conexión"; timeouts; one or several Parent phones per account at once.
- What exactly the server stores, field by field (the claim that replaces C1).

## Answer

Human decisions, 2026-10-20 (two grilling rounds).

- **Account:** email + password; the email is only a login name (no verification, no reset mail, Sophiel sends no email). Forgotten password = new account and re-pair. Password hash `Bun.password` (argon2id).
- **Session:** login returns a random token; server keeps only its hash; the Parent phone keeps it in app-private storage. Lasts until logout or account deletion. No password change in M9.
- **The Child phone never logs in.** Pairing replaces login (rewords D76's "login optional").
- **Pairing flow:** behind the door (D38) the Child phone asks the server for a one-time **claim code** (10 min, memory only) and holds its socket; it shows a QR with the claim code + a fresh AES-256 **pairing key**. The logged-in Parent phone scans it and sends the claim code; the server binds it to the account and sends the Child phone a **device token** over the socket. Server URL is fixed in the build, not in the QR.
- **Second Parent phone:** the Child phone's pairing screen has "Mostrar código otra vez" (fresh claim code, same key); any Parent phone of that account scans it and joins the same pairing.
- **Child phone name** lives only on the Parent phone, beside the key, in its local list (pairing id, name, key). The server only checks the account owns the pairing id. A reinstalled Parent phone must scan again.
- **Envelope:** AES-256-GCM (`javax.crypto`), random 96-bit nonce per message, pairing id as associated data; inside: message id + timestamp. The Child phone rejects anything older than 2 min or an id already seen (no replayed Stop).
- **Transport:** Child phone holds a WebSocket (ticket 02). Parent phone sends one HTTP POST per request; the server forwards it down the socket and holds the response until the reply (10 s timeout), or answers "sin conexión" at once if no socket is open.
- **Log fetch:** the whole 7-day Log each time (scalars, gzipped before encrypting); Parent phone holds it in memory only. Cap messages at 256 KB; measure real size on A during the build.
- **Unpair:** from the Parent phone, server deletes the row and sends a plain "desvinculado" notice down the socket; the Child phone deletes key + token, "Vinculado" leaves Status. From the Child phone (behind the door): tells the server, deletes key + token; the next Parent request gets "Este teléfono se desvinculó" and the Parent phone drops the entry and key. Offline either side: a rejected token / pairing id on next contact does the same cleanup.
- **Account housekeeping:** logout (deletes the session) and "Borrar cuenta" (deletes account, sessions, pairings; Child phones clean up on next connect).
- **Server stores, exactly** (the `remote` privacy claim):
  - Disk (SQLite): `accounts(id, email, password_hash, created_at)`, `sessions(token_hash, account_id, created_at)`, `pairings(id, account_id, device_token_hash, created_at)`. Nothing else.
  - Memory only: claim codes, open sockets, requests awaiting a reply.
  - Never: message bodies, Log entries, app lists, Child phone names, keys, last-seen times.
  - Server logs no bodies and no ids; Fly's proxy still sees IP addresses and request timing (a §7 limitation).
