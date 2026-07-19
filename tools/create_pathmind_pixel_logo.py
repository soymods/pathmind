from pathlib import Path

from PIL import Image, ImageDraw


OUT_DIR = Path("artifacts/pathmind-logo")
OUT_DIR.mkdir(parents=True, exist_ok=True)

# A deliberately small, shared palette. Every edge is hard and grid-aligned.
C = {
    "bg": "#07090d",
    "void": "#0c0f14",
    "outline": "#15191f",
    "deep": "#24282e",
    "steel_dark": "#3b4047",
    "steel": "#656a70",
    "steel_mid": "#92969a",
    "steel_light": "#d4d7d8",
    "white": "#f2f4f3",
    "blue_dark": "#123a98",
    "blue": "#1759df",
    "blue_light": "#4c87ff",
    "red_dark": "#9c1118",
    "red": "#ed1c24",
    "red_light": "#ff4a3d",
    "green_dark": "#158526",
    "green": "#25ce3c",
    "green_light": "#62ed61",
}

im = Image.new("RGB", (64, 64), C["bg"])
d = ImageDraw.Draw(im)


def poly(points, color):
    d.polygon(points, fill=C[color])


def rect(box, color):
    d.rectangle(box, fill=C[color])


def stepped_rail(points):
    """Draw one 5px steel rail using three deliberate pixel bands."""
    poly(points["outline"], "outline")
    poly(points["shadow"], "steel_dark")
    poly(points["body"], "steel")
    poly(points["shine"], "steel_mid")


# Outer navigation circuit: a complete, segmented ring behind the nodes.
stepped_rail({
    "outline": [(24, 10), (27, 13), (20, 18), (20, 20), (17, 20), (17, 23),
                (15, 23), (15, 27), (13, 27), (13, 37), (8, 37), (8, 26),
                (10, 26), (10, 22), (12, 22), (12, 19), (15, 19), (15, 16),
                (18, 16), (18, 14), (22, 10)],
    "shadow": [(23, 12), (25, 14), (19, 19), (19, 21), (16, 21), (16, 25),
               (14, 25), (14, 36), (10, 36), (10, 27), (12, 27), (12, 23),
               (14, 23), (14, 20), (17, 20), (17, 17), (20, 17)],
    "body": [(22, 12), (24, 14), (19, 18), (17, 18), (17, 21), (15, 21),
             (15, 25), (12, 25), (12, 34), (10, 34), (10, 28), (12, 28),
             (12, 24), (14, 24), (14, 20), (16, 20), (16, 17), (19, 17)],
    "shine": [(21, 13), (22, 14), (18, 17), (16, 17), (16, 20), (14, 20),
              (14, 24), (12, 24), (12, 27), (10, 27), (11, 25), (13, 25),
              (13, 21), (15, 21), (15, 18), (18, 18)],
})

stepped_rail({
    "outline": [(40, 10), (42, 10), (42, 12), (46, 12), (46, 15), (49, 15),
                (49, 18), (52, 18), (52, 21), (54, 21), (54, 25), (56, 25),
                (56, 37), (51, 37), (51, 27), (49, 27), (49, 23), (47, 23),
                (47, 20), (44, 20), (44, 18), (39, 13)],
    "shadow": [(40, 12), (43, 13), (43, 15), (46, 15), (46, 18), (49, 18),
               (49, 21), (52, 21), (52, 25), (54, 25), (54, 36), (52, 36),
               (52, 27), (50, 27), (50, 23), (48, 23), (48, 20), (45, 20),
               (45, 17), (42, 17), (39, 14)],
    "body": [(40, 12), (42, 14), (45, 14), (45, 17), (48, 17), (48, 20),
             (51, 20), (51, 24), (53, 24), (53, 34), (55, 34), (55, 28),
             (53, 28), (53, 25), (51, 25), (51, 22), (49, 22), (49, 19),
             (46, 19), (46, 16), (43, 16)],
    "shine": [(41, 13), (43, 14), (46, 14), (46, 17), (49, 17), (49, 20),
              (52, 20), (52, 24), (54, 24), (54, 27), (52, 27), (52, 23),
              (50, 23), (50, 20), (47, 20), (47, 17), (44, 17)],
})

# Lower rails converge on a central clasp, completing the Pathmind circuit.
poly([(13, 49), (17, 47), (22, 52), (27, 54), (27, 59), (24, 59),
      (24, 57), (21, 57), (21, 55), (18, 55), (18, 53), (15, 53)], "outline")
poly([(15, 49), (18, 49), (22, 53), (27, 55), (27, 58), (24, 57),
      (24, 55), (21, 55), (21, 53), (18, 53)], "steel_dark")
poly([(16, 49), (19, 50), (22, 53), (27, 55), (27, 56), (24, 56),
      (24, 54), (21, 54), (21, 52), (18, 52)], "steel")
rect((18, 50, 20, 51), "steel_mid")

poly([(51, 49), (47, 47), (42, 52), (37, 54), (37, 59), (40, 59),
      (40, 57), (43, 57), (43, 55), (46, 55), (46, 53), (49, 53)], "outline")
poly([(49, 49), (46, 49), (42, 53), (37, 55), (37, 58), (40, 57),
      (40, 55), (43, 55), (43, 53), (46, 53)], "steel_dark")
