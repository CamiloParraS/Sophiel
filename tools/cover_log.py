"""Ticket 10 / M6 V4: readable logcat for settings changes taken through the cover path.

    python tools/cover_log.py B        # or A

Hides the per-frame lines and prints one block per change: how long the cover took to show,
then the tiles that probed after it and the exposure of the ones that re-masked (the flagged
content). Ctrl-C prints the median and worst across every change.
"""
import queue
import re
import statistics
import subprocess
import sys
import threading

MODELS = {"A": "SM-A715F", "B": "SM-S721B"}  # DECISIONS.md device roles
SETTLE_S = 1.5  # probes this long after a cover ends belong to it

LINE = re.compile(r"(\d\d:\d\d:\d\d)\.\d+\s+\S+\s+\S+\s+([DIWE]) Sophiel\s*: (.*)")
PROBE = re.compile(r"probe tile=(\d+) exposureMs=(\d+) -> (\w+)")
PEEK = re.compile(r"peek tile=(\d+) ms=(\d+) -> (\w+)")  # judged from a window shot, mask stays up


def adb(*args):
    return subprocess.run(["adb", *args], capture_output=True, text=True).stdout


def serial_for(letter):
    for line in adb("devices").splitlines()[1:]:
        if line.endswith("\tdevice"):
            s = line.split("\t")[0]
            if adb("-s", s, "shell", "getprop", "ro.product.model").strip() == MODELS[letter]:
                return s
    sys.exit(f"Device {letter} ({MODELS[letter]}) is not connected")


def main():
    letter = (sys.argv[1:] or ["A"])[0].upper()
    serial = serial_for(letter)
    adb("-s", serial, "logcat", "-c")
    proc = subprocess.Popen(["adb", "-s", serial, "logcat", "-v", "threadtime", "-s", "Sophiel:D"],
                            stdout=subprocess.PIPE, text=True, encoding="utf-8", errors="replace")
    lines = queue.Queue()
    threading.Thread(target=lambda: [lines.put(l) for l in proc.stdout], daemon=True).start()
    print(f"Device {letter}: watching. Make a settings change with something masked. Ctrl-C for the totals.\n")

    changes = []  # per change: exposures (ms) of tiles that re-masked
    checks = 0  # cover checks seen while waiting for the cover
    batch = None  # after a cover ends: masked exposures, released count, peeked count

    def flush():
        nonlocal batch
        if batch is None:
            return
        masked, released, peeked = batch
        if masked:
            print(f"          re-masked {len(masked):2} tiles   exposure median {statistics.median(masked):.0f} ms, worst {max(masked)} ms")
            changes.append(masked)
        else:
            print("          no probe re-masked")
        print(f"          released  {released:2} tiles")
        if peeked:
            print(f"          peeked    {peeked:2} tiles (mask stayed up, nothing exposed)")
        print()
        batch = None

    try:
        while True:
            try:
                raw = lines.get(timeout=SETTLE_S)
            except queue.Empty:
                flush()
                continue
            m = LINE.search(raw)
            if not m:
                continue
            time, _, msg = m.groups()
            if msg.startswith("cover check"):
                if checks == 0:
                    flush()
                    print(f"{time}  COVER UP")
                checks += 1
            elif msg == "cover ends, every tile probes":
                print(f"{time}  cover seen after {checks} frame(s)" if checks else f"{time}  cover timed out (1 s)")
                print(f"{time}  cover off, every tile probes")
                checks, batch = 0, ([], 0, 0)
            elif msg.startswith("cover ends, box masks="):
                print(f"{time}  PRECISE: cover replaced by {msg.split('=')[1]} box mask(s), nothing exposed\n")
                checks = 0
            elif msg.startswith("box shot: none"):
                print(f"{time}  precise shot failed ({msg.split(', ')[1]})")
            elif batch is not None and (p := PROBE.match(msg)):
                _, ms, state = p.groups()
                if state == "MASKED":
                    batch[0].append(int(ms))
                else:
                    batch = (batch[0], batch[1] + 1, batch[2])
            elif batch is not None and PEEK.match(msg):
                batch = (batch[0], batch[1], batch[2] + 1)
            elif msg.startswith("mask window:"):
                print(f"{time}  {msg}")
    except KeyboardInterrupt:
        flush()
        proc.kill()
        every = [ms for c in changes for ms in c]
        print(f"\nDevice {letter}: {len(changes)} change(s) with re-masked tiles")
        if every:
            worst_per_change = [max(c) for c in changes]
            print(f"  exposure per change (worst tile): median {statistics.median(worst_per_change):.0f} ms, worst {max(worst_per_change)} ms")
            print(f"  every re-masked tile: n={len(every)}, median {statistics.median(every):.0f} ms, worst {max(every)} ms")


if __name__ == "__main__":
    main()
