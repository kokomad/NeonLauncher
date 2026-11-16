import re
import sys

path = sys.argv[1]
s = open(path, encoding="utf8").read()

# 1) Make MainActivity a HOME (launcher) activity
if "android.intent.category.HOME" not in s:
    s, n = re.subn(
        r'(<category\s+android:name="android\.intent\.category\.LAUNCHER"\s*/>)',
        r'\1\n                <category android:name="android.intent.category.HOME" />'
        r'\n                <category android:name="android.intent.category.DEFAULT" />',
        s,
        count=1,
    )
    if n != 1:
        sys.exit("ERROR: LAUNCHER category not found in manifest")

if "<application" not in s:
    sys.exit("ERROR: <application not found in manifest")

# 2) Allow listing launchable apps (Android 11+)
if "<queries>" not in s:
    block = (
        "<queries>\n"
        "        <intent>\n"
        '            <action android:name="android.intent.action.MAIN" />\n'
        '            <category android:name="android.intent.category.LAUNCHER" />\n'
        "        </intent>\n"
        "    </queries>\n\n    "
    )
    s = s.replace("<application", block + "<application", 1)

# 3) Permissions (added one by one, only if missing)
perms = [
    "android.permission.REQUEST_DELETE_PACKAGES",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.EXPAND_STATUS_BAR",
    "android.permission.VIBRATE",
]
for p in perms:
    if p not in s:
        s = s.replace(
            "<application",
            '<uses-permission android:name="%s" />\n\n    <application' % p,
            1,
        )

open(path, "w", encoding="utf8").write(s)
print("Manifest patched OK")
