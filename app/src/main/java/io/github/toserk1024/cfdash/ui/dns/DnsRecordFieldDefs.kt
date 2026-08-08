package io.github.toserk1024.cfdash.ui.dns

/** DNS 记录表单字段定义（key 用于状态存储与 data 序列化） */
data class DnsFieldDef(
    val key: String,
    val label: String,
    val placeholder: String = "",
    val numeric: Boolean = false,
    val required: Boolean = true
)

/**
 * 各 DNS 记录类型的字段模板与顶部说明文案（仿 Cloudflare 控制台）。
 * name 字段单独处理；这里定义 name 之外的完整表单字段。
 */
object DnsRecordFieldDefs {

    // ===== 通用 key 常量（与 data 序列化字段名对应）=====
    const val TARGET = "target"          // A/AAAA/CNAME/MX/NS 的目标
    const val CONTENT = "content"        // TXT 内容
    const val PRIORITY = "priority"
    const val WEIGHT = "weight"
    const val PORT = "port"
    const val FLAGS = "flags"
    const val TAG = "tag"
    const val VALUE = "value"
    const val PROTOCOL = "protocol"
    const val ALGORITHM = "algorithm"
    const val PUBLIC_KEY = "public_key"
    const val FINGERPRINT_TYPE = "fingerprint_type"
    const val FINGERPRINT = "fingerprint"
    const val USAGE = "usage"
    const val SELECTOR = "selector"
    const val MATCHING_TYPE = "matching_type"
    const val CERTIFICATE = "certificate"
    const val ORDER = "order"
    const val PREFERENCE = "preference"
    const val SERVICE = "service"
    const val REGEXP = "regexp"
    const val REPLACEMENT = "replacement"
    const val PARAMS = "params"

    /** 记录类型 → 表单字段列表（不含 name） */
    fun fields(type: String): List<DnsFieldDef> = when (type) {
        "A" -> listOf(f(TARGET, "IPv4 地址", "例如 1.2.3.4"))
        "AAAA" -> listOf(f(TARGET, "IPv6 地址", "例如 2001:db8::1"))
        "CNAME" -> listOf(f(TARGET, "目标（别名指向）", "例如 example.com"))
        "MX" -> listOf(
            f(PRIORITY, "优先级 (1-65535)", "10", numeric = true),
            f(TARGET, "邮件服务器", "例如 mail.example.com")
        )
        "TXT" -> listOf(f(CONTENT, "文本内容", "例如 v=spf1 include:_spf.example.com ~all"))
        "NS" -> listOf(f(TARGET, "名称服务器", "例如 ns1.example.com"))
        "SRV" -> listOf(
            f(PRIORITY, "优先级", "0", numeric = true),
            f(WEIGHT, "权重", "5", numeric = true),
            f(PORT, "端口", "5060", numeric = true),
            f(TARGET, "目标（主机名）", "例如 sip.example.com")
        )
        "DNSKEY" -> listOf(
            f(FLAGS, "标志", "257", numeric = true),
            f(PROTOCOL, "协议", "3", numeric = true),
            f(ALGORITHM, "算法", "13", numeric = true),
            f(PUBLIC_KEY, "公钥", "Base64 编码的公钥")
        )
        "CAA" -> listOf(
            f(FLAGS, "标志", "0", numeric = true),
            f(TAG, "标签", "issue / issuewild / iodef"),
            f(VALUE, "值", "例如 letsencrypt.org")
        )
        "SVCB", "HTTPS" -> listOf(
            f(PRIORITY, "优先级", "1", numeric = true),
            f(TARGET, "目标", "例如 . 或 example.com"),
            f(PARAMS, "参数（alpn/port/ipv4hint 等）", "例如 alpn=h2,port=443")
        )
        "SSHFP" -> listOf(
            f(ALGORITHM, "算法", "1 (RSA) / 4 (Ed25519)", numeric = true),
            f(FINGERPRINT_TYPE, "指纹类型", "1 (SHA-1) / 2 (SHA-256)", numeric = true),
            f(FINGERPRINT, "指纹", "十六进制指纹")
        )
        "TLSA" -> listOf(
            f(USAGE, "用法", "3 (DANE-EE)", numeric = true),
            f(SELECTOR, "选择器", "1 (SPKI)", numeric = true),
            f(MATCHING_TYPE, "匹配类型", "1 (SHA-256)", numeric = true),
            f(CERTIFICATE, "证书关联数据", "十六进制数据")
        )
        "NAPTR" -> listOf(
            f(ORDER, "顺序", "10", numeric = true),
            f(PREFERENCE, "优先级", "10", numeric = true),
            f(FLAGS, "标志", "s / u / a 等"),
            f(SERVICE, "服务", "例如 SIP+D2U"),
            f(REGEXP, "正则表达式", "例如 !^.*$!sip:info@example.com!"),
            f(REPLACEMENT, "替换", "例如 .")
        )
        "URI" -> listOf(
            f(PRIORITY, "优先级", "10", numeric = true),
            f(WEIGHT, "权重", "1", numeric = true),
            f(TARGET, "目标 URI", "例如 https://example.com/")
        )
        else -> listOf(f(TARGET, "内容", "记录内容"))
    }

    /** 记录类型 → 顶部直观说明文案（"[名称]" 为记录名，"[目标]" 为解析目标） */
    fun description(type: String): String = when (type) {
        "A" -> "将子域名指向 IPv4 地址，[名称] 是 [IPv4 地址] 的别名。"
        "AAAA" -> "将子域名指向 IPv6 地址，[名称] 是 [IPv6 地址] 的别名。"
        "CNAME" -> "[名称] 是 [目标] 的别名，访问 [名称] 将跳转到 [目标]。"
        "MX" -> "[名称] 的邮件由 [目标] 处理，优先级数字越小越优先。"
        "TXT" -> "为 [名称] 添加任意文本记录（SPF、DKIM、验证等用途）。"
        "NS" -> "将 [名称] 的解析委托给 [目标] 名称服务器。"
        "SRV" -> "定义 [名称] 的服务位置：优先级、权重、端口与目标主机。"
        "DNSKEY" -> "DNSKEY 记录保存 DNSSEC 公钥，用于验证 DNS 记录签名。"
        "CAA" -> "声明允许为 [名称] 颁发证书的证书颁发机构（CA）。"
        "SVCB" -> "声明 [名称] 的服务参数与目标（服务绑定）。"
        "HTTPS" -> "声明 [名称] 的 HTTPS 服务参数（如 ALPN、端口）。"
        "SSHFP" -> "保存 [名称] 的 SSH 主机公钥指纹，用于 SSH 验证。"
        "TLSA" -> "将 TLS 证书关联数据绑定到 [名称]，用于 DANE 验证。"
        "NAPTR" -> "按规则将 [名称] 重写为其他名称（SIP 等服务常用）。"
        "URI" -> "为 [名称] 记录一个 URI 目标（按优先级/权重选择）。"
        else -> "填写 [名称] 对应的记录内容。"
    }

    private fun f(key: String, label: String, placeholder: String = "", numeric: Boolean = false) =
        DnsFieldDef(key = key, label = label, placeholder = placeholder, numeric = numeric)
}