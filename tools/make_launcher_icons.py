#!/usr/bin/env python3
"""
Renders the legacy (pre-adaptive) launcher icons from the same design as the vector layers.

Adaptive icons cover API 26 and up, which is this app's minimum, so these rasters are mostly
belt and braces for launchers and tools that still ask for a bitmap. Regenerating them from the
same geometry keeps them from drifting away from the vector artwork.
"""
import os
from PIL import Image, ImageDraw

RES = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")
NAVY = (18, 38, 63)
CYAN = (90, 215, 232)
WHITE = (255, 255, 255)
MUTED = (138, 155, 181)

# Densities: directory suffix -> icon edge in px.
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

SS = 8  # supersampling factor, downscaled at the end for clean edges


def draw_design(d, s):
    """Draws the icon into a canvas `s` px across, in the same 108-unit space as the vectors."""
    def u(v):
        return v * s / 108.0

    # The legacy icon has no safe-zone constraint, so the artwork is scaled up to fill more of
    # the tile than the adaptive foreground does.
    def x(v):
        return u(54 + (v - 54) * 1.18)

    w = max(1, int(u(5 * 1.18)))
    cap = w // 2

    def bracket(pts):
        p = [(x(a), x(b)) for a, b in pts]
        d.line(p, fill=CYAN, width=w, joint="curve")
        for px, py in (p[0], p[-1]):
            d.ellipse([px - cap, py - cap, px + cap, py + cap], fill=CYAN)

    bracket([(28, 45), (28, 34), (39, 34)])
    bracket([(69, 34), (80, 34), (80, 45)])
    bracket([(28, 63), (28, 74), (39, 74)])
    bracket([(69, 74), (80, 74), (80, 63)])

    d.rounded_rectangle([x(37), x(43), x(71), x(65)], radius=u(3 * 1.18), fill=WHITE)

    for (x0, x1, y, width, colour) in (
        (43, 59, 50, 4.0, NAVY),
        (43, 53, 58, 2.8, MUTED),
    ):
        lw = max(1, int(u(width * 1.18)))
        d.line([(x(x0), x(y)), (x(x1), x(y))], fill=colour, width=lw)
        r = lw // 2
        for px in (x(x0), x(x1)):
            d.ellipse([px - r, x(y) - r, px + r, x(y) + r], fill=colour)


def render(size, round_icon):
    s = size * SS
    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if round_icon:
        d.ellipse([0, 0, s - 1, s - 1], fill=NAVY)
    else:
        d.rounded_rectangle([0, 0, s - 1, s - 1], radius=s * 0.22, fill=NAVY)
    draw_design(d, s)
    return img.resize((size, size), Image.LANCZOS)


if __name__ == "__main__":
    for suffix, size in DENSITIES.items():
        folder = os.path.join(RES, "mipmap-" + suffix)
        os.makedirs(folder, exist_ok=True)
        for name, is_round in (("ic_launcher", False), ("ic_launcher_round", True)):
            # The template shipped .webp; a .png beside it would be a duplicate resource.
            old = os.path.join(folder, name + ".webp")
            if os.path.exists(old):
                os.remove(old)
            out = os.path.join(folder, name + ".png")
            render(size, is_round).save(out)
            print("wrote", os.path.relpath(out, RES))
