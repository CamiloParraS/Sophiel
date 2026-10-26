# PROTOTYPE builder for M9 ticket 05: the Parent phone's screens. Throwaway.
# Reuses Design A's CSS, sprite and script from .scratch/sophiel-a-screens.html.
import re, pathlib
root = pathlib.Path(r"C:\TETO\Projects\pixelshield")
src = (root / ".scratch/sophiel-a-screens.html").read_text(encoding="utf-8")
head = src[:src.index('<div class="wrap">')].replace("<title>Sophiel A screens</title>", "<title>Parent phone prototype</title>")
script = src[src.rindex("<script>"):]

EXTRA = """<style>
.af .fld{height:56px;border-radius:18px;background:#fff;box-shadow:0 1px 2px var(--line);display:flex;align-items:center;padding:0 16px;font-size:16px;color:var(--fg2);flex:none}
.af .fld.f{color:var(--ink);box-shadow:inset 0 0 0 2px var(--ink)}
.af .fld.bad{box-shadow:inset 0 0 0 2px var(--red);color:var(--ink)}
.af .err{font-size:13.5px;font-weight:600;color:var(--red);padding:0 8px}
.af .hint{font-size:13px;color:var(--fg2);padding:0 8px;line-height:18px}
.af .role{border-radius:24px;background:#fff;box-shadow:0 1px 2px var(--line);padding:16px;display:flex;gap:14px;align-items:flex-start;flex:none}
.af .role.on{box-shadow:inset 0 0 0 2px var(--ink)}
.af .role b{display:block;font-size:18px;font-weight:750;letter-spacing:-.02em}
.af .role small{display:block;font-size:13.5px;line-height:18px;color:var(--fg2);margin-top:3px}
.af .dot{width:9px;height:9px;border-radius:50%;background:var(--sig);display:inline-block;margin-right:5px;vertical-align:1px}
.af .dot.off{background:color-mix(in srgb,var(--ink) 30%,transparent)}
.af .dot.red{background:var(--red)}.af .dot.amb{background:#C77700}
.af .fresh{display:flex;align-items:center;justify-content:center;gap:6px;font-size:12.5px;color:var(--fg2);padding:2px 0}
.af .banner{display:flex;gap:10px;align-items:flex-start;border-radius:20px;padding:12px 14px;font-size:14px;line-height:19px;flex:none}
.af .banner b{display:block;font-size:15px}
.af .banner.red{background:var(--redbg);color:#6d1a20}
.af .banner.amb{background:var(--ambbg);color:#5a3200}
.af .banner.ink{background:var(--ink);color:var(--on)}
.af .banner .ic{width:20px;height:20px;flex:none;margin-top:1px}
.af .skel{border-radius:10px;background:color-mix(in srgb,var(--ink) 8%,transparent);height:14px;animation:pl 1.4s ease-in-out infinite}
@keyframes pl{50%{opacity:.45}}
.af .view{position:relative;height:330px;border-radius:28px;background:#1b1e1d;overflow:hidden;flex:none}
.af .view .frm{position:absolute;left:50%;top:50%;width:190px;height:190px;transform:translate(-50%,-55%);border-radius:28px;box-shadow:0 0 0 999px #0008,inset 0 0 0 3px #fff}
.af .view .qr{position:absolute;left:50%;top:50%;width:150px;height:150px;transform:translate(-50%,-62%);background:repeating-conic-gradient(#ddd 0 25%,#333 0 50%) 0 0/18px 18px;opacity:.5;border-radius:6px}
.af .view p{position:absolute;bottom:14px;left:0;right:0;text-align:center;color:#fff;font-size:14px;margin:0}
.af .mini-hero{border-radius:24px;background:var(--ink);color:var(--on);padding:14px 16px;position:relative;overflow:hidden;flex:none}
.af .mini-hero.off{--ink:#2A2115;--sub:#E8D3B5}
.af .mini-hero.bad{--ink:#2A1517;--sub:#E5BDBD}
.af .mini-hero.gone{background:#fff;color:var(--ink);box-shadow:0 1px 2px var(--line)}
.af .mini-hero .nm{font-size:13.5px;font-weight:650;color:var(--sub);display:flex;justify-content:space-between}
.af .mini-hero.gone .nm{color:var(--fg2)}
.af .mini-hero .st{font-size:30px;font-weight:800;letter-spacing:-.035em;line-height:32px;margin-top:6px}
.af .mini-hero small{display:block;font-size:13px;color:var(--sub);margin-top:2px}
.af .mini-hero.gone small{color:var(--fg2)}
.af .mini-hero .rowb{display:flex;gap:8px;margin-top:12px}
.af .mini-hero .pill{height:40px;border-radius:14px;padding:0 14px;display:flex;align-items:center;gap:6px;font-weight:700;font-size:14px;background:var(--on);color:var(--ink)}
.af .mini-hero .pill.line{background:none;color:var(--on);box-shadow:inset 0 0 0 1.5px color-mix(in srgb,var(--on) 50%,transparent)}
.af .mini-hero .pill .ic{width:16px;height:16px}
.af .spin{width:20px;height:20px;border-radius:50%;border:2.5px solid currentColor;border-right-color:transparent;animation:sp 0.9s linear infinite;flex:none}
@keyframes sp{to{transform:rotate(360deg)}}
.af .btn.dis{opacity:.4}
.vbar{position:fixed;left:50%;bottom:18px;transform:translateX(-50%);z-index:99;display:flex;align-items:center;gap:6px;background:#111;color:#fff;border-radius:999px;padding:6px;font:600 14px Geist,system-ui;box-shadow:0 8px 24px #0005}
.vbar button{width:38px;height:38px;border-radius:50%;border:0;background:#2a2a2a;color:#fff;font-size:18px;cursor:pointer}
.vbar span{padding:0 10px;min-width:220px;text-align:center}
.vsec{display:none}.vsec.on{display:grid}
.stamp{display:inline-block;background:#B8323A;color:#fff;font:700 12px Geist;padding:3px 10px;border-radius:999px;letter-spacing:.04em}
</style>
"""

