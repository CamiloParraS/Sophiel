import { afterEach, beforeEach, expect, test } from "bun:test";
import { mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createApp } from "../src/app";

let app: ReturnType<typeof createApp>;
let base: string;
let clock = 1_000_000;

beforeEach(() => {
  clock = 1_000_000;
  app = createApp({ dbPath: join(mkdtempSync(join(tmpdir(), "relay-")), "t.db"), requestTimeoutMs: 300, now: () => clock });
  app.listen(0);
  base = `http://localhost:${app.server!.port}`;
});
afterEach(() => app.stop(true));

const post = (path: string, body: unknown, tok?: string, method = "POST") =>
  fetch(base + path, {
    method,
    headers: { "content-type": "application/json", ...(tok ? { authorization: `Bearer ${tok}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

let n = 0;
async function signup(): Promise<string> {
  const r = await post("/signup", { email: `p${n++}@x.com`, password: "password1" });
  return (await r.json()).token;
}

type Child = { ws: WebSocket; frames: any[]; next(t: string): Promise<any>; send(o: object): void };
async function child(): Promise<Child> {
  const ws = new WebSocket(base.replace("http", "ws") + "/child");
  const frames: any[] = [];
  const waiters: (() => void)[] = [];
  ws.onmessage = (e) => {
    frames.push(JSON.parse(e.data));
    waiters.splice(0).forEach((w) => w());
  };
  await new Promise((r) => (ws.onopen = r));
  const next = async (t: string) => {
    for (;;) {
      const i = frames.findIndex((f) => f.t === t);
      if (i >= 0) return frames.splice(i, 1)[0];
      await Promise.race([
        new Promise<void>((r) => waiters.push(r)),
        Bun.sleep(2000).then(() => {
          throw new Error("no " + t);
        }),
      ]);
    }
  };
  return { ws, frames, next, send: (o) => ws.send(JSON.stringify(o)) };
}

async function pair(tok: string) {
  const c = await child();
  c.send({ t: "claim" });
  const { claimCode } = await c.next("code");
  const { pairingId } = await (await post("/pairings", { claimCode }, tok)).json();
  const paired = await c.next("paired");
  return { c, pairingId, deviceToken: paired.deviceToken as string };
}

const closed = (ws: WebSocket) =>
  new Promise<void>((r) => (ws.readyState === WebSocket.CLOSED ? r() : (ws.onclose = () => r())));
const count = (t: string) => (app.db.query(`SELECT count(*) c FROM ${t}`).get() as any).c;

test("schema: only the three tables and their columns", () => {
  const tables = (app.db.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").all() as any[])
    .map((r) => r.name)
    .sort();
  expect(tables).toEqual(["accounts", "pairings", "sessions"]);
  const cols = (t: string) => (app.db.query(`PRAGMA table_info(${t})`).all() as any[]).map((r) => r.name);
  expect(cols("accounts")).toEqual(["id", "email", "password_hash", "created_at"]);
  expect(cols("sessions")).toEqual(["token_hash", "account_id", "created_at"]);
  expect(cols("pairings")).toEqual(["id", "account_id", "device_token_hash", "created_at"]);
});

test("sign-up, login, logout, delete account", async () => {
  const email = "a@x.com";
  expect((await post("/signup", { email, password: "short" })).status).toBe(400);
  const t1 = (await (await post("/signup", { email, password: "password1" })).json()).token;
  expect((await post("/signup", { email, password: "password1" })).status).toBe(409);
  expect((await post("/login", { email, password: "nope-nope" })).status).toBe(401);
  const t2 = (await (await post("/login", { email, password: "password1" })).json()).token;
  expect((await post("/logout", undefined, t2)).status).toBe(200);
  expect((await post("/logout", undefined, t2)).status).toBe(401); // token rejected after logout
  await pair(t1);
  expect((await post("/account", undefined, t1, "DELETE")).status).toBe(200);
  expect((await post("/logout", undefined, t1)).status).toBe(401);
  for (const t of ["sessions", "pairings", "accounts"]) expect(count(t)).toBe(0);
});

test("login: same error for wrong email or password; lockout after 5 failures", async () => {
  await post("/signup", { email: "l@x.com", password: "password1" });
  const wrongPw = await (await post("/login", { email: "l@x.com", password: "wrong-pass" })).text();
  const noUser = await (await post("/login", { email: "nobody@x.com", password: "wrong-pass" })).text();
  expect(wrongPw).toBe(noUser);
  for (let i = 0; i < 4; i++) await post("/login", { email: "l@x.com", password: "wrong-pass" });
  expect((await post("/login", { email: "l@x.com", password: "password1" })).status).toBe(429); // locked even for the right password
  clock += 15 * 60_000 + 1;
  expect((await post("/login", { email: "l@x.com", password: "password1" })).status).toBe(200);
});

test("pairing: claim, paired, then hello accepted", async () => {
  const tok = await signup();
  const { c, deviceToken } = await pair(tok);
  c.ws.close();
  const c2 = await child();
  c2.send({ t: "hello", deviceToken });
  await Bun.sleep(100);
  expect(c2.frames).toEqual([]);
  expect(c2.ws.readyState).toBe(WebSocket.OPEN);
});

test("unpair from the Parent side", async () => {
  const tok = await signup();
  const { c, pairingId } = await pair(tok);
  expect((await post("/pairings/" + pairingId, undefined, tok, "DELETE")).status).toBe(200);
  await c.next("unpaired");
  expect(count("pairings")).toBe(0);
});

test("unpair from the Child side", async () => {
  const tok = await signup();
  const { c, pairingId, deviceToken } = await pair(tok);
  c.send({ t: "hello", deviceToken });
  c.send({ t: "unpair" });
  await closed(c.ws);
  expect(count("pairings")).toBe(0);
  expect((await post(`/pairings/${pairingId}/request`, { body: "x" }, tok)).status).toBe(410);
});

test("unpaired by the Parent while the Child was offline: its next hello is rejected", async () => {
  const tok = await signup();
  const { c, pairingId, deviceToken } = await pair(tok);
  c.ws.close();
  await closed(c.ws);
  expect((await post("/pairings/" + pairingId, undefined, tok, "DELETE")).status).toBe(200);
  const c2 = await child();
  c2.send({ t: "hello", deviceToken });
  await c2.next("unpaired");
  await closed(c2.ws);
});

test("the Child unpairs on a fresh socket after being offline (hello, then unpair)", async () => {
  const tok = await signup();
  const { c, pairingId, deviceToken } = await pair(tok);
  c.ws.close();
  await closed(c.ws);
  const c2 = await child();
  c2.send({ t: "hello", deviceToken });
  c2.send({ t: "unpair" });
  await closed(c2.ws);
  expect((await post(`/pairings/${pairingId}/request`, { body: "x" }, tok)).status).toBe(410);
});

test("a deleted account's device token gets unpaired on hello", async () => {
  const tok = await signup();
  const { c, deviceToken } = await pair(tok);
  c.ws.close();
  await post("/account", undefined, tok, "DELETE");
  const c2 = await child();
  c2.send({ t: "hello", deviceToken });
  await c2.next("unpaired");
  await closed(c2.ws);
});

test("claim code: expired and over-guessed are 404", async () => {
  const tok = await signup();
  const c = await child();
  c.send({ t: "claim" });
  const { claimCode } = await c.next("code");
  clock += 10 * 60_000 + 1;
  expect((await post("/pairings", { claimCode }, tok)).status).toBe(404);

  const tok2 = await signup();
  const d = await child();
  d.send({ t: "claim" });
  const code2 = (await d.next("code")).claimCode;
  for (let i = 0; i < 5; i++) expect((await post("/pairings", { claimCode: "deadbeef00" }, tok2)).status).toBe(404);
  expect((await post("/pairings", { claimCode: code2 }, tok2)).status).toBe(404);
});

test("relay round trip", async () => {
  const tok = await signup();
  const { c, pairingId } = await pair(tok);
  const p = post(`/pairings/${pairingId}/request`, { body: "QUJD" }, tok);
  const req = await c.next("req");
  expect(req.body).toBe("QUJD");
  c.send({ t: "res", rid: req.rid, body: "REVG" });
  expect(await (await p).json()).toEqual({ body: "REVG" });
});

test("offline Child phone gets 503 at once", async () => {
  const tok = await signup();
  const { c, pairingId } = await pair(tok);
  c.ws.close();
  await Bun.sleep(100);
  const t0 = performance.now();
  expect((await post(`/pairings/${pairingId}/request`, { body: "x" }, tok)).status).toBe(503);
  expect(performance.now() - t0).toBeLessThan(150);
});

test("no reply gives 504", async () => {
  const tok = await signup();
  const { pairingId } = await pair(tok);
  expect((await post(`/pairings/${pairingId}/request`, { body: "x" }, tok)).status).toBe(504);
});

test("410 for another account's pairing; 401 bad token; 413 over the cap", async () => {
  const a = await signup();
  const b = await signup();
  const { pairingId } = await pair(a);
  expect((await post(`/pairings/${pairingId}/request`, { body: "x" }, b)).status).toBe(410);
  expect((await post(`/pairings/${pairingId}/request`, { body: "x" }, "bad")).status).toBe(401);
  expect((await post(`/pairings/${pairingId}/request`, { body: "x".repeat(256 * 1024 + 1) }, a)).status).toBe(413);
});

test("Mostrar código otra vez: a paired claim joins the same pairing, for the same account only", async () => {
  const tok = await signup();
  const { c, pairingId, deviceToken } = await pair(tok);
  c.send({ t: "hello", deviceToken });
  // another Parent phone of the account (or the reinstalled one) logs in and scans the fresh code
  const tok2 = (await (await post("/login", { email: `p${n - 1}@x.com`, password: "password1" })).json()).token;
  const claim = await child();
  claim.send({ t: "claim", deviceToken });
  const { claimCode } = await claim.next("code");
  expect((await (await post("/pairings", { claimCode }, tok2)).json()).pairingId).toBe(pairingId);
  expect(await claim.next("paired")).toEqual({ t: "paired", pairingId }); // no new device token
  expect(count("pairings")).toBe(1);
  // the Child socket still answers for the same pairing
  const p = post(`/pairings/${pairingId}/request`, { body: "QQ" }, tok2);
  const req = await c.next("req");
  c.send({ t: "res", rid: req.rid, body: "Ug" });
  expect(await (await p).json()).toEqual({ body: "Ug" });

  // another account cannot take it over
  const other = await signup();
  claim.send({ t: "claim", deviceToken });
  const code2 = (await claim.next("code")).claimCode;
  expect((await post("/pairings", { claimCode: code2 }, other)).status).toBe(404);
  expect(count("pairings")).toBe(1);
});

test("deleting a pairing while a request is pending resolves it as 410; a dropped socket stays 503", async () => {
  const tok = await signup();
  const a = await pair(tok);
  const p = post(`/pairings/${a.pairingId}/request`, { body: "x" }, tok);
  await a.c.next("req");
  expect((await post(`/pairings/${a.pairingId}`, undefined, tok, "DELETE")).status).toBe(200);
  const r = await p;
  expect(r.status).toBe(410);
  expect((await r.json()).error).toBe("Este teléfono se desvinculó");
  const b = await pair(tok);
  const q = post(`/pairings/${b.pairingId}/request`, { body: "x" }, tok);
  await b.c.next("req");
  b.c.ws.close();
  expect((await q).status).toBe(503);
});

test("abuse counters are pruned once their windows pass", async () => {
  await post("/login", { email: "ghost@x.com", password: "wrong-pass" });
  expect(app.counters().ipHits).toBe(1);
  expect(app.counters().failures).toBe(1);
  clock += 15 * 60_000 + 1;
  await post("/login", { email: "ghost2@x.com", password: "wrong-pass" });
  expect(app.counters()).toEqual({ ipHits: 1, failures: 1, wrongClaims: 0 });
});
