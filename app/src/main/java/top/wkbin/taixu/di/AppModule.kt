package top.wkbin.taixu.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import top.wkbin.taixu.core.database.AppDatabase
import top.wkbin.taixu.core.database.ToolDao
import top.wkbin.taixu.core.database.InstallLogDao
import top.wkbin.taixu.core.database.InstallTaskDao
import top.wkbin.taixu.core.database.RuntimeDao
import top.wkbin.taixu.core.database.HarnessMessageDao
import top.wkbin.taixu.core.database.HarnessSessionDao
import top.wkbin.taixu.core.database.AiModelDao
import top.wkbin.taixu.core.database.WorkspaceDao
import top.wkbin.taixu.core.database.TerminalSessionDao
import top.wkbin.taixu.harness.WorkspaceFileAccess
import top.wkbin.taixu.core.tools.RuntimeManager
import top.wkbin.taixu.core.tools.RuntimeManagerImpl
import top.wkbin.taixu.core.tools.DependencyManager
import top.wkbin.taixu.core.tools.DependencyManagerImpl
import top.wkbin.taixu.runtime.shell.ProcessRegistry
import top.wkbin.taixu.runtime.shell.ProcessRegistryImpl
import top.wkbin.taixu.core.network.HttpClientProvider
import top.wkbin.taixu.core.network.FileDownloader
import top.wkbin.taixu.core.network.ResumableFileDownloader
import top.wkbin.taixu.runtime.LinuxRuntime
import top.wkbin.taixu.runtime.LinuxRuntimeImpl
import top.wkbin.taixu.runtime.shell.ProcessShellExecutor
import top.wkbin.taixu.runtime.shell.ShellExecutor
import top.wkbin.taixu.runtime.pty.PtyManager
import top.wkbin.taixu.runtime.pty.NativePtyManager
import top.wkbin.taixu.runtime.service.LocalServiceLauncher
import top.wkbin.taixu.runtime.service.LocalServiceLauncherImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient


@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        // 请求/存储时省略 null 字段：未配置的 reasoning_content 不会发给非推理模型
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        migrateLegacyDatabaseName(context)
        return Room.databaseBuilder(context, AppDatabase::class.java, "taixu.db")
            .build()
    }

    private fun migrateLegacyDatabaseName(context: Context) {
        val legacyName = "linux" + "ai.db"
        val currentName = "taixu.db"
        val legacy = context.getDatabasePath(legacyName)
        val current = context.getDatabasePath(currentName)
        if (!legacy.exists() || current.exists()) return
        current.parentFile?.mkdirs()
        listOf("", "-wal", "-shm", "-journal").forEach { suffix ->
            val source = context.getDatabasePath(legacyName + suffix)
            if (!source.exists()) return@forEach
            val target = context.getDatabasePath(currentName + suffix)
            if (!source.renameTo(target)) {
                source.copyTo(target, overwrite = false)
                source.delete()
            }
        }
    }

    @Provides
    @Singleton
    fun provideToolDao(database: AppDatabase): ToolDao = database.toolDao()

    @Provides
    @Singleton
    fun provideInstallLogDao(database: AppDatabase): InstallLogDao = database.installLogDao()

    @Provides
    @Singleton
    fun provideInstallTaskDao(database: AppDatabase): InstallTaskDao = database.installTaskDao()

    @Provides
    @Singleton
    fun provideRuntimeDao(database: AppDatabase): RuntimeDao = database.runtimeDao()

    @Provides
    @Singleton
    fun provideHarnessMessageDao(database: AppDatabase): HarnessMessageDao = database.harnessMessageDao()

    @Provides
    @Singleton
    fun provideHarnessSessionDao(database: AppDatabase): HarnessSessionDao = database.harnessSessionDao()

    @Provides
    @Singleton
    fun provideAiModelDao(database: AppDatabase): AiModelDao = database.aiModelDao()

    @Provides
    @Singleton
    fun provideWorkspaceDao(database: AppDatabase): WorkspaceDao = database.workspaceDao()

    @Provides
    @Singleton
    fun provideTerminalSessionDao(database: AppDatabase): TerminalSessionDao = database.terminalSessionDao()

    @Provides
    @Singleton
    fun provideWorkspaceFileAccess(pathManager: top.wkbin.taixu.runtime.RuntimePathManager): WorkspaceFileAccess =
        WorkspaceFileAccess(pathManager.workspaceDir)

    @Provides
    @Singleton
    fun provideRuntimeManager(impl: RuntimeManagerImpl): RuntimeManager = impl

    @Provides
    @Singleton
    fun provideDependencyManager(impl: DependencyManagerImpl): DependencyManager = impl

    @Provides
    @Singleton
    fun provideProcessRegistry(impl: ProcessRegistryImpl): ProcessRegistry = impl

    @Provides
    @Singleton
    fun provideOkHttpClient(provider: HttpClientProvider): OkHttpClient = provider.create()

    @Provides
    @Singleton
    fun provideKtorHttpClient(
        provider: HttpClientProvider,
        okHttpClient: OkHttpClient,
    ): HttpClient = provider.createKtorClient(okHttpClient)

    @Provides
    @Singleton
    fun provideFileDownloader(impl: ResumableFileDownloader): FileDownloader = impl

    @Provides
    @Singleton
    fun provideShellExecutor(impl: ProcessShellExecutor): ShellExecutor = impl

    @Provides
    @Singleton
    fun providePtyManager(impl: NativePtyManager): PtyManager = impl

    @Provides
    @Singleton
    fun provideLinuxRuntime(impl: LinuxRuntimeImpl): LinuxRuntime = impl

    @Provides
    @Singleton
    fun provideLocalServiceLauncher(impl: LocalServiceLauncherImpl): LocalServiceLauncher = impl
}
