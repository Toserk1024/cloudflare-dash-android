package com.java.myapplication

import android.content.Context
import com.java.myapplication.data.api.CloudflareClient
import com.java.myapplication.data.repository.CloudflareRepository
import com.java.myapplication.data.storage.TokenStore

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