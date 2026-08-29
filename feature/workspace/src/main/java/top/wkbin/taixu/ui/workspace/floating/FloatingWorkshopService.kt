package top.wkbin.taixu.ui.workspace.floating

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import top.wkbin.taixu.core.database.WorkspaceEntity
import top.wkbin.taixu.core.database.WorkspaceRepository
import top.wkbin.taixu.ui.theme.TaiXuTheme
import top.wkbin.taixu.ui.workspace.WorkspaceBuildTaskCoordinator
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * 工坊桌面悬浮小窗后台服务。
 * 管理 WindowManager 悬浮图层的创建、更新与交互。
 */
@AndroidEntryPoint
class FloatingWorkshopService : Service() {

    @Inject
    lateinit var workspaceRepository: WorkspaceRepository

    @Inject
    lateinit var buildCoordinator: WorkspaceBuildTaskCoordinator

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var lifecycleOwner: FloatingWindowLifecycleOwner? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var isExpanded by mutableStateOf(false)
    private var windowParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: run {
            stopSelf()
            return
        }

        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val initialX = (displayMetrics.widthPixels - 140 * density).roundToInt()
        val initialY = (displayMetrics.heightPixels * 0.25f).roundToInt()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }
        windowParams = params

        val owner = FloatingWindowLifecycleOwner()
        lifecycleOwner = owner
        owner.onCreate()

        val view = ComposeView(this).apply {
            owner.attach(this)
            setContent {
                TaiXuTheme {
                    val projects: List<WorkspaceEntity> by workspaceRepository.observeAll().collectAsState(initial = emptyList())
                    val buildTaskState by buildCoordinator.state.collectAsState()
                    val buildProgress = buildTaskState?.progress
                    val activeBuildingProjectName = buildTaskState?.project?.name
                    val activeProjectName = activeBuildingProjectName ?: projects.firstOrNull()?.name

                    FloatingWorkshopView(
                        activeProjectName = activeProjectName,
                        buildProgress = buildProgress,
                        isExpanded = isExpanded,
                        onToggleExpanded = {
                            isExpanded = !isExpanded
                        },
                        onDragBy = { dx, dy ->
                            val currentParams = windowParams ?: return@FloatingWorkshopView
                            currentParams.x += dx.roundToInt()
                            currentParams.y += dy.roundToInt()
                            runCatching { windowManager?.updateViewLayout(this@apply, currentParams) }
                        },
                        onRestoreApp = {
                            restoreAppToDestination("workspace")
                        },
                        onOpenTerminal = {
                            restoreAppToDestination("terminal")
                        },
                        onOpenAgent = {
                            restoreAppToDestination("agent")
                        },
                        onClose = {
                            stopSelf()
                        },
                    )
                }
            }
        }
        composeView = view

        runCatching {
            windowManager?.addView(view, params)
            owner.onStart()
        }.onFailure {
            stopSelf()
        }
    }

    private fun restoreAppToDestination(destination: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_TO", destination)
        }
        if (launchIntent != null) {
            startActivity(launchIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleOwner?.onStop()
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null

        composeView?.let { view ->
            runCatching { windowManager?.removeView(view) }
        }
        composeView = null
        windowManager = null
        serviceScope.cancel()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, FloatingWorkshopService::class.java)
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingWorkshopService::class.java)
            context.stopService(intent)
        }
    }
}
