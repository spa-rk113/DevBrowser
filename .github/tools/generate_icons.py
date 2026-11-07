
from pathlib import Path
from PIL import Image, ImageOps

ROOT = Path(__file__).resolve().parent.parent
SOURCE = ROOT / "assets" / "devbrowser-logo.png"
RES = ROOT / "app" / "src" / "main" / "res"

if not SOURCE.exists():
    raise FileNotFoundError(f"Logo missing: {SOURCE}")

image = Image.open(SOURCE).convert("RGBA")

# Square crop
image = ImageOps.fit(
    image,
    (1024, 1024),
    method=Image.Resampling.LANCZOS
)

# Legacy launcher icon sizes
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

# Adaptive icon foreground with safe padding
foreground = Image.new("RGBA", (432, 432), (0, 0, 0, 0))

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

foreground.save(drawable / "ic_launcher_foreground.png")

# Adaptive icon XML
adaptive = RES / "mipmap-anydpi-v26"
adaptive.mkdir(parents=True, exist_ok=True)

(adaptive / "ic_launcher.xml").write_text(
    """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
""",
    encoding="utf-8"
)

# Background color
values = RES / "values"
values.mkdir(parents=True, exist_ok=True)

(values / "ic_launcher_background.xml").write_text(
    """<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#11141B</color>
</resources>
""",
    encoding="utf-8"
)

print("DevBrowser icons generated successfully!")
