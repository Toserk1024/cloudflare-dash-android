package io.github.toserk1024.cfdash

import android.app.Application

/**
 * 应用入口。
 * 全局依赖容器在进程启动时初始化一次，避免依赖 MainActivity 的 onCreate
 * （Activity 因配置变更重建时无需重复初始化，也便于后续扩展前台服务/WorkManager 等入口）。
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
