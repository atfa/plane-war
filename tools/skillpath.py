"""Locate `ascii.py` for the screenshot tools in this repo.

Search order: $SKILL_SCRIPTS -> the vendored copy in this `tools/` directory ->
the skill checkout used during development on the phone.
See https://github.com/atfa/dev-apk-on-android.
"""
import os
import sys

LEGACY = os.path.expanduser("~/fix/.agents/skills/android-on-device-apk/scripts")


def add():
    here = os.path.dirname(os.path.abspath(__file__))
    for p in (os.environ.get("SKILL_SCRIPTS", ""), here, LEGACY):
        if p and os.path.isfile(os.path.join(p, "ascii.py")):
            sys.path.insert(0, p)
            return
    sys.exit("ascii.py not found - set SKILL_SCRIPTS to the skill's scripts dir")