SB = '<div class="sb"><span>9:41</span><span>▴ ▮</span></div>'
def ic(n, s=""): return f'<svg class="ic" style="{s}"><use href="#{n}"/></svg>'
def bar(title, back=True, right="", sub=""):
    b = f'<span class="rb">{ic("back")}</span>' if back else ""
    s = f"<small>{sub}</small>" if sub else ""
    return f'<div class="bar">{b}<h1>{title}{s}</h1>{right}</div>'
def phone(inner, cap, cls=""):
    return f'<div class="ph"><div class="phone af {cls}"><div class="scr">{SB}{inner}</div></div><div class="cap">{cap}</div></div>'
def section(title, desc, phones, cls="", vid=""):
    v = f' data-v="{vid}"' if vid else ""
    return f'<section class="row {cls}"{v} style="grid-template-columns:1fr"><div class="desc"><h2>{title}</h2><p>{desc}</p></div><div class="phones">{"".join(phones)}</div></section>'
ACCT = f'<span class="rb">{ic("gear")}</span>'

def r(icon, t, s="", v="", cls=""):
    i = f'<span class="i">{ic(icon)}</span>' if icon else ""
    sm = f"<small>{s}</small>" if s else ""
    return f'<div class="r {cls}">{i}<div class="t"><b>{t}</b>{sm}</div>{v}</div>'
def child_row(name, state, dot, extra=""):
    return f'<div class="r"><span class="ai" style="background:var(--ink)">{name[0]}</span><div class="t"><b>{name}</b><small><span class="dot {dot}"></span>{state}</small></div>{extra}{ic("chev")}</div>'

APPS = [("Roblox","#4C5560","blk"),("TikTok","#1F1F24","blk"),("Firefox","#E07A1F","trust"),("Chrome","#3B6FD8",""),("Instagram","#C2407F",""),("Netflix","#9E1C24",""),("Spotify","#27A35A","")]
def app_rows(letters_only=True):
    out = []
    for n, c, k in APPS:
        bg = "var(--ink)" if letters_only else c
        tag = {"blk": f'<span class="tg blk">{ic("block")}Bloqueada</span>', "trust": '<span class="tg line">De confianza</span>'}.get(k, '<span class="v" style="color:var(--fg2);font-weight:500">Normal</span>')
        out.append(f'<div class="r" style="min-height:58px"><span class="ai" style="background:{bg}">{n[0]}</span><div class="t"><b>{n}</b></div>{tag}{ic("chev")}</div>')
    return '<div class="card">' + "".join(out) + "</div>"

