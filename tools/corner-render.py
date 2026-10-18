"""Renders the Capy's corner room from the design's Corner.dc.html into stacked transparent layers.

Usage (once: `npm install @fontsource/nunito@5.1.0 @fontsource/dancing-script@5.1.0` next to this
script, for the poster lettering; Python Playwright with Chromium):
    python3 tools/corner-render.py <Corner.dc.html from the "Capy Tutore Pink" canvas> app/src/main/res
writes res/drawable-nodpi/corner_*.png (light) and res/drawable-night-nodpi/corner_*.png (dark).

Each layer is the full room (328 x 320 CSS px) at SCALE, so the app stacks them with no positions.
Light and dark use the design's own token maps (L / D in the artboard's script).
"""
import pathlib
import re
import sys

from playwright.sync_api import sync_playwright

SRC = pathlib.Path(sys.argv[1])
OUT = pathlib.Path(sys.argv[2])
SCALE = 4
HERE = pathlib.Path(__file__).parent
FONTS = HERE / "node_modules/@fontsource"

lines = SRC.read_text().split("\n")


def block(a, b):
    """Lines a..b (1-based, inclusive) without the sc-if wrappers."""
    out = []
    for l in lines[a - 1:b]:
        l = re.sub(r"<sc-if[^>]*>", "", l).replace("</sc-if>", "")
        out.append(l)
    return "\n".join(out)


def tokens(name):
    src = SRC.read_text()
    m = re.search(r"const %s = (\{.*?\});" % name, src)
    pairs = re.findall(r"(\w+):'([^']*)'", m.group(1))
    return dict(pairs)


ROOM_OPEN = lines[23]  # the room div (line 24)
LAYERS = {
    "bg": block(25, 26) + "\n" + block(103, 111),
    "window": block(28, 30),
    "shelf": block(32, 41),
    "stickers": block(43, 57),
    "posters": block(59, 88),
    "plant": block(90, 101),
    "lamp": block(113, 141),
    "cup": block(143, 154),
    "rug": block(156, 166),
    "pillow_back": block(168, 185),
    "pillow_front": block(189, 203),
    "food": block(205, 213),
    "plush_high": block(215, 256),
    "plush_low": block(215, 256),
}


def page(layer, t):
    body = LAYERS[layer]
    body = body.replace("{{mBottom}}", "56" if layer == "plush_high" else "20")
    # The poster's lettering is wider than the poster in this font: fit it inside.
    body = body.replace('font-weight="800" fill="#FFFFFF"', 'font-weight="800" textLength="34" lengthAdjust="spacingAndGlyphs" fill="#FFFFFF"')
    room = ROOM_OPEN if layer == "bg" else re.sub(r"background: linear-gradient\([^;]*\);", "background: transparent;", ROOM_OPEN)
    room = room.replace('role="img" aria-label="{{roomLabel}}" ', "")
    html = room + "\n" + body + "\n</div>"
    html = re.sub(r"\{\{t\.(\w+)\}\}", lambda m: t[m.group(1)], html)
    assert "{{" not in html, re.findall(r"\{\{[^}]*\}\}", html)[:3]
    fonts = f"""
@font-face{{font-family:'Nunito';font-weight:800;src:url({FONTS}/nunito/files/nunito-latin-800-normal.woff2)}}
@font-face{{font-family:'Nunito';font-weight:700;src:url({FONTS}/nunito/files/nunito-latin-700-normal.woff2)}}
@font-face{{font-family:'Dancing Script';font-weight:700;src:url({FONTS}/dancing-script/files/dancing-script-latin-700-normal.woff2)}}
"""
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>{fonts}
html,body{{margin:0;background:transparent}} #wrap{{width:328px;height:320px}}</style></head>
<body><div id="wrap">{html}</div></body></html>"""


def main():
    themes = {"": tokens("L"), "night-": tokens("D")}
    with sync_playwright() as p:
        b = p.chromium.launch()
        ctx = b.new_context(device_scale_factor=SCALE, viewport={"width": 400, "height": 400})
        pg = ctx.new_page()
        for prefix, t in themes.items():
            d = OUT / f"drawable-{prefix}nodpi"
            d.mkdir(parents=True, exist_ok=True)
            for layer in LAYERS:
                f = HERE / f"page-{prefix}{layer}.html"
                f.write_text(page(layer, t))
                pg.goto(f.as_uri())
                pg.evaluate("document.fonts.ready")
                pg.wait_for_timeout(150)
                pg.locator("#wrap").screenshot(path=str(d / f"corner_{layer}.png"), omit_background=True)
                print(prefix or "light-", layer)
        b.close()


main()
