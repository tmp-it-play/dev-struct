package team.themoment.dev_struct

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Path
import kotlin.io.path.name

/** macOS 26 팝오버 톤의 색. 테두리 대신 옅은 채움으로 묶음을 구분한다. */
private data class Palette(
    val window: Color,
    val group: Color,
    val footer: Color,
    val hairline: Color,
    val label: Color,
    val secondary: Color,
    val tertiary: Color,
    val accent: Color,
    val warning: Color,
    val segmentTrack: Color,
    val segmentThumb: Color,
)

private val Light =
    Palette(
        window = Color(0xFAFFFFFF),
        group = Color(0xFFF2F2F4),
        footer = Color(0x0A000000),
        hairline = Color(0x14000000),
        label = Color(0xFF1D1D1F),
        secondary = Color(0xFF6E6E73),
        tertiary = Color(0xFFAEAEB2),
        accent = Color(0xFF007AFF),
        warning = Color(0xFFC93400),
        segmentTrack = Color(0x0F000000),
        segmentThumb = Color.White,
    )

private val Dark =
    Palette(
        window = Color(0xF71E1E20),
        group = Color(0xFF2A2A2D),
        footer = Color(0x33000000),
        hairline = Color(0x1FFFFFFF),
        label = Color(0xFFF2F2F7),
        secondary = Color(0xFF98989F),
        tertiary = Color(0xFF636366),
        accent = Color(0xFF0A84FF),
        warning = Color(0xFFFF9F0A),
        segmentTrack = Color(0x14FFFFFF),
        segmentThumb = Color(0x38FFFFFF),
    )

private enum class Tab(val label: String) {
    Repos("정리"),
    Settings("설정"),
}

