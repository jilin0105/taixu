import io
p = "feature/settings/src/main/java/top/wkbin/taixu/ui/settings/SshSettingsViewModel.kt"
s = io.open(p, encoding="utf-8").read()  # universal newlines -> \n
old = '''    private fun vpnActiveNow(cm: ConnectivityManager): Boolean =
        cm.getNetworkCapabilities(cm.activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true'''
new = '''    private fun vpnActiveNow(cm: ConnectivityManager): Boolean = runCatching {
        cm.getNetworkCapabilities(cm.activeNetwork)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }.getOrDefault(false)'''
if old in s:
    s = s.replace(old, new, 1)
    # restore CRLF to match repo
    s = s.replace("\r\n", "\n").replace("\n", "\r\n")
    io.open(p, "w", encoding="utf-8", newline="").write(s)
    print("HARDENED_OK")
else:
    print("PATTERN_NOT_FOUND")