EVENTS = f'''<div class="dayh"><b>Hoy</b><span>2 tapados, casi todo en Chrome</span></div><div class="card">
<div class="ev"><time>14:32</time><span class="eb cam"></span><div><b>Chrome</b><small>2 zonas tapadas</small></div></div>
<div class="ev"><time>12:40</time><span class="eb red">{ic("shieldoff")}</span><div><b class="red">Sin protección</b><small>La accesibilidad está apagada · 12 min</small></div></div>
<div class="ev"><time>11:20</time><span class="eb blk">{ic("block")}</span><div><b>Apertura bloqueada</b><small>TikTok · la bloqueaste tú</small></div></div>
<div class="ev"><time>09:12</time><span class="eb on">{ic("power")}</span><div><b>Protección activada</b><small>Iniciada a distancia</small></div></div></div>'''

def week_hero(n=6, h=210):
    return f'''<div class="ink fade lh" style="height:{h}px" data-wall data-page="off" data-covered="{n}"><div class="wall"></div><div class="mid"><div class="row"><div style="flex:1"><div class="n" data-q>{n}</div><div class="l" data-q>cubiertas en 7 días</div></div><div data-q><div class="bars"><span><i style="height:6px"></i>L</span><span><i class="h" style="height:17px"></i>M</span><span><i style="height:6px"></i>M</span><span><i class="h" style="height:39px"></i>J</span><span><i style="height:6px"></i>V</span><span><i style="height:6px"></i>S</span><span class="td"><i class="h" style="height:28px"></i>D</span></div></div></div></div></div>'''

def status_card(head, sub, cls="", page="ok", covered=4, h=270, line=""):
    lk = '<div class="lock cookie" data-cookie="9"><svg class="ic" style="width:30px;height:30px"><use href="#lock"/></svg></div>' if page == "ok" else ""
    ln = f'<div class="mode" data-q>{line}</div>' if line else ""
    return f'<div class="hero {cls}" style="height:{h}px;flex:none" data-wall data-page="{page}" data-covered="{covered}"><div class="wall"></div>{lk}<div class="mid"><div class="mode" data-q style="margin-top:0;color:var(--on);font-weight:650">Teléfono de Sofi</div><h3 data-q>{head}</h3><p data-q>{sub}</p>{ln}</div></div>'

# ---------- shared flow ----------
flow = []
def acc_hero(head, sub, h=250, badge="key", bad=False, line=""):
    b = (f'<div class="cb red" data-cookie="0" data-q style="width:64px;height:64px">{ic("x","width:26px;height:26px")}</div>' if bad
         else f'<div class="cb" data-cookie="9" data-q style="width:64px;height:64px">{ic(badge,"width:26px;height:26px")}</div>')
    ln = f'<div class="mode" data-q style="display:block;margin-top:14px;max-width:260px">{ic("shieldok","display:inline-block;vertical-align:-3px;margin-right:5px")}{line}</div>' if line else ""
    return (f'<div class="ink fade" style="min-height:{h}px;display:flex" data-wall data-page="off" data-covered="0"><div class="wall"></div>'
            f'<div class="mid" style="position:relative;flex:1;align-items:center;justify-content:center;text-align:center;padding:22px 22px 20px">{b}'
            f'<h3 data-q class="s" style="margin:14px 0 0;max-width:280px">{head}</h3><p data-q style="margin-top:8px">{sub}</p>{ln}</div></div>')
