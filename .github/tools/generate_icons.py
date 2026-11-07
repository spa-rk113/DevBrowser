from pathlib import Path
from PIL import Image, ImageOps

# Find the actual repository root
ROOT = Path(__file__).resolve().parent.parent.parent

# Input logo
SOURCE = ROOT / "assets" / "devbrowser-logo.png"

# Android resources folder
RES = ROOT / "app" / "src" / "main" / "res"


def generate_icons():
    if not SOURCE.exists():
        raise FileNotFoundError(
            f"Logo not found: {SOURCE}"
        )

    print("Loading DevBrowser logo...")

    image = Image.open(SOURCE).convert("RGBA")

    # Create a square logo image
    image = ImageOps.fit(
        image,
        (1024, 1024),
        method=Image.Resampling.LANCZOS
    )

    # Generate launcher icons for all Android densities
    sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }

    for folder, size in sizes.items():
        directory = RES / folder
        directory.mkdir(parents=True, exist_ok=True)

        icon = image.resize(
            (size, size),
            Image.Resampling.LANCZOS
        )

        icon.save(directory / "ic_launcher.png")

        print(f"Generated {folder}: {size}x{size}")

    # Generate adaptive icon foreground
    foreground = Image.new(
        "RGBA",
        (432, 432),
        (0, 0, 0, 0)
    )

    logo = ImageOps.contain(
        image,
        (280, 280),
        method=Image.Resampling.LANCZOS
    )

    x = (432 - logo.width) // 2
    y = (432 - logo.height) // 2

    foreground.alpha_composite(logo, (x, y))

    drawable = RES / "drawable-nodpi"
    drawable.mkdir(parents=True, exist_ok=True)

    foreground.save(
        drawable / "ic_launcher_foreground.png"
    )

    # Generate adaptive icon XML
    adaptive = RES / "mipmap-anydpi-v26"
    adaptive.mkdir(parents=True, exist_ok=True)

    adaptive_xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
"""

    (adaptive / "ic_launcher.xml").write_text(
        adaptive_xml,
        encoding="utf-8"
    )

    # Generate background color resource
    values = RES / "values"
    values.mkdir(parents=True, exist_ok=True)

    background_xml = """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#11141B</color>
</resources>
"""

    (values / "ic_launcher_background.xml").write_text(
        background_xml,
        encoding="utf-8"
    )

    print("--------------------------------")
    print("DevBrowser icons generated!")
    print("--------------------------------")


if __name__ == "__main__":
    generate_icons()
