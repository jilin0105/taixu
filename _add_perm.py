import io
p = "app/src/main/AndroidManifest.xml"
s = io.open(p, encoding="utf-8").read()
old = '    <uses-permission android:name="android.permission.INTERNET" />'
new = old + "\n" + '    <!-- SSH 设置页检测 VPN / 观察默认网络（getActiveNetwork / NetworkCallback） -->' + "\n" + '    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />'
if old in s and "android.permission.ACCESS_NETWORK_STATE" not in s:
    s = s.replace(old, new, 1)
    io.open(p, "w", encoding="utf-8", newline="").write(s)
    print("PERMISSION_ADDED")
else:
    print("ALREADY_PRESENT_OR_NOT_FOUND")
