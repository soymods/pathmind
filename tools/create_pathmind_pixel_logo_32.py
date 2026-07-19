from pathlib import Path

from PIL import Image, ImageDraw


OUT_DIR = Path("artifacts/pathmind-logo")
OUT_DIR.mkdir(parents=True, exist_ok=True)

C = {
    "bg": "#07090d",
    "void": "#0c0f14",
    "outline": "#171b21",
    "deep": "#292e35",
    "steel_dark": "#42474e",
    "steel": "#70757a",
    "steel_light": "#c7cbcc",
    "white": "#f3f5f4",
    "blue_dark": "#123c9e",
    "blue": "#1760e8",
    "blue_light": "#5590ff",
    "red_dark": "#a7121a",
    "red": "#ef2028",
    "red_light": "#ff5748",
    "green_dark": "#16862a",
    "green": "#27d343",
    "green_light": "#69ef68",
}

im = Image.new("RGB", (32, 32), C["bg"])
d = ImageDraw.Draw(im)


def poly(points, color):
    d.polygon(points, fill=C[color])


def rect(box, color):
    d.rectangle(box, fill=C[color])


# Upper circuit rails, mirrored around the vertical centerline.
poly([(12, 5), (13, 7), (11, 8), (11, 9), (9, 9), (9, 11),
      (8, 11), (8, 13), (6, 13), (6, 19), (3, 19), (3, 13),
      (5, 13), (5, 11), (7, 11), (7, 9), (9, 9), (9, 7)], "outline")
poly([(12, 6), (12, 7), (10, 8), (10, 10), (8, 10), (8, 12),
      (7, 12), (7, 14), (5, 14), (5, 18), (4, 18), (4, 14),
      (6, 14), (6, 12), (8, 12), (8, 10), (10, 10), (10, 8)], "steel")
rect((5, 14, 5, 17), "steel_light")
rect((8, 10, 8, 11), "steel_light")
rect((10, 8, 10, 8), "steel_light")

poly([(19, 5), (18, 7), (20, 8), (20, 9), (22, 9), (22, 11),
      (23, 11), (23, 13), (25, 13), (25, 19), (28, 19), (28, 13),
      (26, 13), (26, 11), (24, 11), (24, 9), (22, 9), (22, 7)], "outline")
poly([(19, 6), (19, 7), (21, 8), (21, 10), (23, 10), (23, 12),
      (24, 12), (24, 14), (26, 14), (26, 18), (27, 18), (27, 14),
      (25, 14), (25, 12), (23, 12), (23, 10), (21, 10), (21, 8)], "steel")
rect((26, 14, 26, 17), "steel_dark")
rect((23, 10, 23, 11), "steel_dark")
rect((21, 8, 21, 8), "steel_dark")

# Lower circuit rails and bottom clasp.
poly([(7, 24), (9, 23), (11, 26), (14, 27), (14, 29), (12, 29),
      (12, 28), (10, 28), (10, 27), (8, 27)], "outline")
poly([(8, 24), (9, 24), (11, 26), (14, 27), (14, 28), (12, 28),
      (12, 27), (10, 27)], "steel")
rect((9, 24, 9, 25), "steel_light")

poly([(24, 24), (22, 23), (20, 26), (17, 27), (17, 29), (19, 29),
      (19, 28), (21, 28), (21, 27), (23, 27)], "outline")
poly([(23, 24), (22, 24), (20, 26), (17, 27), (17, 28), (19, 28),
      (19, 27), (21, 27)], "steel")
rect((22, 24, 22, 25), "steel_dark")


def housing(cx, cy):
    poly([(cx - 3, cy - 4), (cx + 3, cy - 4), (cx + 4, cy - 3),
          (cx + 4, cy + 3), (cx + 3, cy + 4), (cx - 3, cy + 4),
          (cx - 4, cy + 3), (cx - 4, cy - 3)], "outline")
    poly([(cx - 2, cy - 3), (cx + 2, cy - 3), (cx + 3, cy - 2),
          (cx + 3, cy + 2), (cx + 2, cy + 3), (cx - 2, cy + 3),
          (cx - 3, cy + 2), (cx - 3, cy - 2)], "steel_dark")
    rect((cx - 2, cy - 3, cx + 2, cy - 3), "steel_light")
    rect((cx - 3, cy - 2, cx - 3, cy + 1), "steel")
    rect((cx + 3, cy - 1, cx + 3, cy + 2), "deep")


