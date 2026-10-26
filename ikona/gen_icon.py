"""Generates the 3-layer bell launcher icon (SVG + Android VectorDrawables) from one geometry."""
import os
import sys

root = sys.argv[1]
res = os.path.join(root, 'app', 'src', 'main', 'res', 'drawable')
out_dir = os.path.join(root, 'ikona')
os.makedirs(out_dir, exist_ok=True)

BLACK = '#0D0D0D'
# Bell silhouette in the 108x108 adaptive-icon viewport, centred on x=54
BELL_BODY = ('M54,35 C44,35 38,42 38,52 L38,62 C38,65 35.5,67 33,69 L33,71.5 '
             'L75,71.5 L75,69 C72.5,67 70,65 70,62 L70,52 C70,42 64,35 54,35 Z')
# Back layers: same dome but straight sides (no flared rim), so nothing peeks out beside the front bell
BELL_BODY_BACK = 'M54,35 C44,35 38,42 38,52 L38,71.5 L70,71.5 L70,52 C70,42 64,35 54,35 Z'
HANDLE = 'M54,29 a3.6,3.6 0 1,0 0.01,0 Z'   # knob on top
CLAPPER = 'M54,73 a4.2,4.2 0 1,0 0.01,0 Z'  # heart of the bell below the rim
STROKE = 2.6

# back -> front: (colour, translateY, scale)
LAYERS = [
    ('#CBE896', -14, 0.86),
    ('#F9E784', -7, 0.93),
    ('#F4B8E4', 4, 1.0),
]
# whole stack: a little smaller and lowered so it sits centred in the circle
STACK_SCALE = 0.86
STACK_DY = 3.5
CIRCLE_R = 36  # 72dp circle = always fully inside any launcher mask, so the icon is truly round


def svg(with_circle=True):
    parts = []
    for n, (colour, dy, sc) in enumerate(LAYERS):
        body = BELL_BODY if n == len(LAYERS) - 1 else BELL_BODY_BACK
        parts.append(
            f'    <g transform="translate(0 {dy}) translate(54 54) scale({sc}) translate(-54 -54)">\n'
            f'      <path d="{HANDLE}" fill="{colour}" stroke="{BLACK}" stroke-width="{STROKE}"/>\n'
            f'      <path d="{CLAPPER}" fill="{colour}" stroke="{BLACK}" stroke-width="{STROKE}"/>\n'
            f'      <path d="{body}" fill="{colour}" stroke="{BLACK}" stroke-width="{STROKE}" stroke-linejoin="round"/>\n'
            f'    </g>')
    circle = f'  <circle cx="54" cy="54" r="{CIRCLE_R}" fill="{BLACK}"/>\n' if with_circle else ''
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="512" height="512">\n'
            f'{circle}'
            f'  <g transform="translate(0 {STACK_DY}) translate(54 54) scale({STACK_SCALE}) translate(-54 -54)">\n'
            + '\n'.join(parts) + '\n  </g>\n</svg>\n')


def vector(body, comment):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- {comment} -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n'
            '    android:height="108dp"\n'
            '    android:viewportWidth="108"\n'
            '    android:viewportHeight="108">\n'
            f'{body}'
            '</vector>\n')


def bell_paths(fill, indent, stroke=True, body=BELL_BODY):
    s = (f'\n{indent}android:strokeColor="{BLACK}"\n{indent}android:strokeWidth="{STROKE}"' if stroke else '')
    out = ''
    for d in (HANDLE, CLAPPER):
        out += f'{indent[:-4]}<path\n{indent}android:fillColor="{fill}"{s}\n{indent}android:pathData="{d}" />\n'
    out += (f'{indent[:-4]}<path\n{indent}android:fillColor="{fill}"{s}'
            + (f'\n{indent}android:strokeLineJoin="round"' if stroke else '')
            + f'\n{indent}android:pathData="{body}" />\n')
    return out


def stack_group(inner):
    return ('    <group\n'
            '        android:pivotX="54"\n'
            '        android:pivotY="54"\n'
            f'        android:scaleX="{STACK_SCALE}"\n'
            f'        android:scaleY="{STACK_SCALE}"\n'
            f'        android:translateY="{STACK_DY}">\n'
            f'{inner}'
            '    </group>\n')


foreground = ''
for n, ((colour, dy, sc), name) in enumerate(zip(LAYERS, ['tył: zieleń', 'środek: żółć', 'przód: róż'])):
    foreground += (f'        <!-- {name} -->\n'
                   '        <group\n'
                   '            android:pivotX="54"\n'
                   '            android:pivotY="54"\n'
                   f'            android:scaleX="{sc}"\n'
                   f'            android:scaleY="{sc}"\n'
                   f'            android:translateY="{dy}">\n'
                   + bell_paths(colour, ' ' * 16, body=BELL_BODY if n == len(LAYERS) - 1 else BELL_BODY_BACK) +
                   '        </group>\n')

files = {
    'ic_launcher_foreground.xml': vector(stack_group(foreground),
                                         'Trzy nałożone dzwonki (kaskada jak karty serii). Generowane przez ikona/gen_icon.py'),
    'ic_launcher_background.xml': vector(
        f'    <!-- Czarne koło r=36: mieści się w każdej masce launchera, więc ikona jest zawsze okrągła -->\n'
        f'    <path\n        android:fillColor="{BLACK}"\n'
        f'        android:pathData="M54,18 a36,36 0 1,0 0.01,0 Z" />\n',
        'Tło ikony'),
    # Themed icons (Android 13+) and notification icons use only alpha: single bell silhouette
    'ic_launcher_monochrome.xml': vector(stack_group(
        '        <group android:pivotX="54" android:pivotY="54" android:translateY="-4">\n'
        + bell_paths('#FFFFFFFF', ' ' * 16, stroke=False) + '        </group>\n'),
        'Ikona tematyczna (Android 13+)'),
    'ic_notification.xml': vector(stack_group(
        '        <group android:pivotX="54" android:pivotY="54" android:scaleX="1.25" android:scaleY="1.25" android:translateY="-4">\n'
        + bell_paths('#FFFFFFFF', ' ' * 16, stroke=False) + '        </group>\n'),
        'Mała ikona powiadomień: tylko kanał alfa'),
}
for name, content in files.items():
    open(os.path.join(res, name), 'w', encoding='utf-8').write(content)

open(os.path.join(out_dir, 'budzik-ikona.svg'), 'w', encoding='utf-8').write(svg())
print('ok')
