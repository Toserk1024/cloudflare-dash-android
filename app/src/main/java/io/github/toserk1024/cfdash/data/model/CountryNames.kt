package io.github.toserk1024.cfdash.data.model

/**
 * 国家/地区代码 → 中文名映射（ISO 3166-1 alpha-2）。
 *
 * 独立成文件的原因：安全页后续要重构，其内部的 `CountryMapping` 届时统一迁移到此处；
 * 本次只有统计页使用，安全页代码保持原样不动。
 *
 * Cloudflare `clientCountryName` 返回两位国家代码；`A1` / `A2` / `T1` 为匿名网络类特殊值。
 * 未收录的代码原样返回，保证信息不丢失。
 */
object CountryNames {

    private val zhNames: Map<String, String> = mapOf(
        // ===== 亚太 =====
        "CN" to "中国", "HK" to "中国香港", "MO" to "中国澳门", "TW" to "中国台湾",
        "JP" to "日本", "KR" to "韩国", "KP" to "朝鲜", "SG" to "新加坡",
        "MY" to "马来西亚", "TH" to "泰国", "VN" to "越南", "PH" to "菲律宾",
        "ID" to "印度尼西亚", "IN" to "印度", "PK" to "巴基斯坦", "BD" to "孟加拉国",
        "LK" to "斯里兰卡", "NP" to "尼泊尔", "MM" to "缅甸", "KH" to "柬埔寨",
        "LA" to "老挝", "BN" to "文莱", "MN" to "蒙古",
        "AU" to "澳大利亚", "NZ" to "新西兰", "FJ" to "斐济", "PG" to "巴布亚新几内亚",
        // ===== 北美 / 南美 =====
        "US" to "美国", "CA" to "加拿大", "MX" to "墨西哥",
        "BR" to "巴西", "AR" to "阿根廷", "CL" to "智利", "CO" to "哥伦比亚",
        "PE" to "秘鲁", "VE" to "委内瑞拉", "EC" to "厄瓜多尔", "UY" to "乌拉圭",
        "PY" to "巴拉圭", "BO" to "玻利维亚", "CR" to "哥斯达黎加", "PA" to "巴拿马",
        "GT" to "危地马拉", "DO" to "多米尼加", "CU" to "古巴",
        // ===== 欧洲 =====
        "GB" to "英国", "IE" to "爱尔兰", "FR" to "法国", "DE" to "德国",
        "NL" to "荷兰", "BE" to "比利时", "LU" to "卢森堡", "CH" to "瑞士",
        "AT" to "奥地利", "IT" to "意大利", "ES" to "西班牙", "PT" to "葡萄牙",
        "SE" to "瑞典", "NO" to "挪威", "DK" to "丹麦", "FI" to "芬兰",
        "IS" to "冰岛", "PL" to "波兰", "CZ" to "捷克", "SK" to "斯洛伐克",
        "HU" to "匈牙利", "RO" to "罗马尼亚", "BG" to "保加利亚", "GR" to "希腊",
        "HR" to "克罗地亚", "SI" to "斯洛文尼亚", "RS" to "塞尔维亚", "BA" to "波黑",
        "MK" to "北马其顿", "AL" to "阿尔巴尼亚", "ME" to "黑山", "MT" to "马耳他",
        "CY" to "塞浦路斯", "LT" to "立陶宛", "LV" to "拉脱维亚", "EE" to "爱沙尼亚",
        "UA" to "乌克兰", "BY" to "白俄罗斯", "MD" to "摩尔多瓦", "RU" to "俄罗斯",
        // ===== 中东 / 中亚 =====
        "TR" to "土耳其", "IL" to "以色列", "SA" to "沙特阿拉伯", "AE" to "阿联酋",
        "QA" to "卡塔尔", "KW" to "科威特", "BH" to "巴林", "OM" to "阿曼",
        "JO" to "约旦", "LB" to "黎巴嫩", "IQ" to "伊拉克", "IR" to "伊朗",
        "SY" to "叙利亚", "YE" to "也门", "KZ" to "哈萨克斯坦", "UZ" to "乌兹别克斯坦",
        "KG" to "吉尔吉斯斯坦", "TJ" to "塔吉克斯坦", "TM" to "土库曼斯坦", "AF" to "阿富汗",
        "AZ" to "阿塞拜疆", "GE" to "格鲁吉亚", "AM" to "亚美尼亚",
        // ===== 非洲 =====
        "ZA" to "南非", "EG" to "埃及", "NG" to "尼日利亚", "KE" to "肯尼亚",
        "MA" to "摩洛哥", "DZ" to "阿尔及利亚", "TN" to "突尼斯", "GH" to "加纳",
        "ET" to "埃塞俄比亚", "TZ" to "坦桑尼亚", "UG" to "乌干达", "CM" to "喀麦隆",
        "CI" to "科特迪瓦", "SN" to "塞内加尔", "ZW" to "津巴布韦", "ZM" to "赞比亚",
        "MZ" to "莫桑比克", "AO" to "安哥拉", "SD" to "苏丹", "LY" to "利比亚",
        // ===== 特殊值（Cloudflare 匿名网络类）=====
        "A1" to "匿名代理", "A2" to "卫星网络", "T1" to "Tor 网络",
        "EU" to "欧洲（区域）", "AP" to "亚太（区域）"
    )

    /** 代码 → 中文名；未收录返回原代码（保证不丢信息） */
    fun zh(code: String): String =
        if (code.isBlank()) code else zhNames[code.trim().uppercase()] ?: code
}