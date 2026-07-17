#!/usr/bin/env python3
"""Fix missing commas before bottomAdHtml in server.js object literals."""
from pathlib import Path
import re
import subprocess

p = Path("/root/all/all2/server.js")
t = p.read_text(encoding="utf-8")

# Add comma before bottomAdHtml when previous non-empty line lacks trailing comma
def fix(m):
    prev = m.group(1)
    indent = m.group(2)
    if prev.rstrip().endswith(",") or prev.rstrip().endswith("{"):
        return m.group(0)
    return prev + ",\n" + indent + "bottomAdHtml:"

t2 = re.sub(r"([^\n]+)\n(\s+)bottomAdHtml:", fix, t)
p.write_text(t2, encoding="utf-8")
print("wrote", p)

r = subprocess.run(["node", "--check", str(p)], capture_output=True, text=True)
print("node check:", r.returncode)
if r.stderr:
    print(r.stderr[:500])
else:
    print("OK")