TRUST = "Solo guarda tu correo y qué teléfonos vinculaste."
def role(head, sub, icon, on):
    if on:
        return f'<div class="ink fade" style="height:232px" data-wall data-page="off" data-covered="5"><div class="wall"></div><div class="cb" data-cookie="9" style="position:absolute;right:14px;top:14px;width:56px;height:56px;z-index:2">{ic("check","width:24px;height:24px")}</div><div class="mid"><h3 data-q class="s">{head}</h3><p data-q>{sub}</p></div></div>'
    return f'<div class="tile" style="min-height:150px;padding:16px 18px 18px"><div class="cb inkf" data-cookie="0" style="width:44px;height:44px">{ic(icon,"width:20px;height:20px")}</div><b style="font-size:30px;line-height:32px;font-weight:800;letter-spacing:-.035em;margin-top:auto">{head}</b><small style="font-size:14px;line-height:19px;margin-top:4px">{sub}</small></div>'
flow.append(phone(f'''<div style="padding:14px 16px 14px"><h1 style="margin:0;font-size:30px;line-height:32px;font-weight:800;letter-spacing:-.035em;text-wrap:balance">¿De quién es este teléfono?</h1><small style="display:block;font-size:14px;color:var(--fg2);margin-top:6px">Puedes cambiarlo más tarde.</small></div><div class="body">
{role("Del Hijo","Sophiel protege este teléfono. Funciona sin cuenta.","shieldok",True)}
{role("Del Padre","Ves y controlas los teléfonos de tus hijos. Necesita una cuenta.","key",False)}
</div><div class="act"><div class="btn go">Continuar</div></div>''', "First launch, before the wizard. The chosen role becomes the state card (wall, display type, a check badge); the other waits as a white tile. Hijo is the default. Padre skips the protection wizard and goes to login."))
flow.append(phone(f'''<div class="body" style="justify-content:center;gap:10px">{acc_hero("Teléfono del Padre","Entra para ver y controlar los teléfonos de tus hijos.", line=TRUST)}
<div class="fld f">ana@correo.com</div><div class="fld">Contraseña</div><div class="btn go">Entrar</div><div class="lk">Crear una cuenta</div>
</div><div class="act"><div class="lk" style="font-weight:500">Este teléfono es del Hijo</div></div>''', "Login, centred: one stack of card, fields and button in the middle of the screen; the card centres its badge, headline and privacy line. No 'forgot password' (none in M9). The last link goes back to the Child role, allowed only here."))
flow.append(phone(f'''{bar("Crear cuenta")}<div class="body" style="justify-content:center;gap:10px">{acc_hero("Una cuenta, todos tus hijos","Con ella vinculas cada teléfono del Hijo.", h=200, badge="key")}
<div class="fld f">ana@correo.com</div><div class="fld bad">••••••</div><div class="err">Usa al menos 8 caracteres.</div><div class="fld">Repite la contraseña</div>
<div class="banner amb">{ic("warn")}<div><b>Guárdala bien</b>Si la olvidas no se puede recuperar: tendrás que crear otra cuenta y vincular los teléfonos otra vez.</div></div>
<div class="btn go">Crear cuenta</div></div>''', "Sign-up, centred the same way: a shorter card, the fields, the warning and the button as one stack. The no-recovery truth stays amber, once, right before the button."))
flow.append(phone(f'''<div class="body" style="justify-content:center;gap:10px">{acc_hero("No pudiste entrar","Correo o contraseña incorrectos. Demasiados intentos bloquean la entrada unos minutos.", bad=True)}
<div class="fld f">ana@correo.com</div><div class="fld bad">••••••••</div><div class="btn go dis"><span class="spin"></span>Entrando…</div><div class="lk">Crear una cuenta</div>
</div><div class="act"><div class="lk" style="font-weight:500">Este teléfono es del Hijo</div></div>''', "Wrong credentials: the card itself says it, its badge goes red and square like a wrong PIN (one message for both, no account enumeration). Rate-limit wording depends on ticket 09. Button: in-flight state."))
flow.append(phone(f'''{bar("Tus hijos", back=False, right=ACCT)}<div class="body">
<div class="ink fade" style="height:430px" data-wall data-page="off" data-covered="0"><div class="wall"></div><div class="cb" data-cookie="9" style="position:absolute;right:16px;top:16px;width:72px;height:72px;z-index:2">{ic("layers","width:28px;height:28px")}</div>
<div class="mid"><h3 data-q>Aún no hay teléfonos</h3><p data-q>En el teléfono del Hijo: abre Sophiel, entra con el PIN y ve a Ajustes &gt; Vincular con el Padre. Luego escanea su código aquí.</p></div></div>
</div><div class="act"><div class="btn go">{ic("search")}Escanear código</div></div>''', "Empty list after login (also after a reinstall: the list lives on this phone only). The whole page is the state card; the lit tiles are the phones still to come. The steps sit where the promise usually does."))
flow.append(phone(f'''{bar("Escanear")}<div class="body"><div class="view"><div class="qr"></div><div class="frm"></div><p>Apunta al código del teléfono del Hijo</p></div>
<div class="hint" style="text-align:center">Lo abre el lector de Google. Sophiel no usa la cámara.</div></div>''', "Scan, via the Google code scanner (ticket 03). The disclosure line matters: the scanner is Google's, and the camera permission is not Sophiel's."))
flow.append(phone(f'''{bar("Ponle nombre")}<div class="body" style="padding-top:8px">
<div class="banner ink">{ic("check")}<div><b>Teléfono vinculado</b>Ya pueden hablar entre ellos, cifrado de punta a punta.</div></div>
<div class="fld f">Teléfono de Sofi</div><div class="hint">Solo se ve en este teléfono.</div>
</div><div class="act"><div class="btn go">Guardar</div></div>''', "After a good scan: name it. The name stays on the Parent phone (the server only knows the pairing)."))
flow.append(phone(f'''{bar("Escanear")}<div class="body"><div class="view"><div class="qr"></div><div class="frm" style="box-shadow:0 0 0 999px #0008,inset 0 0 0 3px #FF8A8F"></div><p>Este código ya no sirve</p></div>
<div class="banner red">{ic("x")}<div><b>El código caducó</b>En el teléfono del Hijo toca “Mostrar código otra vez”.</div></div></div>''', "Bad scan: expired or used claim code, or not a Sophiel code. One message names the fix on the other phone."))
flow.append(phone(f'''{bar("Tus hijos", back=False, right=ACCT)}<div class="body"></div>
<div class="scrim"></div><div class="sheet"><div class="handle"></div><div class="sh-t"><h2>Cuenta</h2></div>
<div class="ink" style="padding:16px 18px;display:flex;align-items:center;gap:14px"><div class="cb" data-cookie="9" style="width:52px;height:52px">{ic("key","width:22px;height:22px")}</div><div style="min-width:0"><b style="display:block;font-size:20px;font-weight:800;letter-spacing:-.025em">ana@correo.com</b><small style="display:block;font-size:13.5px;color:var(--sub)">Teléfono del Padre · 3 vinculados</small></div></div>
<div class="card">{r("x","Cerrar sesión","Los teléfonos siguen vinculados", ic("chev"))}</div>
<div class="card"><div class="r"><span class="i" style="background:var(--redbg);color:var(--red)">{ic("trash")}</span><div class="t"><b style="color:var(--red)">Borrar cuenta</b><small>Desvincula todos los teléfonos y borra la cuenta</small></div>{ic("chev")}</div></div>
<div class="hint">Para usar este teléfono como teléfono del Hijo, cierra sesión.</div></div>''', "The gear opens Cuenta: who you are as a small ink card, then logout, then Borrar cuenta in red. Role change back to Child = log out, then the login screen's last link."))

