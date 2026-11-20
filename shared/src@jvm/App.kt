package team.themoment.dev_struct

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
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

/** 메뉴바 팝오버 내용. 그림자는 macOS가 그린다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Popover(app: DevStruct, onQuit: () -> Unit, onHeightChange: (Dp) -> Unit) {
    val p = if (isSystemInDarkTheme()) Dark else Light
    var tab by remember { mutableStateOf(Tab.Repos) }
    val shape = RoundedCornerShape(16.dp)
    val density = LocalDensity.current

    // Material 물결 효과(Switch 등)를 끈다
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        Box(Modifier.fillMaxSize()) {
            // 창 높이를 내용에 맞추도록 실제 높이를 알려준다
            Column(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { with(density) { onHeightChange(it.height.toDp()) } }
                    .clip(shape)
                    .background(p.window)
                    .border(0.5.dp, p.hairline, shape),
            ) {
                Segmented(Tab.entries, tab, { it.label }, p, Modifier.padding(10.dp)) { tab = it }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 400.dp, max = 640.dp)
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
        val detail =
            if (from.parent == to.parent) "${from.name}에서 이름 변경" else "${from.parent ?: "루트 폴더"} → ${to.parent}"
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
    var jetbrains by remember { mutableStateOf(Settings.jetbrains) }
    var showNotices by remember { mutableStateOf(false) }

    if (showNotices) {
        Notices(p) { showNotices = false }
        return
    }

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
            Segmented(listOf(15, 30, 60, 180), interval, ::intervalLabel, p, Modifier.width(196.dp), fontSize = 11.sp) {
                interval = it
                Settings.intervalMinutes = it
            }
        }
    }

    SectionTitle("동작", p)
    Group(p) {
        SettingRow("자동으로 옮기기", p, info = "켜면 옮길 레포를 찾는 즉시 옮겨요. 끄면 정리 탭에서 승인한 레포만 옮겨요.") {
            MacSwitch(app.autoMove, p, onChange = app::updateAutoMove)
        }
        Hairline(p)
        SettingRow(
            "JetBrains IDE 연동",
            p,
            info = "IntelliJ IDEA 같은 JetBrains IDE가 켜져 있으면 끌 때까지 기다렸다가 옮기고, 옮긴 뒤 IDE의 최근 프로젝트 경로도 바꿔요.",
        ) {
            MacSwitch(jetbrains, p) {
                jetbrains = it
                Settings.jetbrains = it
                app.scanNow()
            }
        }
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

    SectionTitle("정보", p)
    Group(p) {
        LinkRow("GitHub 저장소", p) { openUrl("https://github.com/tmp-it-play/dev-struct") }
        Hairline(p)
        LinkRow("라이선스 (Boost Software License 1.0)", p) {
            openUrl("https://github.com/tmp-it-play/dev-struct/blob/master/LICENSE")
        }
        Hairline(p)
        LinkRow("오픈소스 고지", p) { showNotices = true }
    }
}

private fun intervalLabel(minutes: Int) = if (minutes < 60) "${minutes}분" else "${minutes / 60}시간"

/** 앱에 함께 들어가는 오픈소스와 라이선스 */
private val NOTICES =
    listOf(
        Triple("Kotlin", "Apache License 2.0", "https://github.com/JetBrains/kotlin"),
        Triple("Compose Multiplatform", "Apache License 2.0", "https://github.com/JetBrains/compose-multiplatform"),
        Triple("Skiko", "Apache License 2.0", "https://github.com/JetBrains/skiko"),
        Triple("Material Icons", "Apache License 2.0", "https://github.com/google/material-design-icons"),
        Triple("AndroidX Lifecycle", "Apache License 2.0", "https://github.com/JetBrains/compose-multiplatform-core"),
        Triple("kotlinx.coroutines", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.coroutines"),
        Triple("kotlinx.serialization", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.serialization"),
        Triple("Logback", "EPL 1.0 / LGPL 2.1", "https://github.com/qos-ch/logback"),
        Triple("OpenJDK (Eclipse Temurin)", "GPL 2.0 with Classpath Exception", "https://adoptium.net"),
    )

@Composable
private fun ColumnScope.Notices(p: Palette, onBack: () -> Unit) {
    Row(Modifier.padding(top = 8.dp).pressable(onBack), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.KeyboardArrowLeft, null, tint = p.accent, modifier = Modifier.size(18.dp))
        Text("설정", color = p.accent, fontSize = 12.sp)
    }
    SectionTitle("오픈소스 고지", p)
    Group(p) {
        NOTICES.forEachIndexed { i, (name, license, url) ->
            if (i > 0) Hairline(p)
            Row(
                Modifier.fillMaxWidth().pressable { openUrl(url) }.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(name, color = p.label, fontSize = 13.sp)
                    Text(license, color = p.secondary, fontSize = 11.sp)
                }
                Icon(Icons.Filled.KeyboardArrowRight, null, tint = p.tertiary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun LinkRow(label: String, p: Palette, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(38.dp).pressable(onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = p.label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = p.tertiary, modifier = Modifier.size(16.dp))
    }
}

private fun openUrl(url: String) {
    runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
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
        CircleButton(PowerIcon, "종료", p, onClick = onQuit)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SettingRow(label: String, p: Palette, info: String? = null, trailing: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = p.label, fontSize = 13.sp)
        info?.let {
            Spacer(Modifier.width(4.dp))
            TooltipArea(
                tooltip = {
                    Text(
                        it,
                        color = p.label,
                        fontSize = 11.sp,
                        modifier =
                        Modifier
                            .widthIn(max = 240.dp)
                            .shadow(4.dp, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(p.window)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                },
                delayMillis = 300,
            ) {
                Icon(Icons.Outlined.Info, "설명", tint = p.tertiary, modifier = Modifier.size(14.dp))
            }
        }
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

/** macOS 종료 버튼에 쓰는 전원(⏻) 모양 */
private val PowerIcon: ImageVector =
    ImageVector
        .Builder("Power", 24.dp, 24.dp, 24f, 24f)
        .path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round,
        ) {
            // 위가 트인 원
            moveTo(7.05f, 6.5f)
            arcTo(7.5f, 7.5f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 16.95f, y1 = 6.5f)
            // 세로 막대
            moveTo(12f, 3.5f)
            lineTo(12f, 11.5f)
        }.build()

/** macOS처럼 누를 때 물결·강조·깜빡임이 없다 */
@Composable
private fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return clickable(source, indication = null, onClick = onClick)
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
