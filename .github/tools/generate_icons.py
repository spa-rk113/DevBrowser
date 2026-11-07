from pathlib import Path
from PIL import Image, ImageOps, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "assets" / "devbrowser-logo.png"
RES = ROOT / "app" / "src" / "main" / "res"

BACKGROUND = (17, 20, 27, 255)


def load_logo():
    if not SOURCE.is_file():
        raise FileNotFoundError(f"Missing logo: {SOURCE}")

    with Image.open(SOURCE) as source:
        source.load()
        return source.convert("RGBA")


def make_square_icon(logo, size, padding=0):
    canvas = Image.new("RGBA", (size, size), BACKGROUND)

    available = size - (padding * 2)

    fitted = ImageOps.contain(
        logo,
        (available, available),
        method=Image.Resampling.LANCZOS
    )

    x = (size - fitted.width) // 2
    y = (size - fitted.height) // 2

    canvas.alpha_composite(fitted, (x, y))
    return canvas


def save_png(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, format="PNG", optimize=True)


def generate_icons():
    print("Generating DevBrowser icons...")

    logo = load_logo()

    # Legacy launcher icons
    sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }

    for folder, size in sizes.items():
        icon = make_square_icon(logo, size, padding=size // 10)

        save_png(
            icon,
            RES / folder / "ic_launcher.png"
        )

        save_png(
            icon,
            RES / folder / "ic_launcher_round.png"
        )

        print(f"Created {folder}: {size}x{size}")

    # Adaptive icon foreground
    foreground = Image.new(
        "RGBA",
        (432, 432),
        (0, 0, 0, 0)
    )

    fitted = ImageOps.contain(
        logo,
        (300, 300),
        method=Image.Resampling.LANCZOS
    )

    x = (432 - fitted.width) // 2
    y = (432 - fitted.height) // 2

    foreground.alpha_composite(fitted, (x, y))

    save_png(
        foreground,
        RES / "drawable-nodpi" / "ic_launcher_foreground.png"
    )

    # Adaptive icon XML
    adaptive_dir = RES / "mipmap-anydpi-v26"
    adaptive_dir.mkdir(parents=True, exist_ok=True)

    adaptive_xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
"""

    (adaptive_dir / "ic_launcher.xml").write_text(
        adaptive_xml,
        encoding="utf-8"
    )

    (adaptive_dir / "ic_launcher_round.xml").write_text(
        adaptive_xml,
        encoding="utf-8"
    )

    # Background color
    values_dir = RES / "values"
    values_dir.mkdir(parents=True, exist_ok=True)

    background_xml = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#11141B</color>
</resources>
"""

    (values_dir / "ic_launcher_background.xml").write_text(
        background_xml,
        encoding="utf-8"
    )

    print("DevBrowser icons generated successfully!")


if __name__ == "__main__":
    generate_icons()
