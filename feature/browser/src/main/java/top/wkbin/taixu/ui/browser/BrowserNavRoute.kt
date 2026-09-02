package top.wkbin.taixu.ui.browser

import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavOptions

/**
 * 内置浏览器页面的 Navigation 路由常量与跳转助手。
 *
 * 在 feature/navigation/TaiXuNavHost.kt 中把 `BrowserRoute.ROUTE` 注册到 NavHost。
 */
object BrowserRoute {
    const val ROUTE = "taixu-browser"

    fun NavController.openBrowser() {
        navigate(ROUTE, NavOptions.Builder().setLaunchSingleTop(true).build())
    }

    fun NavController.openBrowserFromTab() {
        navigate(ROUTE) {
            popUpTo(graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}