# ---------- variant A: Espejo ----------
A = []
A.append(phone(f'''{bar("Tus hijos", back=False, right=ACCT)}<div class="body"><div class="card">
{child_row("Teléfono de Sofi","Conectado","")}{child_row("Tablet de Leo","Sin conexión","off")}{child_row("Teléfono de Mateo","Este teléfono se desvinculó","red")}</div>
<div class="fresh">Desliza hacia abajo para comprobar otra vez</div></div><div class="act"><div class="btn sm">{ic("search")}Vincular otro teléfono</div></div>''',
"Home: a list. 'Conectado' is only 'its socket is up' (the server knows that much). Nothing about protection until you open one."))
A.append(phone(f'''{bar("Teléfono de Sofi")}<div class="body">{status_card("Protegido","Le tapamos lo explícito antes de que lo vea.", line="Hace 3 s")}
<div class="bento" style="grid-template-columns:1fr 1fr"><div class="tile week" style="grid-row:auto;min-height:150px"><div class="hd">Historial{ic("chev")}</div><div class="n">6</div><div class="l">2 hoy</div></div>
<div class="tile apps"><div class="hd">Apps{ic("chev")}</div><div class="row2"><div class="cookie" data-cookie="8"><span>2</span></div><div><b>bloqueadas</b></div></div></div></div></div>
<div class="act"><div class="btn">{ic("stop")}Detener protección</div></div>''',
"Per Child phone: the Child's own Status, mirrored. Opening it fetches the Log; the card is built from the newest entry, stamped 'Hace 3 s'. One action, as on the Child."))
A.append(phone(f'''{bar("Teléfono de Sofi")}<div class="body">{status_card("Se detuvo","La detuviste hace 5 min.", cls="", page="off", covered=0)}
<div class="bento" style="grid-template-columns:1fr 1fr"><div class="tile week" style="grid-row:auto;min-height:150px;background:#fff;color:var(--ink)"><div class="hd">Historial{ic("chev")}</div><div class="n">6</div><div class="l">2 hoy</div></div>
<div class="tile apps"><div class="hd">Apps{ic("chev")}</div><div class="row2"><div class="cookie" data-cookie="8"><span>2</span></div><div><b>bloqueadas</b></div></div></div></div>
<div class="banner red">{ic("x")}<div><b>No se pudo iniciar</b>El servicio de accesibilidad está apagado en ese teléfono. Hay que encenderlo allí.</div></div></div>
<div class="act"><div class="btn go">{ic("play")}Iniciar protección</div></div>''', "Start failed: the reason comes back from the Child phone and sits right above the button that caused it."))
A.append(phone(f'''{bar("Teléfono de Sofi")}<div class="body"><div class="hero" style="height:270px;flex:none;background:#fff;box-shadow:0 1px 2px var(--line)"><div class="mid" style="color:var(--ink)"><div class="skel" style="width:40%"></div><div class="skel" style="width:70%;height:40px;margin-top:auto"></div><div class="skel" style="width:85%;margin-top:10px"></div></div></div>
<div class="fresh"><span class="spin" style="width:14px;height:14px;border-width:2px"></span>Preguntando al teléfono…</div></div><div class="act"><div class="btn dis">Esperando respuesta</div></div>''', "Loading: up to the relay's 10 s. The action is disabled until the state is known."))
A.append(phone(f'''{bar("Tablet de Leo")}<div class="body"><div class="hero" style="height:270px;flex:none;background:#fff;box-shadow:0 1px 2px var(--line)"><div class="mid" style="color:var(--ink)"><h3 style="font-size:40px;line-height:42px">Sin conexión</h3><p style="color:var(--fg2)">La tablet está apagada, sin internet o con Sophiel detenido. Inténtalo luego.</p></div></div></div>
<div class="act"><div class="btn sm">Intentar otra vez</div></div>''', "Offline: the card says what we cannot know. No last-known state is shown (nothing is stored, pure relay)."))

