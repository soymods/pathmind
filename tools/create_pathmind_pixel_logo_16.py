from pathlib import Path

from PIL import Image, ImageDraw


OUT_DIR = Path("artifacts/pathmind-logo")
OUT_DIR.mkdir(parents=True, exist_ok=True)

# Exact palette sampled from the user's finished 32x32 source.
C = {
    "clear": (0, 0, 0, 0),
    "outline": (23, 27, 33, 255),
    "deep": (41, 46, 53, 255),
    "steel_dark": (66, 71, 78, 255),
    "void": (12, 15, 20, 255),
    "steel": (112, 117, 122, 255),
    "steel_light": (199, 203, 204, 255),
    "white": (255, 255, 255, 255),
    "blue_dark": (18, 60, 158, 255),
    "blue": (23, 96, 232, 255),
    "blue_light": (85, 144, 255, 255),
    "red_dark": (167, 18, 26, 255),
    "red": (239, 32, 40, 255),
    "red_light": (255, 87, 72, 255),
    "green_dark": (22, 134, 42, 255),
    "green": (39, 211, 67, 255),
    "green_light": (105, 239, 104, 255),
}

im = Image.new("RGBA", (16, 16), C["clear"])
d = ImageDraw.Draw(im)


def poly(points, color):
    d.polygon(points, fill=C[color])


def rect(box, color):
    d.rectangle(box, fill=C[color])


# Outer circuit, reduced to one-pixel highlights inside a two-pixel silhouette.
d.line([(6, 3), (4, 5), (2, 9)], fill=C["outline"], width=2)
d.line([(9, 3), (11, 5), (13, 9)], fill=C["outline"], width=2)
d.line([(3, 11), (5, 13), (7, 14)], fill=C["outline"], width=1)
d.line([(12, 11), (10, 13), (8, 14)], fill=C["outline"], width=1)
d.line([(6, 3), (4, 5), (3, 8)], fill=C["steel"], width=1)
d.line([(9, 3), (11, 5), (12, 8)], fill=C["steel_dark"], width=1)
rect((4, 5, 4, 5), "steel_light")


def top_node():
    poly([(6, 1), (9, 1), (10, 2), (10, 4), (9, 5), (6, 5),
          (5, 4), (5, 2)], "outline")
    rect((6, 2, 9, 4), "steel_dark")
    rect((6, 2, 8, 2), "steel_light")
    poly([(7, 2), (9, 2), (9, 4), (7, 4), (6, 3)], "blue_dark")
    rect((7, 3, 8, 4), "blue")
    rect((7, 3, 7, 3), "blue_light")


def left_node():
    poly([(2, 9), (4, 9), (5, 10), (5, 13), (4, 14), (2, 14),
          (1, 13), (1, 10)], "outline")
    rect((2, 10, 4, 13), "steel_dark")
    rect((2, 10, 4, 10), "steel_light")
    poly([(2, 11), (4, 11), (4, 13), (2, 13)], "red_dark")
    rect((3, 11, 4, 12), "red")
    rect((3, 11, 3, 11), "red_light")


def right_node():
    poly([(11, 9), (13, 9), (14, 10), (14, 13), (13, 14), (11, 14),
          (10, 13), (10, 10)], "outline")
    rect((11, 10, 13, 13), "steel_dark")
    rect((11, 10, 13, 10), "steel_light")
    poly([(11, 11), (13, 11), (13, 13), (11, 13)], "green_dark")
    rect((11, 11, 12, 12), "green")
    rect((11, 11, 11, 11), "green_light")


top_node()
left_node()
right_node()

# Bottom clasp completes the silhouette and reaches the one-pixel safe margin.
poly([(6, 13), (9, 13), (10, 14), (9, 14), (9, 15 - 1),
      (6, 15 - 1), (5, 14)], "outline")
rect((6, 13, 9, 13), "steel_dark")
rect((7, 13, 8, 13), "steel")

# Central compass: simplified octagon, high-contrast rim, diagonal needle.
poly([(7, 5), (9, 5), (11, 7), (11, 10), (9, 12), (7, 12),
      (5, 10), (5, 7)], "outline")
poly([(7, 6), (9, 6), (10, 7), (10, 10), (9, 11), (7, 11),
      (6, 10), (6, 7)], "steel_dark")
rect((7, 6, 9, 6), "steel_light")
rect((6, 7, 6, 9), "steel_light")
rect((7, 7, 9, 10), "void")
rect((10, 8, 10, 10), "deep")
rect((7, 11, 9, 11), "deep")

# Two-pixel arrowheads are the minimum that retain the red/white compass idea.
rect((8, 8, 8, 8), "steel_light")
rect((9, 7, 9, 7), "red")
rect((9, 8, 9, 8), "red_dark")
rect((7, 9, 7, 9), "white")
rect((6, 10, 6, 10), "steel")

native_path = OUT_DIR / "pathmind-logo-16.png"
preview_path = OUT_DIR / "pathmind-logo-16-preview-512.png"
im.save(native_path, optimize=False)
im.resize((512, 512), Image.Resampling.NEAREST).save(preview_path, optimize=False)
