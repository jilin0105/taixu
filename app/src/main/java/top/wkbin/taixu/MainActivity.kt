package top.wkbin.taixu

import top.wkbin.taixu.ui.components.RuntimeAlertDialog

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import top.wkbin.taixu.ui.components.RuntimeButton as Button
import top.wkbin.taixu.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import top.wkbin.taixu.ui.components.RuntimeLinearProgressIndicator as LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import top.wkbin.taixu.ui.components.RuntimeTextButton as TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import top.wkbin.taixu.core.model.AppUpdateInfo
import top.wkbin.taixu.ui.components.RuntimeIcon
import top.wkbin.taixu.ui.components.RuntimeIconName
import top.wkbin.taixu.ui.navigation.TaiXuNavHost
import top.wkbin.taixu.ui.theme.TaiXuTheme

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @javax.inject.Inject
    lateinit var settingsDataStore: top.wkbin.taixu.core.datastore.AppearancePreferences

    @javax.inject.Inject
    lateinit var appUpdateManager: top.wkbin.taixu.core.network.AppUpdateManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    private var notificationPermissionCheckScheduled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by settingsDataStore.themeMode.collectAsStateWithLifecycle(initialValue = "system")
            val themeStyle by settingsDataStore.themeStyle.collectAsStateWithLifecycle(initialValue = "xuantong")
            val chengmingBackgroundUri by settingsDataStore.chengmingBackgroundUri.collectAsStateWithLifecycle(initialValue = null)
            val pageScale by settingsDataStore.appFontScale.collectAsStateWithLifecycle(initialValue = 1f)
            val systemDark = isSystemInDarkTheme()
            val systemDensity = LocalDensity.current
            val isDark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }

            CompositionLocalProvider(
                LocalDensity provides Density(
                    density = systemDensity.density * pageScale.coerceIn(0.8f, 1.3f),
                    fontScale = systemDensity.fontScale,
                ),
            ) {
                TaiXuTheme(
                    style = top.wkbin.taixu.ui.theme.ThemeStyle.fromId(themeStyle),
                    darkTheme = isDark,
                    backgroundUri = chengmingBackgroundUri,
                ) {
                val onboardingViewModel: OnboardingViewModel = hiltViewModel()
                LaunchedEffect(Unit) { onboardingViewModel.restoreInstalledState() }
                val onboarding by onboardingViewModel.status.collectAsStateWithLifecycle()

                // 启动时静默检查更新
                var updateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
                var downloadProgress by remember { mutableStateOf<Float?>(null) }
                var isDownloading by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()

                LaunchedEffect(onboarding.completed) {
                    if (onboarding.completed) {
                        val autoCheck = settingsDataStore.autoCheckUpdates.first()
                        if (autoCheck) {
                            val res = appUpdateManager.checkUpdate("0.3.0")
                            res.onSuccess { info ->
                                if (info.hasUpdate) updateInfo = info
                            }
                        }
                    }
                }

                updateInfo?.let { info ->
                    RuntimeAlertDialog(
                        onDismissRequest = { if (!isDownloading) updateInfo = null },
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                RuntimeIcon(
                                    name = RuntimeIconName.Refresh,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(stringResource(R.string.taixu_update_available, info.latestVersion), fontWeight = FontWeight.Bold)
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.taixu_update_versions, info.currentVersion, info.latestVersion),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                                if (info.releaseNotes.isNotBlank()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            text = info.releaseNotes,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(12.dp),
                                        )
                                    }
                                }
                                if (isDownloading) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(stringResource(R.string.taixu_update_downloading_package), style = MaterialTheme.typography.labelMedium)
                                        if (downloadProgress != null) {
                                            LinearProgressIndicator(
                                                progress = { downloadProgress ?: 0f },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        } else {
                                            LinearProgressIndicator(
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            val apkUrl = info.apkDownloadUrl
                            if (apkUrl != null) {
                                Button(
                                    onClick = {
                                        isDownloading = true
                                        downloadProgress = 0f
                                        scope.launch {
                                            val res = appUpdateManager.downloadApk(apkUrl) { dl, tot ->
                                                if (tot != null && tot > 0) downloadProgress = dl.toFloat() / tot.toFloat()
                                            }
                                            isDownloading = false
                                            res.onSuccess { apkFile ->
                                                downloadProgress = 1f
                                                appUpdateManager.installApk(apkFile)
                                                updateInfo = null
                                            }
                                        }
                                    },
                                    enabled = !isDownloading,
                                ) {
                                    Text(stringResource(if (isDownloading) R.string.taixu_downloading else R.string.taixu_update_now))
                                }
                            } else {
                                Button(
                                    onClick = {
                                        runCatching {
                                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl)))
                                        }
                                        updateInfo = null
                                    },
                                ) {
                                    Text(stringResource(R.string.taixu_open_github))
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { updateInfo = null },
                                enabled = !isDownloading,
                            ) {
                                Text(stringResource(R.string.taixu_later))
                            }
                        },
                    )
                }

                when {
                    !onboarding.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                    onboarding.completed -> TaiXuNavHost()
                    else -> OnboardingScreen(onboardingViewModel)
                }
                }
            }
        }
    }

    override fun onPostResume() {
        super.onPostResume()
        if (notificationPermissionCheckScheduled) return
        notificationPermissionCheckScheduled = true

        // Runtime permission dialogs are most reliable after the first page is resumed and drawn.
        // First launch also restores onboarding/theme state, so requesting from onCreate can be
        // swallowed by some Android builds before the Activity becomes fully interactive.
        window.decorView.post {
            if (!isFinishing && !isDestroyed) {
                requestNotificationPermissionIfNeeded()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
