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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

/** 메뉴바 팝오버 내용. 그림자는 macOS가 그린다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Popover(app: DevStruct, onQuit: () -> Unit, onClose: () -> Unit) {
    val p = if (isSystemInDarkTheme()) Dark else Light
    val tab = app.page
    val shape = RoundedCornerShape(16.dp)

    // Material 물결 효과(Switch 등)를 끈다
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        Box(Modifier.fillMaxSize()) {
            // 창 높이는 고정이고, 넘치는 내용만 가운데 영역에서 스크롤된다
            Column(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(p.window)
                    .border(0.5.dp, p.hairline, shape),
            ) {
                Segmented(Page.entries, tab, { it.label }, p, Modifier.padding(10.dp)) { app.page = it }
                // 정리 탭이 비어 있으면 스크롤 없이 안내를 세로 가운데에 둔다
                val empty = tab == Page.Repos && app.pending.isEmpty() && app.discovered.isEmpty()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .then(if (empty) Modifier else Modifier.verticalScroll(rememberScrollState()))
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when (tab) {
                        Page.Repos -> ReposTab(app, p, onClose)
                        Page.Settings -> SettingsTab(app, p, onClose)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Footer(app, p, tab, onQuit)
            }
        }
    }
}

@Composable
private fun ColumnScope.ReposTab(app: DevStruct, p: Palette, onClose: () -> Unit) {
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

    if (!app.loggedIn && app.deviceCode == null) {
        SectionTitle("GitHub", p)
        ItemRow(Icons.Filled.Lock, "로그인", "비공개 저장소도 함께 정리해요", p) {
            Pill("로그인", p) {
                app.login()
                onClose()
            }
        }
    }

    if (app.pending.isEmpty() && app.discovered.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.CheckCircle, null, tint = p.tertiary, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(10.dp))
            Text(if (app.scanning) "확인하는 중" else "모두 정리돼 있어요", color = p.secondary, fontSize = 13.sp)
        }
        return
    }

    if (app.pending.isNotEmpty() && app.runningAgents.isNotEmpty()) {
        Notice("${app.runningAgents.joinToString()} 실행 중 · 바뀐 경로가 덮어써질 수 있어요", p)
    }

    if (app.pending.isNotEmpty()) {
        SectionTitle("옮길 저장소", p) {
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
                when {
                    // 루트 밖에서 가져오는 저장소
                    !plan.dir.startsWith(root) -> "${homeRelative(plan.dir)} → ${to.parent}"
                    from.parent == to.parent -> "${from.name}에서 이름 변경"
                    else -> "${from.parent ?: "루트 폴더"} → ${to.parent}"
                }
            ItemRow(Icons.Filled.ArrowForward, plan.repo.ref.name, detail, p) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Pill("고정", p, subtle = true) { app.pin(plan) }
                    if (app.autoMove || plan.dir in app.approved) {
                        WaitingLabel(app, plan, p)
                    } else {
                        Pill("승인", p) { app.approve(plan) }
                    }
                }
            }
        }
    }

    if (app.discovered.isNotEmpty()) {
        SectionTitle("다른 곳에서 찾은 저장소 ${app.discovered.size}", p)
        app.discovered.forEach { found ->
            ItemRow(Icons.Filled.Search, found.ref.name, "${homeRelative(found.dir)} → ${found.ref.owner}", p) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Pill("여기 두기", p, subtle = true) { app.keepHere(found) }
                    Pill("가져오기", p) { app.importRepo(found) }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SettingsTab(app: DevStruct, p: Palette, onClose: () -> Unit) {
    var root by remember { mutableStateOf(Settings.root) }
    var interval by remember { mutableStateOf(Settings.intervalMinutes) }
    var loginItem by remember { mutableStateOf(LoginItem.enabled) }
    var devTools by remember { mutableStateOf(Settings.devTools) }
    var agentTools by remember { mutableStateOf(Settings.agentTools) }
    var discover by remember { mutableStateOf(Settings.discover) }
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
        SettingRow("자동으로 옮기기", p, info = "끄면 승인한 저장소만 옮겨요.") {
            MacSwitch(app.autoMove, p, onChange = app::updateAutoMove)
        }
        Hairline(p)
        SettingRow("IDE·ADE 연동", p, info = IDE_INFO) {
            MacSwitch(devTools, p) {
                devTools = it
                Settings.devTools = it
                app.scanNow()
            }
        }
        Hairline(p)
        SettingRow("에이전트 도구 연동", p, info = AGENT_INFO) {
            MacSwitch(agentTools, p) {
                agentTools = it
                Settings.agentTools = it
            }
        }
        Hairline(p)
        SettingRow("홈 폴더에서도 찾기", p, info = DISCOVER_INFO) {
            MacSwitch(discover, p) {
                discover = it
                Settings.discover = it
                app.discoverNow()
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
            if (app.loggedIn) {
                Pill("로그아웃", p, onClick = app::logout)
            } else {
                Pill("로그인", p) {
                    app.login()
                    onClose()
                }
            }
        }
    }

    SectionTitle("업데이트", p)
    Group(p) {
        SettingRow("버전 $APP_VERSION", p) {
            val update = app.update
            when {
                app.updateProgress != null -> ProgressPill(app.updateProgress!!, p)
                update != null -> Pill("${update.tag} 설치", p, onClick = app::installUpdate)
                app.checkingUpdate -> Text("확인하는 중…", color = p.secondary, fontSize = 12.sp)
                else -> Pill("업데이트 확인", p, onClick = app::checkForUpdate)
            }
        }
        app.updateStatus?.let {
            Text(
                it,
                color = p.secondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 9.dp),
            )
        }
    }

    if (app.pinned.isNotEmpty() || app.keptElsewhere.isNotEmpty()) PinnedSection(app, p)

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

private const val DISCOVER_INFO = "루트 밖 홈 폴더에서 저장소를 찾아요. 승인해야 가져와요. 숨김 폴더와 ~/Library는 빼요."

private const val IDE_INFO = "IDE·ADE가 켜져 있으면 끈 뒤에 옮기고, 프로젝트 경로를 바꿔요."

private const val AGENT_INFO = "에이전트 도구의 프로젝트 경로를 바꿔요. 세션이 실행 중이면 덮어써질 수 있어요."

private fun homeRelative(path: Path) = path.toString().replaceFirst(System.getProperty("user.home"), "~")

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
private fun Footer(app: DevStruct, p: Palette, tab: Page, onQuit: () -> Unit) {
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
        // 상태는 한 번만: 확인 중이면 도는 아이콘만, 문제가 있으면 그 문제만 보여준다
        val problem = app.problem
        when {
            tab == Page.Settings -> Text("DevStruct $APP_VERSION", color = p.secondary, fontSize = 12.sp)
            problem != null ->
                Text(
                    problem,
                    color = p.warning,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            app.scanning -> Unit
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
                tooltip = { Tip(it, p) },
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
private fun Pill(label: String, p: Palette, subtle: Boolean = false, onClick: () -> Unit) {
    val color = if (subtle) p.secondary else p.accent
    Text(
        label,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier =
        Modifier
            .clip(CircleShape)
            .background(if (subtle) p.segmentTrack else color.copy(alpha = 0.14f))
            .pressable(onClick)
            .padding(horizontal = 11.dp, vertical = 3.dp),
    )
}

/** 승인했지만 아직 옮기지 않은 이유. IDE 때문이면 어떤 IDE인지 툴팁으로 알려준다 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WaitingLabel(app: DevStruct, plan: RepoPlan, p: Palette) {
    val reason =
        when {
            app.blockingIdes.isNotEmpty() ->
                "도구 종료 후" to
                    "${app.blockingIdes.joinToString()}을(를) 끄면 옮겨요."
            plan.dir in app.busyRepos -> "사용 중" to "터미널이나 에이전트가 이 폴더를 쓰고 있어요."
            else -> null
        }
    if (reason == null) {
        Text("곧 옮겨요", color = p.secondary, fontSize = 11.sp)
        return
    }
    TooltipArea(tooltip = { Tip(reason.second, p) }, delayMillis = 300) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(reason.first, color = p.warning, fontSize = 11.sp)
            Spacer(Modifier.width(3.dp))
            Icon(Icons.Outlined.Info, null, tint = p.warning, modifier = Modifier.size(12.dp))
        }
    }
}

/** 경고 한 줄(주황색 느낌표 + 문장) */
@Composable
private fun Notice(text: String, p: Palette) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Warning, null, tint = p.warning, modifier = Modifier.size(14.dp).padding(top = 1.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = p.warning, fontSize = 12.sp)
    }
}

