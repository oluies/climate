#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
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

# WCAG 2.2 AA, 1.1.1 Non-text Content. Pandoc gives an image written
# ![](path) no alt attribute at all - not even an empty one - so a screen
# reader is left to read out the filename. And an image written ![text](path)
# does not get its text into alt either: pandoc turns it into a figure and
# prints the text as a visible figcaption, which is why the 2026 figures were
# showing their own descriptions as body copy. Quarto's fig-alt attribute puts
# the text where it belongs, so both forms are rewritten to use it.
IMG_ALT = re.compile(r'!\[([^\]]+)\]\(([^)]+)\)')
IMG_BARE = re.compile(r'!\[\]\(([^)]+)\)')
FIGNUM = re.compile(r'figurenumber">\s*([^<]+?)\s*<')

# WCAG 2.2, duplicate ids. MacKay's internal anchors sit inside heading text as
# <span id="x">, and quarto copies a heading's markup into the table of
# contents, so the id appears twice on the page and an in-page link to it lands
# wherever the browser decides. Eight chapters do this. Lifting the anchor to
# its own line above the heading keeps the link target and leaves one id.
HEADING = re.compile(r'^(#{1,6}\s+)(.*)$')
SPAN_ID = re.compile(r'<span id="([^"]+)">(.*?)</span>')

for f in os.listdir(IMGSRC):
    s = os.path.join(IMGSRC, f)
    if os.path.isfile(s):
        shutil.copy(s, os.path.join(IMGDST, f))

FOOTNOTE = re.compile(r"\[\^([^\]\s]+)\]")

def namespace_notes(text, stem):
    """Prefix every footnote label with the chapter's name.

    MacKay numbers his notes per chapter, so [^1] exists in thirty-seven of
    them. The html book renders each chapter as its own document and the labels
    never meet. Any single-document format - epub, pdf - is one pandoc pass over
    all of them, where a repeated label is deduplicated to the first definition:
    before this, every chapter's note 1 showed chapter 1's note 1, silently, in
    thirty-six chapters. Namespacing here rather than in the source keeps the
    author's numbering intact and leaves the rendered output identical, since
    the label is never shown."""
    sub = lambda t: FOOTNOTE.sub(lambda m: f"[^{stem}-{m.group(1)}]", t)
    out, fenced = [], False
    for line in text.split("\n"):
        if line.lstrip().startswith("```"):
            fenced = not fenced
            out.append(line)
        elif fenced:
            out.append(line)          # charts.md has [^\d.eE+-] in a code block
        else:
            # Inline code spans are left alone for the same reason.
            out.append("".join(p if i % 2 else sub(p)
                               for i, p in enumerate(line.split("`"))))
    return "\n".join(out)

def add_alt(line, lines, i):
    """Give every image an alt attribute, by the best means available.

    An image that carries descriptive text in the source keeps it. One that
    does not is labelled from the caption that follows it - "Figure 20.5" -
    which says that a figure is here and which one, and leaves the caption
    itself, already visible on the next line, to do the describing. An image
    with no caption at all is marked decorative with an empty alt, which is
    what a chapter-opening illustration is. This is a floor, not a
    substitute: writing a real description for each of MacKay's figures is
    editorial work and cannot be done here.

    The alt text is escaped on the way in. A straight double quote would
    otherwise close the attribute early, and pandoc then prints the whole
    {fig-alt=...} block into the chapter as literal text and leaves the image
    with no alt at all - the failure this function exists to prevent. No
    source triggers it today; the descriptions on the 2026 figures are long
    enough that one eventually will."""
    def attr(value):
        return value.replace("\\", "\\\\").replace('"', '\\"')

    if IMG_ALT.search(line):
        return IMG_ALT.sub(lambda m: '![](%s){fig-alt="%s"}' % (m.group(2), attr(m.group(1))), line)
    if not IMG_BARE.search(line):
        return line
    num = FIGNUM.search(" ".join(lines[i + 1:i + 4]))
    label = attr(num.group(1).rstrip(".").strip()) if num else ""
    return IMG_BARE.sub(lambda m: '![](%s){fig-alt="%s"}' % (m.group(1), label), line)


def slug(text):
    """Pandoc's identifier for a heading, closely enough to spot a collision."""
    t = re.sub(r"<[^>]+>|[*_`]", "", text).lower()
    t = re.sub(r"[^\w\s-]", "", t, flags=re.UNICODE)
    return re.sub(r"\s+", "-", t.strip())


def lift_anchors(line):
    """Move an anchor span out of a heading, returning the lines to emit.

    An anchor whose id is what pandoc would name the heading anyway is
    dropped rather than moved: keeping it would reintroduce the duplicate
    this is here to remove, and the link target survives on the section."""
    h = HEADING.match(line)
    if not h or "<span id=" not in h.group(2):
        return [line]
    text = SPAN_ID.sub(lambda m: m.group(2), h.group(2))
    ids = [m.group(1) for m in SPAN_ID.finditer(h.group(2)) if m.group(1) != slug(text)]
    return ['<span id="%s"></span>' % i for i in ids] + ["", h.group(1) + text]


stripped = 0
for name in os.listdir(SRC):
    if not name.endswith(".md"): continue
    raw = open(os.path.join(SRC, name), encoding="utf-8").read()
    lines = namespace_notes(raw, os.path.splitext(name)[0]).splitlines()
    out = []
    for i, line in enumerate(lines):
        if IMGLINE.match(line):
            ctx = " ".join(lines[max(0, i-2):i+4])
            if RIGHTS.search(ctx):
                out.append("> *(Figure omitted from this edition: third-party rights.)*")
                stripped += 1
                continue
        for emit in lift_anchors(add_alt(line, lines, i)):
            out.append(emit.replace("/img/without-hot-air/", "img/without-hot-air/"))
    open(os.path.join(CHAP, name), "w", encoding="utf-8").write("\n".join(out) + "\n")
print(f"prep: {len(os.listdir(CHAP))} chapters, images copied, {stripped} rights-restricted figures stripped")
