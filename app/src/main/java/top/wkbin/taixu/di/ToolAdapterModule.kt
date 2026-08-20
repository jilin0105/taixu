package top.wkbin.taixu.di

import top.wkbin.taixu.core.tools.ToolRuntimeAdapter
import top.wkbin.taixu.runtime.tools.CodexToolInstaller
import top.wkbin.taixu.runtime.tools.HelloToolInstaller
import top.wkbin.taixu.runtime.tools.HermesToolInstaller
import top.wkbin.taixu.runtime.tools.OpenClawToolInstaller
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
abstract class ToolAdapterModule {
    @Binds
    @IntoSet
    abstract fun bindHelloToolInstaller(installer: HelloToolInstaller): ToolRuntimeAdapter

    @Binds
    @IntoSet
    abstract fun bindCodexToolInstaller(installer: CodexToolInstaller): ToolRuntimeAdapter

    @Binds
    @IntoSet
    abstract fun bindOpenClawToolInstaller(installer: OpenClawToolInstaller): ToolRuntimeAdapter

    @Binds
    @IntoSet
    abstract fun bindHermesToolInstaller(installer: HermesToolInstaller): ToolRuntimeAdapter
}
