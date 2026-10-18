package io.github.toserk1024.cfdash.data.state

import io.github.toserk1024.cfdash.data.repository.CloudflareRepository

/**
 * Zone 设置的内存缓存（zoneId → setting 名 → 值）。
 * 配合 `GET /zones/{id}/settings` 全量接口，把"切一次域名要发 16+ 个单项请求"降到 1 个。
 */
object ZoneSettingsCache {

    private val cache = mutableMapOf<String, MutableMap<String, String>>()

    @Synchronized
    fun snapshot(zoneId: String): Map<String, String>? = cache[zoneId]?.toMap()

    @Synchronized
    fun putAll(zoneId: String, values: Map<String, String>) {
        if (values.isEmpty()) return
        cache.getOrPut(zoneId) { mutableMapOf() }.putAll(values)
    }

    @Synchronized
    fun put(zoneId: String, setting: String, value: String) {
        cache.getOrPut(zoneId) { mutableMapOf() }[setting] = value
    }

    @Synchronized
    fun invalidate(zoneId: String) {
        cache.remove(zoneId)
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }
}

/** 设置加载结果（值为 设置名 → 值；errors 为加载失败的单项说明，用于界面提示） */
data class ZoneSettingsLoadResult(
    val values: Map<String, String>,
    val errors: List<String>
)

/**
 * 统一加载 Zone 设置：
 * 1. 命中缓存直接用；
 * 2. 未命中先尝试全量接口 `GET /zones/{id}/settings`（一次拿全部字符串型设置）；
 * 3. 全量里缺失的项（如 nel 这类对象型设置）回退为单项请求；
 * 4. 单项失败不阻塞其他，错误汇总到 [ZoneSettingsLoadResult.errors]。
 */
object ZoneSettingsLoader {

    suspend fun load(
        repository: CloudflareRepository,
        zoneId: String,
        keys: List<String>
    ): ZoneSettingsLoadResult {
        val values = LinkedHashMap<String, String>()
        val errors = mutableListOf<String>()

        val full = ZoneSettingsCache.snapshot(zoneId) ?: try {
            repository.getAllZoneSettings(zoneId).also { ZoneSettingsCache.putAll(zoneId, it) }
        } catch (e: Exception) {
            // 全量接口不可用（权限/接口变更）时降级为单项请求，不直接报错
            emptyMap()
        }

        keys.forEach { key -> full[key]?.let { values[key] = it } }

        keys.filterNot { values.containsKey(it) }.forEach { key ->
            try {
                val value = repository.getZoneSetting(zoneId, key).value
                values[key] = value
                ZoneSettingsCache.put(zoneId, key, value)
            } catch (e: Exception) {
                errors.add("$key：${e.message ?: "加载失败"}")
            }
        }
        return ZoneSettingsLoadResult(values, errors)
    }
}
