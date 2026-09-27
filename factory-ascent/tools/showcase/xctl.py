#!/usr/bin/env python3
"""Tiny X11 driver for the screenshot run: click, type, press keys, capture the screen."""
import sys
import time
from Xlib import X, XK, display
from Xlib.ext import xtest
from PIL import Image

d = display.Display(":99")
root = d.screen().root


def shot(path):
    g = root.get_geometry()
    raw = root.get_image(0, 0, g.width, g.height, X.ZPixmap, 0xFFFFFFFF)
    Image.frombytes("RGB", (g.width, g.height), raw.data, "raw", "BGRX").save(path)


def click(x, y, button=1):
    xtest.fake_input(d, X.MotionNotify, x=x, y=y)
    d.sync()
    time.sleep(0.2)
    xtest.fake_input(d, X.ButtonPress, button)
    d.sync()
    time.sleep(0.08)
    xtest.fake_input(d, X.ButtonRelease, button)
    d.sync()


SHIFTED = {"@": "2", "~": "grave", "_": "minus", ":": "semicolon", "{": "bracketleft", "}": "bracketright", '"': "apostrophe"}
NAMES = {" ": "space", "/": "slash", "-": "minus", ".": "period", ",": "comma", "=": "equal", "[": "bracketleft",
         "]": "bracketright", "\n": "Return"}


def key(name, shift=False):
    code = d.keysym_to_keycode(XK.string_to_keysym(name))
    shift_code = d.keysym_to_keycode(XK.string_to_keysym("Shift_L"))
    if shift:
        xtest.fake_input(d, X.KeyPress, shift_code)
    xtest.fake_input(d, X.KeyPress, code)
    d.sync()
    time.sleep(0.05)
    xtest.fake_input(d, X.KeyRelease, code)
    if shift:
        xtest.fake_input(d, X.KeyRelease, shift_code)
    d.sync()
    time.sleep(0.05)


def type_text(text):
    for ch in text:
        if ch in SHIFTED:
            key(SHIFTED[ch], True)
        elif ch in NAMES:
            key(NAMES[ch])
        elif ch.isupper():
            key(ch.lower(), True)
        else:
            key(ch)


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "shot":
        shot(sys.argv[2])
    elif cmd == "click":
        click(int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4]) if len(sys.argv) > 4 else 1)
    elif cmd == "key":
        key(sys.argv[2])
    elif cmd == "type":
        type_text(sys.argv[2])
