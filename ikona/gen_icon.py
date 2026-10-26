"""Generates the flat bell VectorDrawables: themed (monochrome) and notification icons, plus the circle under the 3D icon.

The coloured launcher foreground is a PNG built from the 3D artwork by gen_icon_3d.py."""
import os
import sys

root = sys.argv[1]
res = os.path.join(root, 'app', 'src', 'main', 'res', 'drawable')

BLACK = '#0D0D0D'
CIRCLE = '#171A1F'  # edge colour of the 3D icon's dark circle
# Bell silhouette in the 108x108 adaptive-icon viewport, centred on x=54
BELL_BODY = ('M54,35 C44,35 38,42 38,52 L38,62 C38,65 35.5,67 33,69 L33,71.5 '
             'L75,71.5 L75,69 C72.5,67 70,65 70,62 L70,52 C70,42 64,35 54,35 Z')
HANDLE = 'M54,29 a3.6,3.6 0 1,0 0.01,0 Z'   # knob on top
CLAPPER = 'M54,73 a4.2,4.2 0 1,0 0.01,0 Z'  # heart of the bell below the rim
STROKE = 2.6

# whole stack: a little smaller and lowered so it sits centred in the circle
STACK_SCALE = 0.86
STACK_DY = 3.5


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


files = {
    'ic_launcher_background.xml': vector(
        f'    <!-- Pełne tło w kolorze krawędzi koła z ikony 3D: maska launchera przycina je do koła -->\n'
        f'    <path\n        android:fillColor="{CIRCLE}"\n'
        f'        android:pathData="M0,0 H108 V108 H0 Z" />\n',
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

print('ok')