# ---------- variant B: Tarjetas ----------
def mh(name, st, sub, cls="", btns=""):
    return f'<div class="mini-hero {cls}"><div class="nm"><span>{name}</span>{ic("chev","width:16px;height:16px")}</div><div class="st">{st}</div><small>{sub}</small>{btns}</div>'
STOPB = f'<div class="rowb"><span class="pill line">{ic("stop")}Detener</span></div>'
STARTB = f'<div class="rowb"><span class="pill">{ic("play")}Iniciar</span></div>'
B = []
B.append(phone(f'''{bar("Tus hijos", back=False, right=ACCT)}<div class="body">
{mh("Teléfono de Sofi","Protegido","6 tapados en 7 días · hace 3 s","",STOPB)}
{mh("Tablet de Leo","Se detuvo","Lo detuvo el Padre · hace 3 s","off",STARTB)}
{mh("Teléfono de Mateo","Sin conexión","No responde","gone",'<div class="rowb"><span class="pill" style="background:var(--wash);color:var(--ink)">Reintentar</span></div>')}
</div><div class="act"><div class="lk">{ic("search")}&nbsp;Vincular otro teléfono</div></div>''', "Home: one small state card per Child phone, all asked at once on open. Start/Stop sits on the card: one tap, no screen in between."))
B.append(phone(f'''{bar("Teléfono de Sofi", sub="Protegido · hace 3 s")}<div class="body"><div class="seg"><span class="on">Historial</span><span>Apps</span></div>{week_hero(6,190)}{EVENTS}</div>''', "Tap a card: two tabs, Historial and Apps. Historial is the Child's Historial with the same rows."))
B.append(phone(f'''{bar("Teléfono de Sofi", sub="Protegido · hace 3 s")}<div class="body"><div class="seg"><span>Historial</span><span class="on">Apps</span></div>{app_rows()}</div>''', "The Apps tab: D58's rows; tapping a row opens the same rule sheet. Letter tiles from the label: no icons cross the wire."))
B.append(phone(f'''{bar("Tus hijos", back=False, right=ACCT)}<div class="body">
{mh("Teléfono de Sofi","Protegido","6 tapados en 7 días · hace 3 s","",STOPB)}
<div class="mini-hero off"><div class="nm"><span>Tablet de Leo</span></div><div class="st">Se detuvo</div><small>Lo detuvo el Padre · hace 3 s</small><div class="rowb"><span class="pill"><span class="spin" style="width:14px;height:14px;border-width:2px"></span>Iniciando…</span></div></div>
<div class="banner red">{ic("x")}<div><b>Tablet de Leo: no se pudo iniciar</b>El servicio de accesibilidad está apagado allí.</div></div>
</div>''', "In-flight and failure, on the home: the pill spins in place; a failure drops a banner under the card."))
B.append(phone(f'''{bar("Tus hijos", back=False, right=ACCT)}<div class="body">
<div class="mini-hero gone"><div class="nm"><span>Teléfono de Sofi</span></div><div class="skel" style="width:60%;height:28px;margin-top:8px"></div><div class="skel" style="width:80%;margin-top:8px"></div></div>
{mh("Teléfono de Mateo","Se desvinculó","Lo desvincularon en ese teléfono","gone",'<div class="rowb"><span class="pill" style="background:var(--wash);color:var(--ink)">Quitar de la lista</span></div>')}
</div>''', "Loading (skeleton card) and a pairing removed from the Child side."))