/** 메뉴바 팝오버 내용. 바깥 여백은 그림자 자리다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Popover(app: DevStruct, onQuit: () -> Unit, onHeightChange: (Dp) -> Unit) {
    val p = if (isSystemInDarkTheme()) Dark else Light
    var tab by remember { mutableStateOf(Tab.Repos) }
    val shape = RoundedCornerShape(16.dp)
    val density = LocalDensity.current

    // Material 물결 효과(Switch 등)를 끈다
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        Box(Modifier.fillMaxSize().padding(10.dp)) {
            // 창 높이를 내용에 맞추도록 실제 높이를 알려준다
            Column(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { with(density) { onHeightChange(it.height.toDp() + 20.dp) } }
                    .shadow(20.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(shape)
                    .background(p.window)
                    .border(0.5.dp, p.hairline, shape),
            ) {
                Segmented(Tab.entries, tab, { it.label }, p, Modifier.padding(10.dp)) { tab = it }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 440.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when (tab) {
                        Tab.Repos -> ReposTab(app, p)
                        Tab.Settings -> SettingsTab(app, p)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Footer(app, p, tab, onQuit)
            }
        }
    }
}

@Composable
private fun ColumnScope.ReposTab(app: DevStruct, p: Palette) {
    app.deviceCode?.let { code ->
        Group(p) {
            Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    code.userCode,
                    color = p.label,
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text("열린 브라우저에 이 코드를 입력하세요", color = p.secondary, fontSize = 12.sp)
            }
        }
    }

    val notice = app.problem ?: app.blockingIdes.takeIf { it.isNotEmpty() }?.let { "${it.joinToString()}을(를) 종료하면 옮겨요" }
    notice?.let {
        Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = p.warning, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(it, color = p.warning, fontSize = 12.sp)
        }
    }

    if (!app.loggedIn && app.deviceCode == null) {
        SectionTitle("GitHub", p)
        ItemRow(Icons.Filled.Lock, "로그인", "비공개 레포도 함께 정리해요", p) { Pill("로그인", p, app::login) }
    }

    if (app.pending.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.CheckCircle, null, tint = p.tertiary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(10.dp))
            Text(if (app.scanning) "확인하는 중" else "모두 정리돼 있어요", color = p.secondary, fontSize = 13.sp)
        }
        return
    }

    SectionTitle("옮길 레포", p) {
        if (!app.autoMove && app.pending.any { it.dir !in app.approved }) {
            Text(
                "모두 승인",
                color = p.accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.pressable(app::approveAll),
            )
        }
    }
    val root = Path.of(Settings.root)
    app.pending.forEach { plan ->
        val from = root.relativize(plan.dir)
        val to = root.relativize(plan.target)
        val detail = if (from.parent == to.parent) "${from.name}에서 이름 변경" else "${from.parent} → ${to.parent}"
        ItemRow(Icons.Filled.ArrowForward, plan.repo.ref.name, detail, p) {
            if (app.autoMove || plan.dir in app.approved) {
                Text("대기 중", color = p.secondary, fontSize = 11.sp)
            } else {
                Pill("승인", p) { app.approve(plan) }
            }
        }
    }
}

@Composable
private fun ColumnScope.SettingsTab(app: DevStruct, p: Palette) {
    var root by remember { mutableStateOf(Settings.root) }
    var interval by remember { mutableStateOf(Settings.intervalMinutes) }
    var loginItem by remember { mutableStateOf(LoginItem.enabled) }

    SectionTitle("폴더", p)
    Group(p) {
        SettingRow("위치", p) {
            Text(
                root.replaceFirst(System.getProperty("user.home"), "~"),
                color = p.secondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(8.dp))
            Pill("변경", p) {
                chooseFolder(root)?.let {
                    root = it
                    Settings.root = it
                    app.scanNow()
                }
            }
        }
        Hairline(p)
        SettingRow("확인 주기", p) {
            val options = listOf(15, 30, 60, 180)
            Segmented(options, interval, {
                if (it <
                    60
                ) {
                    "${it}분"
                } else {
                    "${it / 60}시간"
                }
            }, p, Modifier.width(196.dp), fontSize = 11.sp) {
                interval = it
                Settings.intervalMinutes = it
            }
        }
    }

    SectionTitle("동작", p)
    Group(p) {
        SettingRow("자동으로 옮기기", p) { MacSwitch(app.autoMove, p, onChange = app::updateAutoMove) }
        Hairline(p)
        SettingRow("로그인 시 열기", p) {
            MacSwitch(loginItem, p, enabled = LoginItem.available) {
                if (it) LoginItem.enable() else LoginItem.disable()
                loginItem = LoginItem.enabled
            }
        }
    }

    SectionTitle("GitHub", p)
    Group(p) {
        SettingRow(if (app.loggedIn) "로그인됨" else "로그인 안 됨", p) {
            if (app.loggedIn) Pill("로그아웃", p, app::logout) else Pill("로그인", p, app::login)
        }
    }
}

@Composable
private fun Footer(app: DevStruct, p: Palette, tab: Tab, onQuit: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    Row(
        Modifier.fillMaxWidth().background(p.footer).padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val update = app.update
        when {
            update != null -> Text(
                "${update.tag} 설치",
                color = p.accent,
                fontSize = 12.sp,
                modifier = Modifier.pressable(app::installUpdate),
            )
            tab == Tab.Settings -> Text("DevStruct $APP_VERSION", color = p.secondary, fontSize = 12.sp)
            app.scanning -> Text("확인하는 중…", color = p.secondary, fontSize = 12.sp)
            else -> {
                val minutes = ((app.nextScanAt - now) / 60_000).coerceAtLeast(0)
                Text(if (minutes < 1) "곧 다시 확인해요" else "${minutes}분 후 다시 확인", color = p.secondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        // 확인하는 동안에는 새로고침 아이콘이 천천히 돈다
        val spin by rememberInfiniteTransition().animateFloat(
            0f,
            360f,
            infiniteRepeatable(tween(1200, easing = LinearEasing)),
        )
        CircleButton(Icons.Filled.Refresh, "지금 확인", p, if (app.scanning) Modifier.rotate(spin) else Modifier) {
            if (!app.scanning) app.scanNow()
        }
        Spacer(Modifier.width(6.dp))
        CircleButton(Icons.Filled.ExitToApp, "종료", p, onClick = onQuit)
    }
}

@Composable
private fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    p: Palette,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
    onSelect: (T) -> Unit,
) {
    Row(modifier.fillMaxWidth().clip(CircleShape).background(p.segmentTrack).padding(2.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .then(if (isSelected) Modifier.shadow(1.dp, CircleShape) else Modifier)
                    .clip(CircleShape)
                    .background(if (isSelected) p.segmentThumb else Color.Transparent)
                    .pressable { onSelect(option) }
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    color = if (isSelected) p.label else p.secondary,
                    fontSize = fontSize,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, p: Palette, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text,
            color = p.secondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
private fun ItemRow(icon: ImageVector, title: String, subtitle: String, p: Palette, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(p.group), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = p.secondary, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = p.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, color = p.secondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun Group(p: Palette, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.group), content = content)
}

@Composable
private fun SettingRow(label: String, p: Palette, trailing: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = p.label, fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            trailing()
        }
    }
}

@Composable
private fun Hairline(p: Palette) =
    Box(Modifier.padding(start = 12.dp).fillMaxWidth().height(0.5.dp).background(p.hairline))

@Composable
private fun Pill(label: String, p: Palette, onClick: () -> Unit) {
    Text(
        label,
        color = p.accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(
            CircleShape,
        ).background(
            p.accent.copy(alpha = 0.14f),
        ).pressable(onClick).padding(horizontal = 11.dp, vertical = 3.dp),
    )
}

@Composable
private fun CircleButton(
    icon: ImageVector,
    description: String,
    p: Palette,
    iconModifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(28.dp).clip(CircleShape).background(p.segmentTrack).pressable(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = p.secondary, modifier = iconModifier.size(15.dp))
    }
}

/** macOS처럼 물결·사각 강조 없이 누르는 동안만 살짝 흐려진다 */
@Composable
private fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return alpha(if (pressed) 0.5f else 1f).clickable(source, indication = null, onClick = onClick)
}

@Composable
private fun MacSwitch(checked: Boolean, p: Palette, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors =
        SwitchDefaults.colors(
            checkedTrackColor = p.accent,
            checkedThumbColor = Color.White,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = p.segmentTrack,
            uncheckedBorderColor = Color.Transparent,
        ),
        thumbContent = {},
        modifier = Modifier.scale(0.62f).height(24.dp),
    )
}

/** macOS 기본 폴더 선택 창 */
private fun chooseFolder(current: String): String? {
    System.setProperty("apple.awt.fileDialogForDirectories", "true")
    val dialog = FileDialog(null as Frame?, "관리할 폴더 선택", FileDialog.LOAD).apply { directory = current }
    dialog.isVisible = true
    System.setProperty("apple.awt.fileDialogForDirectories", "false")
    return dialog.file?.let { File(dialog.directory, it).path }
}
