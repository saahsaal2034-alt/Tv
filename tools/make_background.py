"""Draws the black-and-gold night skyline used as the launcher background.

Writes city.svg; render it to 1920x1080 and save it as
launcher/src/main/res/drawable-nodpi/bg_city.jpg.
"""
import random

W, H = 1920, 1080
HORIZON = 905
rnd = random.Random(7)
out = []
add = out.append

add(f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}">')
add('''<defs>
  <linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#030302"/>
    <stop offset="0.55" stop-color="#090704"/>
    <stop offset="0.82" stop-color="#1e1508"/>
    <stop offset="1" stop-color="#3a2a0e"/>
  </linearGradient>
  <radialGradient id="haze" cx="0.66" cy="0.84" r="0.5">
    <stop offset="0" stop-color="#e8bd5a" stop-opacity="0.62"/>
    <stop offset="0.45" stop-color="#c9a24a" stop-opacity="0.2"/>
    <stop offset="1" stop-color="#c9a24a" stop-opacity="0"/>
  </radialGradient>
  <linearGradient id="water" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#120d05"/>
    <stop offset="1" stop-color="#020201"/>
  </linearGradient>
  <linearGradient id="far" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#17110a"/>
    <stop offset="1" stop-color="#0d0a06"/>
  </linearGradient>
  <linearGradient id="rim" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#f3dd9b" stop-opacity="0.75"/>
    <stop offset="0.5" stop-color="#d4af37" stop-opacity="0.15"/>
    <stop offset="1" stop-color="#d4af37" stop-opacity="0"/>
  </linearGradient>
  <filter id="streak"><feGaussianBlur stdDeviation="3 1.2"/></filter>
  <filter id="blur"><feGaussianBlur stdDeviation="2.2"/></filter>
  <filter id="soft"><feGaussianBlur stdDeviation="0.6"/></filter>
</defs>''')
add(f'<rect width="{W}" height="{H}" fill="url(#sky)"/>')
add(f'<rect width="{W}" height="{H}" fill="url(#haze)"/>')

# Faint stars, kept to the top half.
for _ in range(140):
    x, y = rnd.uniform(0, W), rnd.uniform(0, HORIZON * 0.55)
    add(f'<circle cx="{x:.0f}" cy="{y:.0f}" r="{rnd.uniform(0.5, 1.3):.1f}" fill="#f3dd9b" opacity="{rnd.uniform(0.08, 0.45):.2f}"/>')

lit = []

def windows(x, top, w, h, density, bright):
    cols = max(1, int(w // 9))
    rows = int(h // 14)
    gap = w / cols
    for r in range(rows):
        for c in range(cols):
            if rnd.random() < density:
                op = rnd.uniform(0.25, 1) * bright
                color = rnd.choice(["#d4af37", "#f3dd9b", "#e8c25a", "#b8892e"])
                wx = x + c * gap + gap * 0.3
                add(f'<rect x="{wx:.1f}" y="{top + 10 + r * 14:.1f}" width="{gap * 0.4:.1f}" height="6" fill="{color}" opacity="{op:.2f}"/>')
                if bright > 0.5:
                    lit.append((wx, op))

def building(x, w, h, fill, density, bright, deco=False):
    top = HORIZON - h
    shape = f'<rect x="{x:.0f}" y="{top:.0f}" width="{w:.0f}" height="{h + 2:.0f}" fill="{fill}"/>'
    if deco:
        # Stepped art-deco crown with a spire.
        steps = ""
        sx, sw, st = x, w, top
        for _ in range(3):
            sx += sw * 0.15; sw *= 0.7; st -= h * 0.06
            steps += f'<rect x="{sx:.0f}" y="{st:.0f}" width="{sw:.0f}" height="{top - st + 2:.0f}" fill="{fill}"/>'
        spire = f'<rect x="{sx + sw / 2 - 1.5:.0f}" y="{st - h * 0.18:.0f}" width="3" height="{h * 0.18:.0f}" fill="{fill}"/>'
        light = f'<circle cx="{sx + sw / 2:.0f}" cy="{st - h * 0.18:.0f}" r="2.5" fill="#f3dd9b"/>'
        add(steps + spire + light)
    add(shape)
    if bright > 0.5:
        # Gold light from the glow catches the edge facing it.
        edge = x + w - 1.5 if x + w / 2 < 1270 else x
        add(f'<rect x="{edge:.0f}" y="{top:.0f}" width="1.5" height="{h:.0f}" fill="url(#rim)"/>')
        add(f'<rect x="{x:.0f}" y="{top:.0f}" width="{w:.0f}" height="1.2" fill="#f3dd9b" opacity="0.35"/>')
    windows(x + 3, top, w - 6, h - 20, density, bright)

# Far skyline.
x = -20
while x < W:
    w = rnd.uniform(40, 110)
    h = rnd.uniform(90, 260) + (120 if 900 < x < 1500 else 0)
    building(x, w, h, "url(#far)", 0.10, 0.35, deco=rnd.random() < 0.12)
    x += w + rnd.uniform(-10, 6)

# Near skyline, taller in the middle-right where the glow is.
x = -30
while x < W:
    w = rnd.uniform(55, 150)
    lift = 1.0 + (0.9 if 1050 < x < 1550 else 0.35 if 600 < x < 1800 else 0)
    h = rnd.uniform(70, 200) * lift
    building(x, w, h, "#050403", 0.22, 0.9, deco=h > 230 and rnd.random() < 0.6)
    x += w + rnd.uniform(-6, 10)

# Water with a blurred, flipped reflection of the gold glow.
add(f'<rect y="{HORIZON}" width="{W}" height="{H - HORIZON}" fill="url(#water)"/>')
add(f'<rect y="{HORIZON}" width="{W}" height="1.5" fill="#d4af37" opacity="0.45"/>')
# Reflected windows: soft vertical streaks under each lit column.
add('<g filter="url(#streak)">')
for wx, op in rnd.sample(lit, min(len(lit), 420)):
    y = HORIZON + rnd.uniform(4, 120)
    fade = 1 - (y - HORIZON) / (H - HORIZON)
    add(f'<rect x="{wx:.0f}" y="{y:.0f}" width="3" height="{rnd.uniform(6, 26):.0f}" fill="#d4af37" opacity="{op * 0.35 * fade:.2f}"/>')
add('</g>')
add('<g filter="url(#blur)">')
for _ in range(160):
    x = rnd.uniform(0, W)
    y = rnd.uniform(HORIZON + 6, H - 10)
    fade = 1 - (y - HORIZON) / (H - HORIZON)
    add(f'<rect x="{x:.0f}" y="{y:.0f}" width="{rnd.uniform(8, 40):.0f}" height="{rnd.uniform(1.5, 3):.1f}" fill="#d4af37" opacity="{rnd.uniform(0.1, 0.5) * fade:.2f}"/>')
add('</g>')
add('</svg>')
open("city.svg", "w").write("\n".join(out))
