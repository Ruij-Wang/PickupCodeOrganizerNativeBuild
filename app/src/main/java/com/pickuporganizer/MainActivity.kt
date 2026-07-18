package com.pickuporganizer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pickuporganizer.data.PickupItemEntity
import com.pickuporganizer.data.PickupStatus
import com.pickuporganizer.data.RawMessageEntity
import com.pickuporganizer.settings.ListenerPreferences
import com.pickuporganizer.ui.PickupViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PickupOrganizerApp() }
    }
}

private enum class Screen(val title: String) {
    Home("取件台"),
    Inbox("采集"),
    Settings("设置")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickupOrganizerApp(viewModel: PickupViewModel = viewModel()) {
    val colorScheme = lightColorScheme(
        primary = Color(0xFF0B6B61),
        onPrimary = Color.White,
        secondary = Color(0xFF8B6D19),
        tertiary = Color(0xFF81556C),
        background = Color(0xFFF5F7F4),
        surface = Color.White,
        surfaceVariant = Color(0xFFE7ECE8)
    )

    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            var screen by remember { mutableStateOf(Screen.Home) }
            var selected by remember { mutableStateOf<PickupItemEntity?>(null) }

            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                if (selected == null) screen.title else "取件详情",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            if (selected != null) {
                                TextButton(onClick = { selected = null }) { Text("返回") }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    )
                },
                bottomBar = {
                    if (selected == null) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            Screen.entries.forEach { item ->
                                NavigationBarItem(
                                    selected = screen == item,
                                    onClick = { screen = item },
                                    icon = { Dot(selected = screen == item) },
                                    label = { Text(item.title) }
                                )
                            }
                        }
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                ) {
                    if (selected != null) {
                        PickupDetailScreen(
                            item = selected!!,
                            onSave = { appSource, station, pickupCode, status ->
                                viewModel.updatePickupItem(selected!!.id, appSource, station, pickupCode, status)
                                selected = null
                            }
                        )
                    } else {
                        when (screen) {
                            Screen.Home -> PickupHomeScreen(viewModel, onOpen = { selected = it })
                            Screen.Inbox -> InboxScreen(viewModel)
                            Screen.Settings -> SettingsScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickupHomeScreen(viewModel: PickupViewModel, onOpen: (PickupItemEntity) -> Unit) {
    val pickupItems by viewModel.pickupItems.collectAsState()
    val rawMessages by viewModel.rawMessages.collectAsState()
    val modelStatus by viewModel.modelStatus.collectAsState()
    val pending = pickupItems.count { it.status == PickupStatus.PENDING }
    val collected = pickupItems.count { it.status == PickupStatus.COLLECTED }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { PermissionBanner() }
        item {
            SummaryPanel(
                pending = pending,
                collected = collected,
                captured = rawMessages.size,
                modelMode = modelStatus.modeLabel
            )
        }

        if (pickupItems.isEmpty()) {
            item {
                EmptyState(onCreateSample = {
                    viewModel.saveSample("您的京东快递已到xx大学xx园x号菜鸟驿站京东点，请凭提货号A-1024前往领取")
                })
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("最近取件", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${pickupItems.size} 条", style = MaterialTheme.typography.labelLarge, color = Color(0xFF687271))
                }
            }
            items(pickupItems, key = { it.id }) { item ->
                PickupItemCard(
                    item = item,
                    onOpen = { onOpen(item) },
                    onStatus = { viewModel.updateStatus(item.id, it) }
                )
            }
        }
    }
}

@Composable
private fun SummaryPanel(pending: Int, collected: Int, captured: Int, modelMode: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("今日概览", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile("待取", pending.toString(), Color(0xFFEAF5F1), Modifier.weight(1f))
            MetricTile("已取", collected.toString(), Color(0xFFFFF3D8), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile("原始通知", captured.toString(), Color(0xFFEFF2FB), Modifier.weight(1f))
            MetricTile("抽取方式", modelMode, Color(0xFFF6EAF1), Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFF53605D))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InboxScreen(viewModel: PickupViewModel) {
    val rawMessages by viewModel.rawMessages.collectAsState()
    val context = LocalContext.current
    val accessEnabled = isNotificationListenerEnabled(context)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            DiagnosticPanel(
                accessEnabled = accessEnabled,
                rawCount = rawMessages.size
            )
        }

        if (rawMessages.isEmpty()) {
            item {
                PlainPanel(
                    title = "暂无原始通知",
                    body = "开启通知访问后，短信 App、购物 App、微信等通知会先进入这里，再尝试抽取取件码。"
                )
            }
        } else {
            items(rawMessages, key = { it.id }) { message ->
                RawMessageCard(message)
            }
        }
    }
}

@Composable
private fun DiagnosticPanel(accessEnabled: Boolean, rawCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("采集诊断", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricTile(
                label = "通知访问",
                value = if (accessEnabled) "已开启" else "未开启",
                color = if (accessEnabled) Color(0xFFEAF5F1) else Color(0xFFFFECE8),
                modifier = Modifier.weight(1f)
            )
            MetricTile("已捕获", "$rawCount 条", Color(0xFFEFF2FB), Modifier.weight(1f))
        }
        PlainPanel(
            title = "数据源状态",
            body = "当前读取的是系统通知内容，包括短信 App 弹出的短信通知；没有申请 RECEIVE_SMS，因此不会直接扫描短信数据库。"
        )
    }
}

@Composable
private fun RawMessageCard(message: RawMessageEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(message.appName, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                Text(formatTime(message.postedAtMillis), style = MaterialTheme.typography.labelMedium)
            }
            Text(message.sourcePackage, style = MaterialTheme.typography.labelSmall, color = Color(0xFF687271))
            message.title?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontWeight = FontWeight.Medium)
            }
            Text(message.body ?: message.combinedText, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PermissionBanner() {
    val context = LocalContext.current
    val notificationAccessEnabled = remember { isNotificationListenerEnabled(context) }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val canPostNotifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    if (notificationAccessEnabled && canPostNotifications) return

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E8)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("权限未完整开启", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!notificationAccessEnabled) {
                    Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                        Text("通知访问")
                    }
                }
                if (!canPostNotifications && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    OutlinedButton(onClick = { requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text("提醒权限")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onCreateSample: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("还没有取件码", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("可以先生成一条示例，确认列表和编辑流程。", color = Color(0xFF53605D))
            Button(onClick = onCreateSample) { Text("生成示例") }
        }
    }
}

@Composable
private fun PickupItemCard(item: PickupItemEntity, onOpen: () -> Unit, onStatus: (String) -> Unit) {
    val accent = when (item.status) {
        PickupStatus.COLLECTED -> Color(0xFF8B6D19)
        PickupStatus.IGNORED -> Color(0xFF81556C)
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SourcePill(item.appSource, accent)
                Text(formatTime(item.postedAtMillis), style = MaterialTheme.typography.labelMedium, color = Color(0xFF687271))
            }
            Text(item.pickupCode, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(item.station ?: "未识别驿站", maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("未取", item.status == PickupStatus.PENDING) { onStatus(PickupStatus.PENDING) }
                StatusChip("已取", item.status == PickupStatus.COLLECTED) { onStatus(PickupStatus.COLLECTED) }
                StatusChip("忽略", item.status == PickupStatus.IGNORED) { onStatus(PickupStatus.IGNORED) }
            }
        }
    }
}

