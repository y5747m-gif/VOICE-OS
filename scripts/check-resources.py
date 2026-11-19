#!/usr/bin/env python3
"""Verify locale coverage/format args and the release manifest's standalone permissions."""
from collections import Counter
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app" / "src" / "main"
ANDROID = "{http://schemas.android.com/apk/res/android}"


def strings(folder):
    elements = ET.parse(APP / "res" / folder / "strings.xml").getroot().findall("string")
    result = {item.attrib["name"]: item for item in elements}
    assert len(result) == len(elements), f"Duplicate strings in {folder}"
    return result


en, ar = strings("values"), strings("values-ar")
translatable = {k for k, v in en.items() if v.get("translatable") != "false"}
assert translatable == ar.keys(), f"Locale mismatch: {sorted(translatable ^ ar.keys())}"
for key, value in ar.items():
    args = lambda item: Counter(re.findall(r"%\d+\$[ds]", item.text or ""))
    assert args(value) == args(en[key]), f"Format arguments differ: {key}"
used = set()
for kotlin in (APP / "java").rglob("*.kt"):
    used.update(re.findall(r"\bR\.string\.(\w+)", kotlin.read_text(encoding="utf-8")))
assert used <= en.keys(), f"Missing resources: {sorted(used - en.keys())}"

manifest = ET.parse(APP / "AndroidManifest.xml").getroot()
permissions = {e.get(ANDROID + "name") for e in manifest if e.tag.startswith("uses-permission")}
assert permissions == {"android.permission.RECORD_AUDIO", "android.permission.INTERNET"}, permissions
application = manifest.find("application")
assert application is not None and application.get(ANDROID + "allowBackup") == "false"
assert application.get(ANDROID + "usesCleartextTraffic") == "false"
assert not application.findall("service"), "MVP has no background services"
print(f"VOICE OS resources: {len(ar)} translated strings, {len(used)} Kotlin references, microphone and HTTPS-site permissions only.")