poly([(48, 49), (45, 50), (42, 53), (37, 55), (37, 56), (40, 56),
      (40, 54), (43, 54), (43, 52), (46, 52)], "steel")
rect((44, 50, 46, 51), "steel_mid")


def node_housing(cx, cy):
    poly([(cx - 6, cy - 8), (cx + 5, cy - 8), (cx + 8, cy - 5),
          (cx + 8, cy + 5), (cx + 5, cy + 8), (cx - 5, cy + 8),
          (cx - 8, cy + 5), (cx - 8, cy - 5)], "outline")
    poly([(cx - 5, cy - 7), (cx + 4, cy - 7), (cx + 7, cy - 4),
          (cx + 7, cy + 4), (cx + 4, cy + 7), (cx - 4, cy + 7),
          (cx - 7, cy + 4), (cx - 7, cy - 4)], "steel_dark")
    rect((cx - 4, cy - 6, cx + 4, cy - 5), "steel_light")
    rect((cx - 6, cy - 4, cx - 5, cy + 3), "steel_mid")
    rect((cx + 5, cy - 3, cx + 6, cy + 4), "deep")
    rect((cx - 3, cy + 5, cx + 3, cy + 6), "deep")


def gem(cx, cy, dark, base, light):
    poly([(cx - 3, cy - 5), (cx + 3, cy - 5), (cx + 5, cy - 3),
          (cx + 5, cy + 3), (cx + 3, cy + 5), (cx - 3, cy + 5),
          (cx - 5, cy + 3), (cx - 5, cy - 3)], dark)
    poly([(cx - 3, cy - 4), (cx + 2, cy - 4), (cx + 4, cy - 2),
          (cx + 4, cy + 2), (cx + 2, cy + 4), (cx - 2, cy + 4),
          (cx - 4, cy + 2), (cx - 4, cy - 2)], base)
    rect((cx - 2, cy - 3, cx + 1, cy - 2), light)
    rect((cx - 3, cy - 2, cx - 2, cy + 1), light)
    rect((cx + 2, cy + 1, cx + 3, cy + 2), dark)
    rect((cx, cy + 3, cx + 2, cy + 3), dark)


node_housing(32, 10)
gem(32, 10, "blue_dark", "blue", "blue_light")
node_housing(11, 46)
gem(11, 46, "red_dark", "red", "red_light")
node_housing(53, 46)
gem(53, 46, "green_dark", "green", "green_light")

# Bottom clasp: a restrained fourth anchor, not another colored destination.
poly([(26, 54), (38, 54), (40, 56), (40, 62), (38, 63), (26, 63),
      (24, 61), (24, 56)], "outline")
poly([(27, 55), (37, 55), (39, 57), (39, 61), (37, 62), (27, 62),
      (25, 60), (25, 57)], "steel_dark")
rect((27, 57, 37, 60), "steel")
rect((28, 57, 36, 57), "steel_mid")
rect((29, 58, 35, 59), "steel_dark")

# Central compass body: an octagonal dial with a bright upper-left rim.
poly([(25, 20), (39, 20), (45, 26), (45, 40), (39, 46), (25, 46),
      (19, 40), (19, 26)], "outline")
poly([(26, 21), (38, 21), (44, 27), (44, 39), (38, 45), (26, 45),
      (20, 39), (20, 27)], "steel_dark")
poly([(27, 22), (37, 22), (43, 28), (43, 38), (37, 44), (27, 44),
      (21, 38), (21, 28)], "steel_mid")
poly([(27, 23), (37, 23), (42, 28), (42, 38), (37, 43), (27, 43),
      (22, 38), (22, 28)], "void")
rect((27, 22, 36, 23), "steel_light")
rect((21, 28, 22, 36), "steel_light")
rect((27, 42, 37, 43), "steel_dark")
rect((41, 29, 42, 37), "deep")

# Diagonal pathfinder needle: white points southwest, red points northeast.
poly([(31, 33), (34, 29), (36, 29), (36, 27), (38, 27), (38, 25),
      (41, 24), (40, 27), (40, 29), (38, 29), (38, 31), (34, 35)], "red_dark")
poly([(32, 33), (35, 29), (37, 29), (37, 27), (40, 25), (39, 28),
      (39, 30), (37, 30), (37, 32), (33, 35)], "red")
rect((37, 27, 38, 28), "red_light")

poly([(32, 34), (29, 38), (27, 38), (27, 40), (25, 40), (25, 42),
      (22, 43), (23, 40), (23, 38), (25, 38), (25, 36), (29, 32)], "steel_mid")
poly([(31, 34), (28, 38), (26, 38), (26, 40), (23, 42), (24, 39),
      (24, 37), (26, 37), (26, 35), (30, 32)], "white")
rect((30, 32, 32, 34), "steel_light")
rect((31, 33, 32, 34), "white")

# Save native indexed-color art and a nearest-neighbor preview.
native = im.convert("P", palette=Image.Palette.ADAPTIVE, colors=len(set(C.values())))
native.save(OUT_DIR / "pathmind-logo-64.png", optimize=False)
im.resize((512, 512), Image.Resampling.NEAREST).save(
    OUT_DIR / "pathmind-logo-preview-512.png", optimize=False
)
