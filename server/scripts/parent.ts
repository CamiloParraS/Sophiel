// A scripted Parent phone for the M9 ticket 18 check against a local relay (debug Child: adb reverse tcp:3000 tcp:3000).
//   bun scripts/parent.ts "<QR text sophiel:1:code:key>" log "rules:com.whatsapp=Bloqueada" stop start
// Signs up a throwaway account, claims the code, then sends each command sealed as the app does (D79) and prints the reply.
import { createCipheriv, createDecipheriv, randomBytes, randomUUID } from "node:crypto";

const RELAY = process.env.RELAY ?? "http://localhost:3000";
const [qr, ...commands] = process.argv.slice(2);
const m = qr?.match(/^sophiel:1:([^:]+):(.+)$/);
if (!m) throw new Error('usage: bun scripts/parent.ts "<QR text>" log|stop|start|rules:pkg=Normal|Bloqueada|De confianza ...');
const [, claimCode, keyText] = m;
const key = Buffer.from(keyText, "base64url");

const post = async (path: string, body: unknown, token?: string) => {
  const r = await fetch(RELAY + path, {
    method: "POST",
    headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(body),
  });
  const json = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(`${path}: ${r.status} ${JSON.stringify(json)}`);
  return json as Record<string, string>;
};

const seal = (pairingId: string, plain: Buffer) => {
  const nonce = randomBytes(12);
  const c = createCipheriv("aes-256-gcm", key, nonce).setAAD(Buffer.from(pairingId));
  return Buffer.concat([nonce, c.update(plain), c.final(), c.getAuthTag()]);
};

const open = (pairingId: string, body: Buffer) => {
  const d = createDecipheriv("aes-256-gcm", key, body.subarray(0, 12)).setAAD(Buffer.from(pairingId));
  d.setAuthTag(body.subarray(body.length - 16));
  const plain = Buffer.concat([d.update(body.subarray(12, body.length - 16)), d.final()]);
  return plain[0] === 0x1f && plain[1] === 0x8b ? Buffer.from(Bun.gunzipSync(plain)) : plain;
};

const { token } = await post("/signup", { email: `script-${randomUUID()}@test`, password: "script-password" });
const { pairingId } = await post("/pairings", { claimCode }, token);
console.log(`paired: ${pairingId}`);

for (const cmd of commands.length ? commands : ["log"]) {
  const [type, rule] = cmd.split(":");
  const req: Record<string, unknown> = { id: randomUUID(), ts: Date.now(), type };
  if (type === "rules") {
    const [pkg, value] = rule.split("=");
    req.rules = { [pkg]: value };
  }
  const t0 = Date.now();
  const res = await fetch(`${RELAY}/pairings/${pairingId}/request`, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: `Bearer ${token}` },
    body: JSON.stringify({ body: seal(pairingId, Buffer.from(JSON.stringify(req))).toString("base64") }),
  });
  if (!res.ok) { console.log(`${cmd}: ${res.status} ${await res.text()}`); continue; }
  const reply = JSON.parse(open(pairingId, Buffer.from(((await res.json()) as { body: string }).body, "base64")).toString());
  const { logCsv, apps, ...rest } = reply;
  console.log(`${cmd} (${Date.now() - t0} ms):`, JSON.stringify(rest));
  if (logCsv !== undefined) console.log(`  log: ${logCsv.split("\n").filter(Boolean).length} rows; apps: ${apps.length}`);
}