# ---------- variant C: Pedir ----------
C = []
C.append(phone(f'''{bar("Tus hijos", back=False, right=ACCT)}<div class="body"><div class="card">
{child_row("Teléfono de Sofi","Conectado","")}{child_row("Tablet de Leo","Sin conexión","off")}</div></div><div class="act"><div class="btn sm">{ic("search")}Vincular otro teléfono</div></div>''', "Home: the same list as A."))
C.append(phone(f'''{bar("Teléfono de Sofi", sub="Conectado")}<div class="body">
<div class="gt">Ver</div><div class="card">{r("list","Historial","Pide los últimos 7 días", ic("chev"))}{r("apps","Apps","Pide la lista y cambia reglas", ic("chev"))}</div>
<div class="gt">Protección</div><div class="duo"><div class="tile" style="min-height:120px"><div class="hd">Iniciar{ic("play")}</div><b style="margin-top:auto;font-size:15px">Solo con accesibilidad</b></div><div class="tile" style="min-height:120px"><div class="hd">Detener{ic("stop")}</div><b style="margin-top:auto;font-size:15px">Como el PIN</b></div></div>
<div class="gt">Este teléfono</div><div class="card">{r("label","Cambiar nombre","", ic("chev"))}{r("x","Desvincular","", ic("chev"), "")}</div></div>''',
"Per Child phone: a menu of requests. No state is guessed: both Iniciar and Detener are always there, each a request that comes back with a result."))
C.append(phone(f'''{bar("Teléfono de Sofi", sub="Conectado")}<div class="body">
<div class="gt">Protección</div><div class="duo"><div class="tile" style="min-height:120px;box-shadow:inset 0 0 0 2px var(--ink)"><div class="hd">Iniciar<span class="spin" style="width:16px;height:16px;border-width:2px"></span></div><b style="margin-top:auto;font-size:15px">Pidiendo…</b></div><div class="tile" style="min-height:120px;opacity:.4"><div class="hd">Detener{ic("stop")}</div></div></div>
<div class="banner ink">{ic("check")}<div><b>Ya estaba protegido</b>No hizo falta iniciar nada. Hace 2 s.</div></div></div>''', "The answer is the state: 'Ya estaba protegido', 'Protección iniciada', or the failure reason."))
C.append(phone(f'''{bar("Historial", sub="Teléfono de Sofi · hace 3 s")}<div class="body">{week_hero(6,190)}{EVENTS}</div>''', "Historial: fetched once on open; pull to ask again. Same rows as the Child."))
C.append(phone(f'''{bar("Apps", sub="Teléfono de Sofi · hace 3 s")}<div class="body"><div class="banner amb">{ic("warn")}<div><b>No se guardó</b>El teléfono se desconectó. Roblox sigue como estaba.</div></div>{app_rows()}</div>''', "Apps with a failed rule change: the row reverts and says so."))

