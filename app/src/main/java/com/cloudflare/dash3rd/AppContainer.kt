package com.cloudflare.dash3rd

import android.content.Context
import com.cloudflare.dash3rd.data.api.CloudflareClient
import com.cloudflare.dash3rd.data.repository.CloudflareRepository
import com.cloudflare.dash3rd.data.storage.TokenStore

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