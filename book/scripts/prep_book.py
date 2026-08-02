#!/usr/bin/env python3
"""Prepare MacKay chapters for the Quarto build.
Source of truth is without-hot-air/src. This copies each chapter into book/chapters/,
rewrites absolute /img/ paths to relative, and removes figures that carry third-party
rights (not covered by MacKay's CC BY-NC-SA licence) so the public build is clean."""
import os, re, shutil, sys
HERE = os.path.dirname(os.path.abspath(__file__))
BOOK = os.path.dirname(HERE)
ROOT = os.path.dirname(BOOK)
SRC = os.path.join(ROOT, "without-hot-air", "src")
IMGSRC = os.path.join(ROOT, "without-hot-air", "Images")
CHAP = os.path.join(BOOK, "chapters")
IMGDST = os.path.join(BOOK, "chapters", "img", "without-hot-air")
os.makedirs(CHAP, exist_ok=True); os.makedirs(IMGDST, exist_ok=True)

RIGHTS = re.compile(r'private eye|ordnance survey|crown copyright|photo by|photograph by|photo:|photograph:|getty', re.I)
IMGLINE = re.compile(r'^!\[\]\(/img/without-hot-air/[^)]+\)\s*$')

for f in os.listdir(IMGSRC):
    s = os.path.join(IMGSRC, f)
    if os.path.isfile(s):
        shutil.copy(s, os.path.join(IMGDST, f))

stripped = 0
for name in os.listdir(SRC):
    if not name.endswith(".md"): continue
    lines = open(os.path.join(SRC, name), encoding="utf-8").read().splitlines()
    out = []
    for i, line in enumerate(lines):
        if IMGLINE.match(line):
            ctx = " ".join(lines[max(0, i-2):i+4])
            if RIGHTS.search(ctx):
                out.append("> *(Figure omitted from this edition: third-party rights.)*")
                stripped += 1
                continue
        out.append(line.replace("/img/without-hot-air/", "img/without-hot-air/"))
    open(os.path.join(CHAP, name), "w", encoding="utf-8").write("\n".join(out) + "\n")
print(f"prep: {len(os.listdir(CHAP))} chapters, images copied, {stripped} rights-restricted figures stripped")