intro = f'''<div class="intro"><span class="stamp">PROTOTYPE · throwaway</span>
<h1>The Parent phone</h1>
<p>M9 ticket 05. Everything above the switcher is the shared flow: role choice, account, pairing. The <b>per-Child-phone screens</b> come in three structurally different variants; flip with the bar at the bottom, ← → keys, or <code>?variant=A|B|C</code>.</p>
<p><b>A · Espejo</b>: the Child's own Status, mirrored after a fetch. <b>B · Tarjetas</b>: every Child on one page as small state cards with Start/Stop on them. <b>C · Pedir</b>: no guessed state, a menu of requests that each come back with an answer.</p>
<p>The open question under all three: pure relay means the Parent phone knows nothing until it asks. A and B ask on open and show a state stamped "hace 3 s"; C never shows a state it was not just told.</p></div>'''

body = [intro, section("Flow · shared", "First launch, account and pairing. Same in every variant.", flow)]
names = {"A": "Espejo", "B": "Tarjetas", "C": "Pedir"}
for k, ph in (("A", A), ("B", B), ("C", C)):
    body.append(section(f"Per Child phone · {k} ({names[k]})", "", ph, "vsec", k))

switch = """<div class="vbar"><button id="vp" aria-label="Anterior">←</button><span id="vl"></span><button id="vn" aria-label="Siguiente">→</button></div>
<script>
const VS=['A','B','C'],VN={A:'Espejo',B:'Tarjetas',C:'Pedir'};
function setV(v,push){document.querySelectorAll('.vsec').forEach(s=>s.classList.toggle('on',s.dataset.v===v));document.getElementById('vl').textContent=v+' ('+VN[v]+')';
 if(push){const u=new URL(location);u.searchParams.set('variant',v);history.replaceState(null,'',u)}}
let cur=new URL(location).searchParams.get('variant');if(!VS.includes(cur))cur='A';setV(cur);
const step=d=>{cur=VS[(VS.indexOf(cur)+d+VS.length)%VS.length];setV(cur,true);location.reload()};
document.getElementById('vp').onclick=()=>step(-1);document.getElementById('vn').onclick=()=>step(1);
addEventListener('keydown',e=>{if(e.target.closest('input,textarea,[contenteditable]'))return;if(e.key==='ArrowLeft')step(-1);if(e.key==='ArrowRight')step(1)});
</script>
"""
# the wall script measures offsetHeight, so hidden sections must be shown before it runs: switcher script goes first
html = head + EXTRA + '<div class="wrap">' + "".join(body) + "</div>" + switch + script
out = root / ".scratch/m9-remote/prototype-parent-screens.html"
out.write_text(html, encoding="utf-8")
print(out, len(html))