@Composable
private fun Tip(text: String, p: Palette) {
    Text(
        text,
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
}

/** 설치 버튼이 진행률만큼 왼쪽부터 채워진다. 채워지기 전에도 옅은 바탕색은 있다 */
@Composable
private fun ProgressPill(progress: Float, p: Palette) {
    Box(
        Modifier
            .width(88.dp)
            .height(22.dp)
            .clip(CircleShape)
            .background(p.accent.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.align(
                Alignment.CenterStart,
            ).fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(p.accent.copy(alpha = 0.38f)),
        )
        Text("${(progress * 100).toInt()}%", color = p.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 고정된 저장소 목록. 기본은 접혀 있다 */
@Composable
private fun PinnedSection(app: DevStruct, p: Palette) {
    var expanded by remember { mutableStateOf(false) }
    val root = Path.of(Settings.root)
    // 루트 안에서 고정한 저장소와, 루트 밖에서 "여기 두기"한 저장소
    val rows =
        app.pinned.map { Triple(it.repo.ref.name, root.relativize(it.dir).toString()) { app.unpin(it) } } +
            app.keptElsewhere.map { Triple(it.ref.name, homeRelative(it.dir)) { app.unkeep(it) } }
    SectionTitle("예외", p)
    Group(p) {
        Row(
            Modifier.fillMaxWidth().height(38.dp).pressable { expanded = !expanded }.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("고정된 저장소 ${rows.size}개", color = p.label, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                null,
                tint = p.tertiary,
                modifier = Modifier.size(16.dp),
            )
        }
        if (expanded) {
            rows.forEach { (name, location, release) ->
                Hairline(p)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(name, color = p.label, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            location,
                            color = p.secondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Pill("해제", p, subtle = true, onClick = release)
                }
            }
        }
    }
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
