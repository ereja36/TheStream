#!/usr/bin/env python3
"""Fix invalid regex in playerScript template literal (breaks all channel clicks)."""
from __future__ import annotations

from pathlib import Path

SERVER = Path("/root/all/all2/server.js")


def main() -> None:
    t = SERVER.read_text(encoding="utf-8")
    lines = t.splitlines(keepends=True)
    fixed = 0
    out = []
    for line in lines:
        # Inside playerScript template, \? is eaten → invalid (?|$). Double-escape.
        if "probeUrl" in line and ".mp4" in line and "alb_vod" in line:
            # Current (broken when emitted): if(/\.mp4(\?|$)/i.test(probeUrl) ...
            # Needed in source file: if(/\\.mp4(\\?|$)/i.test(probeUrl) ...
            if r"/\\.mp4(\\?|$)/i" in line:
                out.append(line)
                continue
            if r"/\.mp4(\?|$)/i" in line:
                line = line.replace(r"/\.mp4(\?|$)/i", r"/\\.mp4(\\?|$)/i", 1)
                fixed += 1
                print("fixed probeUrl line")
            out.append(line)
            continue
        out.append(line)
    if fixed != 1:
        # show candidates
        for i, line in enumerate(lines):
            if "mp4" in line and "probeUrl" in line:
                print(i + 1, repr(line[:120]))
        raise SystemExit(f"expected 1 fix, got {fixed}")
    SERVER.write_text("".join(out), encoding="utf-8")
    print("DONE")


if __name__ == "__main__":
    main()
