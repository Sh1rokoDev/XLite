"""Generate every XLite platform icon from one geometry definition. Requires Pillow."""
from pathlib import Path
import re
from PIL import Image, ImageDraw
ROOT = Path(__file__).resolve().parent.parent
BLUE = '#2563EB'
POINTS = [(5,5),(9,5),(12,10),(15,5),(19,5),(14,13),(19,21),(15,21),(12,16),(9,21),(5,21),(10,13)]
PATH = 'M' + ' L'.join(f'{x} {y}' for x,y in POINTS) + ' Z'
def icon(size, round_icon=False):
    scale=4
    canvas=Image.new('RGBA',(size*scale,size*scale))
    draw=ImageDraw.Draw(canvas)
    bounds=(0,0,size*scale-1,size*scale-1)
    if round_icon: draw.ellipse(bounds,fill=BLUE)
    else: draw.rounded_rectangle(bounds,radius=size*scale*.28,fill=BLUE)
    # Matches BrandMark: 86% viewport, centered inside the blue tile.
    draw.polygon([((x/24*.86+.07)*size*scale,(y/24*.86+.07)*size*scale) for x,y in POINTS],fill='white')
    return canvas.resize((size,size),Image.Resampling.LANCZOS)
image=icon(1024)
image.save(ROOT/'branding/xlite-icon.png')
svg=f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><rect width="24" height="24" rx="6.72" fill="{BLUE}"/><path transform="translate(1.68 1.68) scale(.86)" d="{PATH}" fill="white"/></svg>\n'
(ROOT/'branding/xlite-icon.svg').write_text(svg,encoding='utf-8')
image.resize((512,512),Image.Resampling.LANCZOS).save(ROOT/'desktopApp/src/main/resources/xlite.png')
image.save(ROOT/'desktopApp/src/main/resources/xlite.ico',sizes=[(16,16),(20,20),(24,24),(32,32),(48,48),(64,64),(128,128),(256,256)])
res=ROOT/'androidApp/src/main/res'
for dpi,size in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
    for suffix in ['', '_round']:
        icon(size,suffix=='_round').save(res/f'mipmap-{dpi}/ic_launcher{suffix}.png')
# Foreground fits within the adaptive icon safe area.
(res/'drawable/ic_launcher_foreground.xml').write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <group android:scaleX="2.2" android:scaleY="2.2" android:translateX="27.6" android:translateY="27.6">
        <path android:fillColor="#FFFFFF" android:pathData="{PATH}" />
    </group>
</vector>
''',encoding='utf-8')
(res/'drawable/ic_stat_xlite.xml').write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFF" android:pathData="{PATH}" />
</vector>
''',encoding='utf-8')
f=ROOT/'shared/src/commonMain/kotlin/com/xlite/downloader/ui/icons/XLIcons.kt'
s=f.read_text(encoding='utf-8')
s=re.sub(r'icon\("XLite", "[^"]+"\)',lambda _:f'icon("XLite", "{PATH}")',s)
f.write_text(s,encoding='utf-8')
print('Generated SVG, Compose mark, desktop PNG/ICO, Android launcher and notification icons.')
