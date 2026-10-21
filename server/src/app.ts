// Sophiel relay (D79, D84). Disk: accounts, sessions, pairings. Memory: claim codes, sockets, pending requests, abuse counters.
// Never logs bodies, tokens, emails or ids; never parses a relayed `body`.
import { Database } from "bun:sqlite";
import { createHash, randomBytes, randomUUID } from "node:crypto";
import { Elysia } from "elysia";

export type Options = { dbPath: string; requestTimeoutMs?: number; now?: () => number };

const MAX_BODY = 256 * 1024;
const CLAIM_TTL = 10 * 60_000;
const LOCK_MS = 15 * 60_000;
const MAX_WRONG_CLAIMS = 5;

const sha256 = (s: string) => createHash("sha256").update(s).digest("hex");
const token = () => randomBytes(32).toString("base64url");
const reply = (status: number, error: string) => Response.json({ error }, { status });

type Sock = { id: string; send(data: string): unknown; close(): unknown };
type Pending = { pairingId: string; done: (r: { body?: string; status?: number }) => void };

export function createApp({ dbPath, requestTimeoutMs = 10_000, now = Date.now }: Options) {
  const db = new Database(dbPath, { create: true });
  db.run(`CREATE TABLE IF NOT EXISTS accounts (
    id TEXT PRIMARY KEY, email TEXT NOT NULL UNIQUE, password_hash TEXT NOT NULL, created_at INTEGER NOT NULL)`);
  db.run(`CREATE TABLE IF NOT EXISTS sessions (
    token_hash TEXT PRIMARY KEY, account_id TEXT NOT NULL, created_at INTEGER NOT NULL)`);
  db.run(`CREATE TABLE IF NOT EXISTS pairings (
    id TEXT PRIMARY KEY, account_id TEXT NOT NULL, device_token_hash TEXT NOT NULL, created_at INTEGER NOT NULL)`);

  // memory only
  const claims = new Map<string, { sock: Sock; sockId: string; expires: number }>();
  const socks = new Map<string, Sock>(); // pairing id -> open Child socket
  const byId = new Map<string, { sock: Sock; claim?: string; pairingId?: string }>(); // ws.id -> state
  const pending = new Map<string, Pending>();
  const failures = new Map<string, { times: number[]; lockedUntil: number }>(); // per email
  const ipHits = new Map<string, number[]>();
  const wrongClaims = new Map<string, { n: number; until: number }>(); // per account
  // ponytail: counters never pruned; fine for a small relay, sweep on an interval if memory matters
  const dummyHash = Bun.password.hashSync("dummy-password", "argon2id");

  const accountOf = (req: Request) => {
    const t = req.headers.get("authorization")?.match(/^Bearer (.+)$/)?.[1];
    if (!t) return null;
    const row = db.query("SELECT account_id FROM sessions WHERE token_hash = ?").get(sha256(t)) as { account_id: string } | null;
    return row?.account_id ?? null;
  };

  const ipLimited = (ip: string) => {
    const t = now();
    const hits = (ipHits.get(ip) ?? []).filter((h) => t - h < 60_000);
    hits.push(t);
    ipHits.set(ip, hits);
    return hits.length > 20;
  };

  const newSession = (accountId: string) => {
    const t = token();
    db.run("INSERT INTO sessions VALUES (?, ?, ?)", [sha256(t), accountId, now()]);
    return t;
  };

  const dropSocket = (pairingId: string, frame?: object) => {
    const s = socks.get(pairingId);
    socks.delete(pairingId);
    if (s) {
      if (frame) s.send(JSON.stringify(frame));
      s.close();
    }
    for (const [rid, p] of pending) if (p.pairingId === pairingId) { pending.delete(rid); p.done({ status: 503 }); }
  };

  async function credentials(req: Request, ip: string) {
    if (ipLimited(ip)) return { err: reply(429, "Demasiados intentos") };
    const b = (await req.json().catch(() => null)) as { email?: unknown; password?: unknown } | null;
    if (typeof b?.email !== "string" || typeof b.password !== "string") return { err: reply(400, "Datos no válidos") };
    return { email: b.email.trim().toLowerCase(), password: b.password };
  }

  const ipOf = (server: { requestIP(r: Request): { address: string } | null } | null, req: Request) =>
    server?.requestIP(req)?.address ?? "unknown";

  const app = new Elysia({ serve: { maxRequestBodySize: MAX_BODY * 2 } })
    .post("/signup", async ({ request, server }) => {
      const c = await credentials(request, ipOf(server, request));
      if (c.err) return c.err;
      if (!c.email.includes("@") || c.password.length < 8) return reply(400, "Datos no válidos");
      const id = randomUUID();
      try {
        db.run("INSERT INTO accounts VALUES (?, ?, ?, ?)", [id, c.email, await Bun.password.hash(c.password, "argon2id"), now()]);
      } catch {
        return reply(409, "Ese correo ya tiene cuenta");
      }
      return { token: newSession(id) };
    })
    .post("/login", async ({ request, server }) => {
      const c = await credentials(request, ipOf(server, request));
      if (c.err) return c.err;
      const f = failures.get(c.email) ?? { times: [], lockedUntil: 0 };
      if (now() < f.lockedUntil) return reply(429, "Demasiados intentos");
      const row = db.query("SELECT id, password_hash FROM accounts WHERE email = ?").get(c.email) as { id: string; password_hash: string } | null;
      const ok = await Bun.password.verify(c.password, row?.password_hash ?? dummyHash);
      if (!row || !ok) {
        f.times = f.times.filter((t) => now() - t < LOCK_MS);
        f.times.push(now());
        if (f.times.length >= 5) { f.lockedUntil = now() + LOCK_MS; f.times = []; }
        failures.set(c.email, f);
        return reply(401, "Correo o contraseña incorrectos");
      }
      failures.delete(c.email);
      return { token: newSession(row.id) };
    })
    .post("/logout", ({ request }) => {
      const t = request.headers.get("authorization")?.match(/^Bearer (.+)$/)?.[1];
      if (!t || !accountOf(request)) return reply(401, "Sesión no válida");
      db.run("DELETE FROM sessions WHERE token_hash = ?", [sha256(t)]);
      return { ok: true };
    })
    .delete("/account", ({ request }) => {
      const acc = accountOf(request);
      if (!acc) return reply(401, "Sesión no válida");
      const ids = (db.query("SELECT id FROM pairings WHERE account_id = ?").all(acc) as { id: string }[]).map((r) => r.id);
      db.transaction(() => {
        db.run("DELETE FROM pairings WHERE account_id = ?", [acc]);
        db.run("DELETE FROM sessions WHERE account_id = ?", [acc]);
        db.run("DELETE FROM accounts WHERE id = ?", [acc]);
      })();
      for (const id of ids) dropSocket(id, { t: "unpaired" });
      return { ok: true };
    })
    .post("/pairings", async ({ request }) => {
      const acc = accountOf(request);
      if (!acc) return reply(401, "Sesión no válida");
      const w = wrongClaims.get(acc);
      if (w && w.n >= MAX_WRONG_CLAIMS && now() < w.until) return reply(404, "Código no válido");
      const b = (await request.json().catch(() => null)) as { claimCode?: unknown } | null;
      const claim = typeof b?.claimCode === "string" ? claims.get(b.claimCode) : undefined;
      if (!claim || now() > claim.expires) {
        if (claim) claims.delete(b!.claimCode as string);
        const fresh = w && now() < w.until ? w : { n: 0, until: now() + CLAIM_TTL };
        fresh.n++;
        wrongClaims.set(acc, fresh);
        return reply(404, "Código no válido");
      }
      claims.delete(b!.claimCode as string);
      const pairingId = randomUUID();
      const deviceToken = token();
      db.run("INSERT INTO pairings VALUES (?, ?, ?, ?)", [pairingId, acc, sha256(deviceToken), now()]);
      const st = byId.get(claim.sockId);
      if (st) { st.claim = undefined; st.pairingId = pairingId; }
      socks.set(pairingId, claim.sock);
      claim.sock.send(JSON.stringify({ t: "paired", pairingId, deviceToken }));
      return { pairingId };
    })
    .delete("/pairings/:id", ({ request, params }) => {
      const acc = accountOf(request);
      if (!acc) return reply(401, "Sesión no válida");
      const r = db.run("DELETE FROM pairings WHERE id = ? AND account_id = ?", [params.id, acc]);
      if (!r.changes) return reply(410, "Este teléfono se desvinculó");
      dropSocket(params.id, { t: "unpaired" });
      return { ok: true };
    })
    .post("/pairings/:id/request", async ({ request, params }) => {
      const acc = accountOf(request);
      if (!acc) return reply(401, "Sesión no válida");
      const raw = await request.text();
      if (raw.length > MAX_BODY) return reply(413, "Mensaje demasiado grande");
      const own = db.query("SELECT 1 FROM pairings WHERE id = ? AND account_id = ?").get(params.id, acc);
      if (!own) return reply(410, "Este teléfono se desvinculó");
      const sock = socks.get(params.id);
      if (!sock) return reply(503, "Sin conexión");
      const body = (JSON.parse(raw || "{}") as { body?: unknown }).body; // envelope only; the body itself stays opaque
      if (typeof body !== "string") return reply(400, "Datos no válidos");
      const rid = token();
      const result = await new Promise<{ body?: string; status?: number }>((done) => {
        const timer = setTimeout(() => { pending.delete(rid); done({ status: 504 }); }, requestTimeoutMs);
        pending.set(rid, { pairingId: params.id, done: (r) => { clearTimeout(timer); done(r); } });
        sock.send(JSON.stringify({ t: "req", rid, body }));
      });
      if (result.status) return reply(result.status, result.status === 504 ? "El teléfono no respondió" : "Sin conexión");
      return { body: result.body };
    })
    .ws("/child", {
      idleTimeout: 25,
      close(ws) {
        const st = byId.get(ws.id);
        byId.delete(ws.id);
        if (st?.claim) claims.delete(st.claim);
        if (st?.pairingId && socks.get(st.pairingId)?.id === st.sock.id) dropSocket(st.pairingId);
      },
      message(ws, raw) {
        const m = (typeof raw === "string" ? JSON.parse(raw) : raw) as { t?: string; deviceToken?: string; rid?: string; body?: string };
        const st = byId.get(ws.id) ?? { sock: ws as Sock };
        st.sock = ws as Sock;
        byId.set(ws.id, st);
        if (m.t === "claim") {
          if (st.claim) claims.delete(st.claim);
          st.claim = randomBytes(5).toString("hex"); // 40 bits
          claims.set(st.claim, { sock: ws as Sock, sockId: ws.id, expires: now() + CLAIM_TTL });
          ws.send(JSON.stringify({ t: "code", claimCode: st.claim }));
        } else if (m.t === "hello") {
          const row = typeof m.deviceToken === "string"
            ? (db.query("SELECT id FROM pairings WHERE device_token_hash = ?").get(sha256(m.deviceToken)) as { id: string } | null)
            : null;
          if (!row) { ws.send(JSON.stringify({ t: "unpaired" })); ws.close(); return; }
          if (st.pairingId && st.pairingId !== row.id) socks.delete(st.pairingId);
          const old = socks.get(row.id);
          if (old && old.id !== st.sock.id) old.close();
          st.pairingId = row.id;
          socks.set(row.id, st.sock);
        } else if (m.t === "res" && typeof m.rid === "string") {
          const p = pending.get(m.rid);
          if (p && p.pairingId === st.pairingId && typeof m.body === "string") { pending.delete(m.rid); p.done({ body: m.body }); }
        } else if (m.t === "unpair" && st.pairingId) {
          db.run("DELETE FROM pairings WHERE id = ?", [st.pairingId]);
          const id = st.pairingId;
          st.pairingId = undefined;
          dropSocket(id);
        }
      },
    });

  return Object.assign(app, { db });
}
