# 03: Capture backend, switching rules and start on boot

Type: grilling
Status: open
Blocked by: 01, 02

## Question

Decide how protection gets frames and comes back on its own (gap 1: survive normal life). Starting point: human prefers **hybrid** (MediaProjection when the Parent started it, accessibility screenshots as fallback), and wants the screenshot backend kept **as a toggle** (said after ticket 02). Research may favour replacing MediaProjection outright.

- Toggle: who sets it (the Parent in Settings, or a debug switch), what it chooses (always screenshots / hybrid / MediaProjection only), and whether the fallback still happens automatically when it is off.

- Hybrid vs replace, given ticket 02's numbers on A.
- Switching rules: when does it fall back (reboot, consent revoked, Android 15+ secure-lock end, Child stops via the screen-share chip) and when, if ever, does it go back to MediaProjection? What the Child sees during a switch (exposure).
- Start on boot: boot receiver vs the accessibility service's own rebind; foreground service type for a start with no Activity; what the notification says. Does "Paused — tap to resume" (D21) survive or go away?
- Status screen and Log: how the backend shows (e.g. "Protección activa · modo accesibilidad"); new protection on/off reasons.
- Does Precise (D35) still work on the screenshot backend? (Peek does: ticket 02 ran 171 peeks on B with no failures; window shots have their own budget.)
- From ticket 02: the probe timeout must exceed the shot interval on the screenshot backend (300 ms flickered and got stuck; 700 ms was fine). Is it per backend, and what value? Should peek under mask be on by default wherever it works (B), now that it's the difference between "flickers" and "barely flickers"? Stop shots on screen off (they come back black).
- Is A's ~0.45 s exposure per probe on the fallback acceptable, or does A need something more (for example, telling the Parent that fallback protection is weaker on this phone)?
- Which SPEC §7 limitations (5, 6, 10, 17) change, and which D-entries are amended (D28/D33 rotation path, D46 starting without the service).
