package top.wkbin.taixu.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import top.wkbin.taixu.runtime.webchat.WebChatAgentGateway
import top.wkbin.taixu.webchat.TaiXuWebChatAgentGateway

@Module
@InstallIn(SingletonComponent::class)
abstract class WebChatModule {
    @Binds
    abstract fun bindWebChatAgentGateway(impl: TaiXuWebChatAgentGateway): WebChatAgentGateway
}
