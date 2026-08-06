package io.github.toserk1024.cfdash

import android.content.Context
import io.github.toserk1024.cfdash.data.api.CloudflareClient
import io.github.toserk1024.cfdash.data.repository.CloudflareRepository
import io.github.toserk1024.cfdash.data.storage.TokenStore

/**
 * 全局依赖容器（简单 Service Locator）
 * 在 Application/MainActivity 中初始化一次。
 */
object AppContainer {
    lateinit var tokenStore: TokenStore
        private set
    lateinit var repository: CloudflareRepository
        private set

    fun init(context: Context) {
        if (::tokenStore.isInitialized) return
        val appContext = context.applicationContext
        tokenStore = TokenStore(appContext)
        val client = CloudflareClient(tokenStore::getToken)
        repository = CloudflareRepository(client)
    }
}