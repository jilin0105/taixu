# -*- coding: utf-8 -*-
import io
f = r'C:\Users\wangk\Desktop\LinuxAIRuntime\core\datastore\src\main\java\top\wkbin\taixu\core\datastore\PreferenceFacades.kt'
t = io.open(f, encoding='utf-8').read().replace('\r\n', '\n')

old = '''@Singleton
class ToolPreferences @Inject constructor(private val store: SettingsDataStore) {

@Singleton
class BrowserPreferences @Inject constructor(private val store: SettingsDataStore) {
    fun defaultFamily() = store.browserDefaultFamily
    fun homeUrl() = store.browserHomeUrl
    fun coBrowsingEnabled() = store.browserCoBrowsingEnabled
    fun allowRemoteConnect() = store.browserAllowRemoteConnect
    fun allowEvalJs() = store.browserAllowEvalJs
    fun desktopUserAgent() = store.browserDesktopUserAgent
    fun maxCaptureBytes() = store.browserMaxCaptureBytes
    suspend fun setDefaultFamily(value: String) = store.setBrowserDefaultFamily(value)
    suspend fun setHomeUrl(value: String) = store.setBrowserHomeUrl(value)
    suspend fun setCoBrowsingEnabled(value: Boolean) = store.setBrowserCoBrowsingEnabled(value)
    suspend fun setAllowRemoteConnect(value: Boolean) = store.setBrowserAllowRemoteConnect(value)
    suspend fun setAllowEvalJs(value: Boolean) = store.setBrowserAllowEvalJs(value)
    suspend fun setDesktopUserAgent(value: Boolean) = store.setBrowserDesktopUserAgent(value)
    suspend fun setMaxCaptureBytes(value: Int) = store.setBrowserMaxCaptureBytes(value)
}
    fun toolAccessToken(distroId: String, toolId: String) = store.toolAccessToken(distroId, toolId)
    suspend fun setToolAccessToken(distroId: String, toolId: String, token: String?) =
        store.setToolAccessToken(distroId, toolId, token)
}'''

new = '''@Singleton
class ToolPreferences @Inject constructor(private val store: SettingsDataStore) {
    fun toolAccessToken(distroId: String, toolId: String) = store.toolAccessToken(distroId, toolId)
    suspend fun setToolAccessToken(distroId: String, toolId: String, token: String?) =
        store.setToolAccessToken(distroId, toolId, token)
}

@Singleton
class BrowserPreferences @Inject constructor(private val store: SettingsDataStore) {
    fun defaultFamily() = store.browserDefaultFamily
    fun homeUrl() = store.browserHomeUrl
    fun coBrowsingEnabled() = store.browserCoBrowsingEnabled
    fun allowRemoteConnect() = store.browserAllowRemoteConnect
    fun allowEvalJs() = store.browserAllowEvalJs
    fun desktopUserAgent() = store.browserDesktopUserAgent
    fun maxCaptureBytes() = store.browserMaxCaptureBytes
    suspend fun setDefaultFamily(value: String) = store.setBrowserDefaultFamily(value)
    suspend fun setHomeUrl(value: String) = store.setBrowserHomeUrl(value)
    suspend fun setCoBrowsingEnabled(value: Boolean) = store.setBrowserCoBrowsingEnabled(value)
    suspend fun setAllowRemoteConnect(value: Boolean) = store.setBrowserAllowRemoteConnect(value)
    suspend fun setAllowEvalJs(value: Boolean) = store.setBrowserAllowEvalJs(value)
    suspend fun setDesktopUserAgent(value: Boolean) = store.setBrowserDesktopUserAgent(value)
    suspend fun setMaxCaptureBytes(value: Int) = store.setBrowserMaxCaptureBytes(value)
}'''

if old in t:
    t = t.replace(old, new, 1)
    print("REPLACED ToolPreferences/BrowserPreferences structure")
else:
    print("NOT FOUND")
    # dump the region for debugging
    i = t.find('class ToolPreferences')
    print(t[i:i+800] if i >= 0 else "ToolPreferences not found")

io.open(f, 'w', encoding='utf-8', newline='\n').write(t)
print("DONE")
