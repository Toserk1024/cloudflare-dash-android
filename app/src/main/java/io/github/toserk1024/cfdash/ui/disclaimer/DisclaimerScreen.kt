package io.github.toserk1024.cfdash.ui.disclaimer

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 免责声明启动页：应用首次进入时展示，须勾选"已知晓并愿意承担相应风险"后才能继续。
 * 作为 startDestination，同意后根据登录态进入登录页或主界面。
 */
@Composable
fun DisclaimerScreen(
    onAgree: () -> Unit
) {
    val context = LocalContext.current
    var agreed by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "免责声明",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))

            // 声明全文
            Text(
                text = DISCLAIMER_TEXT,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 已知晓复选框（整行可点击）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { agreed = !agreed }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = agreed, onCheckedChange = { agreed = it })
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "我已知晓并愿意承担相应风险",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (agreed) FontWeight.Medium else FontWeight.Normal
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // 继续（需勾选）
            Button(
                onClick = onAgree,
                enabled = agreed,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("继续", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 退出应用
            OutlinedButton(
                onClick = { (context as? Activity)?.finish() },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("退出")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/** 免责声明全文 */
private val DISCLAIMER_TEXT = """
    CF Dash（下称"本软件"）是第三方独立开发者基于 Cloudflare 公开 API 开发的辅助工具，与 Cloudflare, Inc. 及其关联公司无任何隶属、赞助、授权或官方合作关系。"Cloudflare"及相关标识均为 Cloudflare, Inc. 的注册商标。

    风险自担：
    本软件按"现状"提供，不保证功能完全无错误、无中断，亦不保证不因 Cloudflare 官方 API 策略调整而失效。使用本软件可能违反 Cloudflare 的服务条款，你须自行承担由此产生的一切风险与后果。

    API 密钥与数据安全：
    本软件如需调用你的 Cloudflare API 密钥，该密钥经加密存储于你的本地设备，开发者无法也绝不会接触、收集或存储你的密钥。你全权负责密钥的保管及权限范围，因密钥泄露造成的任何损失，开发者不承担责任。

    责任限制：
    开发者不因使用或无法使用本软件而产生的任何直接、间接、偶然、特殊或后果性损失（包括但不限于数据丢失、服务中断、业务损失）承担任何责任，即使已被告知此类损害的可能性。

    第三方行为：
    你使用本软件对域名、Workers 或站点进行的管理操作，均属你的自主行为，后果由你个人承担。

    接受条款：
    一旦使用本软件，即代表你同意本声明全部内容。若你不同意，请立即停止使用。
""".trimIndent()