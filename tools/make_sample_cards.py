#!/usr/bin/env python3
"""
Generates synthetic business card images for the OCR/parsing test set.

Real cards are the ground truth, but they are hard to share and slow to collect. These cover the
layout shapes that break naive parsers: wrapped titles, several labelled phone numbers, a
light-on-dark design, two columns, non-English words with umlauts, and a card with almost nothing
on it. Fonts are whatever the host has; the point is layout variety, not typography.
"""
import os
import subprocess
from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(__file__), "..", "samples", "images")
W, H = 1050, 600  # 3.5 x 2 inches at 300 dpi

def font_path(name):
    try:
        out = subprocess.check_output(["fc-match", "-f", "%{file}", name], text=True)
        return out.strip()
    except Exception:
        return None

SANS = font_path("Liberation Sans")
SANS_BOLD = font_path("Liberation Sans:bold")
SERIF = font_path("Liberation Serif")
MONO = font_path("Liberation Mono")

def f(path, size):
    return ImageFont.truetype(path, size) if path else ImageFont.load_default(size)

def card(name, bg, draw_fn):
    img = Image.new("RGB", (W, H), bg)
    draw_fn(ImageDraw.Draw(img))
    path = os.path.join(OUT, name + ".png")
    img.save(path)
    print("wrote", path)

def us_northwind(d):
    d.text((60, 60), "NORTHWIND LOGISTICS LTD", font=f(SANS_BOLD, 34), fill=(20, 40, 90))
    d.text((60, 150), "Dana R. Whitfield", font=f(SANS_BOLD, 46), fill=(10, 10, 10))
    d.text((60, 215), "Senior Operations Manager", font=f(SANS, 30), fill=(90, 90, 90))
    d.text((60, 310), "M  +1 415 555 2671", font=f(SANS, 26), fill=(30, 30, 30))
    d.text((60, 352), "T  +1 415 555 0119", font=f(SANS, 26), fill=(30, 30, 30))
    d.text((60, 394), "F  +1 415 555 0188", font=f(SANS, 26), fill=(30, 30, 30))
    d.text((60, 452), "dana.whitfield@northwindlogistics.com", font=f(SANS, 26), fill=(30, 60, 140))
    d.text((60, 494), "www.northwindlogistics.com", font=f(SANS, 26), fill=(30, 60, 140))

def uk_calder(d):
    d.text((60, 70), "Calder Wharf Studio", font=f(SERIF, 40), fill=(15, 15, 15))
    d.text((60, 175), "Priya Ramanathan", font=f(SANS_BOLD, 44), fill=(10, 10, 10))
    d.text((60, 235), "Head of Product Design,", font=f(SANS, 28), fill=(100, 60, 120))
    d.text((60, 275), "Europe", font=f(SANS, 28), fill=(100, 60, 120))
    d.text((60, 350), "Mobile +44 7911 123456", font=f(SANS, 25), fill=(30, 30, 30))
    d.text((60, 390), "Tel +44 20 7946 0821", font=f(SANS, 25), fill=(30, 30, 30))
    d.text((60, 440), "priya@calderwharf.co.uk", font=f(SANS, 25), fill=(30, 60, 140))
    d.text((60, 490), "18 Calder Wharf, Unit 4, Manchester M3 4LZ", font=f(SANS, 22), fill=(80, 80, 80))

def de_mueller(d):
    d.text((60, 60), "Hochbrunn Systeme GmbH", font=f(SANS_BOLD, 36), fill=(20, 20, 20))
    d.text((60, 160), "Dr. Katharina Müller", font=f(SANS_BOLD, 42), fill=(10, 10, 10))
    d.text((60, 220), "Technische Leiterin", font=f(SANS, 28), fill=(90, 90, 90))
    d.text((60, 310), "Tel +49 30 901820", font=f(SANS, 25), fill=(30, 30, 30))
    d.text((60, 350), "Fax +49 30 901899", font=f(SANS, 25), fill=(30, 30, 30))
    d.text((60, 400), "k.mueller@hochbrunn.de", font=f(SANS, 25), fill=(30, 60, 140))
    d.text((60, 450), "Zimmerstrasse 14, 10969 Berlin", font=f(SANS, 23), fill=(80, 80, 80))
    d.text((60, 490), "Germany", font=f(SANS, 23), fill=(80, 80, 80))

def dark_reverse(d):
    d.text((60, 90), "VANTA SECURITY LABS", font=f(SANS_BOLD, 32), fill=(120, 220, 200))
    d.text((60, 190), "Marcus Oyelaran", font=f(SANS_BOLD, 48), fill=(245, 245, 245))
    d.text((60, 255), "Principal Security Engineer", font=f(SANS, 28), fill=(180, 180, 190))
    d.text((60, 360), "M +1 206 555 0173", font=f(SANS, 26), fill=(235, 235, 235))
    d.text((60, 410), "marcus@vantalabs.io", font=f(SANS, 26), fill=(120, 220, 200))
    d.text((60, 470), "vantalabs.io", font=f(MONO, 24), fill=(120, 220, 200))

def two_column(d):
    d.text((60, 80), "Sofia Marchetti", font=f(SANS_BOLD, 44), fill=(10, 10, 10))
    d.text((60, 140), "Managing Partner", font=f(SANS, 28), fill=(90, 90, 90))
    d.text((60, 200), "Marchetti & Vance LLP", font=f(SERIF, 30), fill=(40, 40, 70))
    d.line((540, 70, 540, 520), fill=(200, 200, 200), width=2)
    d.text((590, 90), "Office", font=f(SANS_BOLD, 22), fill=(120, 120, 120))
    d.text((590, 125), "+1 312 555 0144", font=f(SANS, 25), fill=(30, 30, 30))
    d.text((590, 190), "Mobile", font=f(SANS_BOLD, 22), fill=(120, 120, 120))
    d.text((590, 225), "+1 312 555 0190", font=f(SANS, 25), fill=(30, 30, 30))
    d.text((590, 300), "Email", font=f(SANS_BOLD, 22), fill=(120, 120, 120))
    d.text((590, 335), "sofia@mvlaw.com", font=f(SANS, 24), fill=(30, 60, 140))
    d.text((590, 405), "Address", font=f(SANS_BOLD, 22), fill=(120, 120, 120))
    d.text((590, 440), "200 W Adams St, Suite 1400", font=f(SANS, 21), fill=(80, 80, 80))
    d.text((590, 472), "Chicago, IL 60606", font=f(SANS, 21), fill=(80, 80, 80))

def minimal(d):
    d.text((60, 230), "Ines Okafor", font=f(SANS, 54), fill=(20, 20, 20))
    d.text((60, 320), "ines@okafor.studio", font=f(SANS, 30), fill=(20, 20, 20))

CARDS = [
    ("us-northwind", (255, 255, 255), us_northwind),
    ("uk-calder", (252, 250, 246), uk_calder),
    ("de-mueller", (255, 255, 255), de_mueller),
    ("dark-vanta", (18, 22, 30), dark_reverse),
    ("two-column", (255, 255, 255), two_column),
    ("minimal", (255, 255, 255), minimal),
]

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for name, bg, fn in CARDS:
        card(name, bg, fn)
