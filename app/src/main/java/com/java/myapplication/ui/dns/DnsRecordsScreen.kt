package com.java.myapplication.ui.dns

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.java.myapplication.data.model.DnsRecord

/** 独立 DNS 记录页面（从域名详情进入） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsRecordsScreen(
    zoneId: String,
    zoneName: String?,
    onBack: () -> Unit,
    onEditRecord: (String, String?) -> Unit,
    viewModel: DnsViewModel = viewModel()
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(zoneName ?: "DNS 记录", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            DnsRecordsContent(
                onEditRecord = { record: DnsRecord -> onEditRecord(zoneId, record.id) },
                onAddRecord = { onEditRecord(zoneId, null) },
                viewModel = viewModel
            )
        }
    }
}