@Composable
private fun SourcePill(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Dot(selected = true, color = color)
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StatusChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun PickupDetailScreen(
    item: PickupItemEntity,
    onSave: (String, String?, String, String) -> Unit
) {
    var appSource by remember(item.id) { mutableStateOf(item.appSource) }
    var station by remember(item.id) { mutableStateOf(item.station.orEmpty()) }
    var pickupCode by remember(item.id) { mutableStateOf(item.pickupCode) }
    var status by remember(item.id) { mutableStateOf(item.status) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(value = appSource, onValueChange = { appSource = it }, label = { Text("应用归属") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = station, onValueChange = { station = it }, label = { Text("驿站地点") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = pickupCode, onValueChange = { pickupCode = it }, label = { Text("取件码") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                StatusChip("未取", status == PickupStatus.PENDING) { status = PickupStatus.PENDING }
                StatusChip("已取", status == PickupStatus.COLLECTED) { status = PickupStatus.COLLECTED }
                StatusChip("忽略", status == PickupStatus.IGNORED) { status = PickupStatus.IGNORED }
            }
            PlainPanel(
                title = "原始文本",
                body = item.rawText
            )
            Text("置信度 ${"%.0f".format(item.confidence * 100)}%", color = Color(0xFF53605D))
            Button(
                onClick = { onSave(appSource, station, pickupCode, status) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }
        }
    }
}

@Composable
private fun SettingsScreen(viewModel: PickupViewModel) {
    val enabledPackages by viewModel.enabledPackages.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val modelStatus by viewModel.modelStatus.collectAsState()
    val modelTestResult by viewModel.modelTestResult.collectAsState()
    val importModel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importModel(uri)
    }
    var sample by remember {
        mutableStateOf("您的京东快递已到xx大学xx园x号菜鸟驿站京东点，请凭提货号A-1024前往领取")
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PlainPanel(
                title = "抽取引擎",
                body = "当前模式：${modelStatus.modeLabel}\n状态：${modelStatus.engineState}\n模型：${modelStatus.modelFileName ?: "未导入"}${modelStatus.modelFileBytes?.let { "\n大小：${formatBytes(it)}" } ?: ""}${modelStatus.lastError?.let { "\n错误：$it" } ?: ""}\n说明：导入后可立即手动加载；重新启动 App 时会检测已导入模型并尝试加载。推理只在模型分析或规则置信度不足时触发。"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { importModel.launch(arrayOf("*/*")) }) { Text("导入模型") }
                OutlinedButton(
                    enabled = !modelStatus.busy,
                    onClick = { viewModel.loadModel() }
                ) {
                    Text(if (modelStatus.ready) "已加载" else "加载模型")
                }
            }
        }
        item {
            Text("监听白名单", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ListenerPreferences.defaultPackages.forEach { source ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(source.displayName, fontWeight = FontWeight.Medium)
                        Text(source.packageName, style = MaterialTheme.typography.labelSmall, color = Color(0xFF687271))
                    }
                    Switch(
                        checked = enabledPackages.contains(source.packageName),
                        onCheckedChange = { viewModel.togglePackage(source, it) }
                    )
                }
            }
        }
        item {
            Text("规则实验室", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = sample,
                onValueChange = { sample = it },
                label = { Text("通知文本") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = { viewModel.testExtraction(sample) }) { Text("分析") }
                OutlinedButton(
                    enabled = !modelStatus.busy,
                    onClick = { viewModel.testModelExtraction(sample) }
                ) { Text("模型分析") }
                OutlinedButton(onClick = { viewModel.saveSample(sample) }) { Text("入库") }
            }
            LaunchedEffect(Unit) { viewModel.testExtraction(sample) }
            testResult?.let {
                Spacer(modifier = Modifier.height(8.dp))
                PlainPanel(
                    title = "规则分析结果",
                    body = "来源：${it.appSource}\n驿站：${it.station ?: "未识别"}\n取件码：${it.pickupCode ?: "未识别"}\n置信度：${"%.0f".format(it.confidence * 100)}%"
                )
            }
            modelTestResult?.let {
                Spacer(modifier = Modifier.height(8.dp))
                PlainPanel(
                    title = "模型分析结果",
                    body = "来源：${it.appSource}\n驿站：${it.station ?: "未识别"}\n取件码：${it.pickupCode ?: "未识别"}\n置信度：${"%.0f".format(it.confidence * 100)}%"
                )
            }
        }
    }
}

@Composable
private fun PlainPanel(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, color = Color(0xFF53605D))
        }
    }
}

@Composable
private fun Dot(selected: Boolean, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier = Modifier
            .width(if (selected) 8.dp else 6.dp)
            .height(if (selected) 8.dp else 6.dp)
            .clip(CircleShape)
            .background(if (selected) color else Color(0xFFB8C1BD))
    )
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
    return enabled.contains(context.packageName, ignoreCase = true)
}

private fun formatTime(millis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(millis))
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    return "%.1f MB".format(mb)
}
