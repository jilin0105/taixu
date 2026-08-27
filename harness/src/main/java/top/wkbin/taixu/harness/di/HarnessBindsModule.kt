package top.wkbin.taixu.harness.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import top.wkbin.taixu.harness.prompt.DefaultPrivilegeSectionRenderer
import top.wkbin.taixu.harness.prompt.PrivilegeSectionRenderer
import top.wkbin.taixu.harness.projection.LiveMessagePort
import top.wkbin.taixu.harness.projection.SessionMessageProjector

/** Harness 模块内的 Hilt 端点绑定：可测接缝在此收口。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HarnessBindsModule {

    @Binds
    @Singleton
    abstract fun bindPrivilegeSectionRenderer(
        impl: DefaultPrivilegeSectionRenderer,
    ): PrivilegeSectionRenderer

    /** 实时消息窄端口 → 会话消息投影器（CapabilityEventWriter / HarnessLoop 共用） */
    @Binds
    @Singleton
    abstract fun bindLiveMessagePort(
        impl: SessionMessageProjector,
    ): LiveMessagePort
}