def side_housing(cx, cy, facing):
    """A narrower side housing that leaves breathing room around the dial."""
    if facing == "left":
        outer = [(cx - 2, cy - 4), (cx + 2, cy - 4), (cx + 3, cy - 3),
                 (cx + 3, cy + 3), (cx + 2, cy + 4), (cx - 2, cy + 4),
                 (cx - 4, cy + 2), (cx - 4, cy - 2)]
        inner = [(cx - 2, cy - 3), (cx + 1, cy - 3), (cx + 2, cy - 2),
                 (cx + 2, cy + 2), (cx + 1, cy + 3), (cx - 2, cy + 3),
                 (cx - 3, cy + 2), (cx - 3, cy - 2)]
        highlight_x, shadow_x = cx - 3, cx + 2
    else:
        outer = [(cx - 2, cy - 4), (cx + 2, cy - 4), (cx + 4, cy - 2),
                 (cx + 4, cy + 2), (cx + 2, cy + 4), (cx - 2, cy + 4),
                 (cx - 3, cy + 3), (cx - 3, cy - 3)]
        inner = [(cx - 1, cy - 3), (cx + 2, cy - 3), (cx + 3, cy - 2),
                 (cx + 3, cy + 2), (cx + 2, cy + 3), (cx - 1, cy + 3),
                 (cx - 2, cy + 2), (cx - 2, cy - 2)]
        highlight_x, shadow_x = cx - 2, cx + 3
    poly(outer, "outline")
    poly(inner, "steel_dark")
    rect((cx - 1, cy - 3, cx + 1, cy - 3), "steel_light")
    rect((highlight_x, cy - 1, highlight_x, cy + 1), "steel")
    rect((shadow_x, cy, shadow_x, cy + 2), "deep")


def gem(cx, cy, dark, base, light):
    poly([(cx - 1, cy - 2), (cx + 1, cy - 2), (cx + 2, cy - 1),
          (cx + 2, cy + 1), (cx + 1, cy + 2), (cx - 1, cy + 2),
          (cx - 2, cy + 1), (cx - 2, cy - 1)], dark)
    rect((cx - 1, cy - 1, cx + 1, cy + 1), base)
    rect((cx - 1, cy - 1, cx, cy - 1), light)
    rect((cx - 1, cy, cx - 1, cy), light)
    rect((cx + 1, cy + 1, cx + 1, cy + 1), dark)


# Topmost artwork pixel is y=2; side nodes reach x=2 and x=29.
housing(16, 6)
gem(16, 6, "blue_dark", "blue", "blue_light")
side_housing(6, 23, "left")
gem(6, 23, "red_dark", "red", "red_light")
side_housing(25, 23, "right")
gem(25, 23, "green_dark", "green", "green_light")

# Compact bottom clasp reaches y=29, completing the uniform two-pixel margin.
poly([(13, 27), (18, 27), (19, 28), (19, 29), (12, 29), (12, 28)], "outline")
rect((13, 28, 18, 29), "steel_dark")
rect((14, 28, 17, 28), "steel")

# Central dial is broad enough to read at 1x while remaining clear of the gems.
poly([(13, 10), (18, 10), (21, 13), (21, 19), (18, 22), (13, 22),
      (10, 19), (10, 13)], "outline")
poly([(13, 11), (18, 11), (20, 13), (20, 19), (18, 21), (13, 21),
      (11, 19), (11, 13)], "steel")
poly([(14, 12), (17, 12), (19, 14), (19, 18), (17, 20), (14, 20),
      (12, 18), (12, 14)], "void")
rect((14, 11, 17, 11), "steel_light")
rect((11, 14, 11, 18), "steel_light")
rect((14, 21, 17, 21), "steel_dark")
rect((20, 14, 20, 18), "deep")

# Balanced diagonal needle with a distinct one-pixel pivot.
poly([(15, 16), (17, 14), (18, 14), (18, 13), (20, 12), (19, 15),
      (18, 15), (17, 17)], "red_dark")
poly([(16, 16), (18, 14), (19, 13), (18, 16), (17, 16), (17, 17)], "red")
rect((18, 14, 18, 14), "red_light")

poly([(16, 17), (14, 19), (13, 19), (12, 20), (13, 18), (15, 16)], "steel")
poly([(15, 17), (14, 19), (13, 19), (14, 18), (15, 16)], "white")
rect((15, 16, 16, 17), "steel_light")

palette_size = len(set(C.values()))
native = im.convert("P", palette=Image.Palette.ADAPTIVE, colors=palette_size)
native.save(OUT_DIR / "pathmind-logo-32-v2.png", optimize=False)
im.resize((512, 512), Image.Resampling.NEAREST).save(
    OUT_DIR / "pathmind-logo-32-v2-preview-512.png", optimize=False
)
