package team.themoment.dev_struct

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.nio.file.Files
import java.nio.file.Path

@Composable
fun App(app: DevStruct) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsSection(app)
                HorizontalDivider()
                StatusSection(app)
                PendingSection(app, Modifier.weight(1f))
                HorizontalDivider()
                Text("로그", style = MaterialTheme.typography.titleSmall)
                LazyColumn(Modifier.weight(1f)) {
                    items(app.logs) { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(app: DevStruct) {
    var root by remember { mutableStateOf(Settings.root) }
    var interval by remember { mutableStateOf(Settings.intervalMinutes.toString()) }
    var loginItem by remember { mutableStateOf(LoginItem.enabled) }
    val rootValid = Files.isDirectory(Path.of(root))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = root,
            onValueChange = { root = it },
            label = { Text("관리할 루트 폴더") },
            isError = !rootValid,
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = interval,
            onValueChange = { interval = it.filter(Char::isDigit) },
            label = { Text("스캔 주기(분)") },
            singleLine = true,
            modifier = Modifier.weight(0.3f),
        )
        Button(
            enabled = rootValid && interval.isNotEmpty(),
            onClick = {
                Settings.root = root
                Settings.intervalMinutes = interval.toInt()
                app.scanNow()
            },
        ) { Text("저장") }
    }
    LabeledSwitch("자동 이동 (끄면 승인한 레포만 이동)", app.autoMove, onChange = app::updateAutoMove)
    LabeledSwitch(
        if (LoginItem.available) "로그인 시 실행" else "로그인 시 실행 (.app으로 실행했을 때만 가능)",
        loginItem,
        enabled = LoginItem.available,
    ) {
        if (it) LoginItem.enable() else LoginItem.disable()
        loginItem = LoginItem.enabled
    }
}

@Composable
private fun StatusSection(app: DevStruct) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(enabled = !app.scanning, onClick = app::scanNow) { Text(if (app.scanning) "스캔 중…" else "지금 스캔") }
        Text("마지막 스캔: ${app.lastScan ?: "-"}", Modifier.weight(1f))
        Text("v$APP_VERSION")
        app.update?.let { OutlinedButton(onClick = app::installUpdate) { Text("${it.tag}로 업데이트") } }
    }
    if (app.blockingIdes.isNotEmpty()) {
        Text(
            "${app.blockingIdes.joinToString()} 실행 중이라 이동을 미뤘어요. 종료하면 자동으로 진행해요.",
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun PendingSection(app: DevStruct, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "이동 대기 ${app.pending.size}개",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (!app.autoMove &&
                app.pending.isNotEmpty()
            ) {
                OutlinedButton(onClick = app::approveAll) { Text("모두 승인") }
            }
        }
        val root = Path.of(Settings.root)
        LazyColumn {
            items(app.pending, key = { it.dir.toString() }) { plan ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${root.relativize(plan.dir)}  →  ${root.relativize(plan.target)}", Modifier.weight(1f))
                    when {
                        app.autoMove || plan.dir in app.approved -> Text(
                            "대기 중",
                            color = MaterialTheme.colorScheme.primary,
                        )
                        else -> OutlinedButton(onClick = { app.approve(plan) }) { Text("승인") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Text(label)
    }
}
