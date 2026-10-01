package com.github.nrfr.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.nrfr.R
import com.github.nrfr.data.CountryPresets
import com.github.nrfr.data.PresetCarriers
import com.github.nrfr.manager.CarrierConfigManager
import com.github.nrfr.manager.OperationState
import com.github.nrfr.manager.toSimCardsUserMessage
import com.github.nrfr.model.SimCardInfo
import com.github.nrfr.ui.theme.NrfrAdaptive
import com.github.nrfr.ui.theme.NrfrCardShape
import com.github.nrfr.ui.theme.NrfrPill
import com.github.nrfr.ui.theme.NrfrSelectShape
import com.github.nrfr.ui.theme.clickableNoIndication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(onShowAbout: () -> Unit) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    var selectedSlot by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedSimCard by remember { mutableStateOf<SimCardInfo?>(null) }
    var selectedCountryCode by remember { mutableStateOf("") }
    var customCountryCode by remember { mutableStateOf("") }
    var isCustomCountryCode by remember { mutableStateOf(false) }
    var selectedCarrier by remember { mutableStateOf<PresetCarriers.CarrierPreset?>(null) }
    var customCarrierName by remember { mutableStateOf("") }
    var simCards by remember { mutableStateOf<List<SimCardInfo>>(emptyList()) }
    var isLoadingSimCards by remember { mutableStateOf(false) }
    var simCardsError by remember { mutableStateOf<String?>(null) }
    var isSimCardMenuExpanded by remember { mutableStateOf(false) }
    var isCountryCodeMenuExpanded by remember { mutableStateOf(false) }
    var isCarrierMenuExpanded by remember { mutableStateOf(false) }
    var isApplyingConfig by remember { mutableStateOf(false) }
    var activeAction by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(statusMessage) {
        val msg = statusMessage ?: return@LaunchedEffect
        kotlinx.coroutines.delay(2800)
        if (statusMessage == msg) statusMessage = null
    }

    LaunchedEffect(appContext, refreshTrigger) {
        isLoadingSimCards = true
        simCardsError = null
        runCatching {
            withContext(Dispatchers.IO) {
                CarrierConfigManager.getSimCards(appContext)
            }
        }.onSuccess {
            simCards = it
            if (it.isEmpty()) {
                simCardsError = "未检测到可用 SIM 卡，请确认 Shizuku 已授权"
            }
        }.onFailure {
            simCardsError = it.toSimCardsUserMessage()
        }
        isLoadingSimCards = false
    }

    LaunchedEffect(simCards, selectedSlot) {
        if (simCards.isEmpty()) {
            selectedSimCard = null
            return@LaunchedEffect
        }
        val slot = selectedSlot ?: simCards.minByOrNull { it.slot }?.slot
        if (selectedSlot == null && slot != null) {
            selectedSlot = slot
        }
        selectedSimCard = simCards.find { it.slot == slot } ?: simCards.first()
    }

    fun refreshSimCards() {
        refreshTrigger += 1
    }

    val effectiveSimCard = selectedSimCard ?: simCards.firstOrNull()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 88.dp)
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 与官方 1.0.3 一致：紫色盾牌人物图标
                    Icon(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                        contentDescription = "App Icon",
                        modifier = Modifier.size(40.dp),
                        tint = Color(0xFF8300FD)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "Nrfr",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp,
                                fontSize = 22.sp,
                                lineHeight = 26.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "运营商覆盖",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onShowAbout,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "关于",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CurrentConfigsOverview(
                    simCards = simCards,
                    isLoading = isLoadingSimCards,
                    errorMessage = simCardsError
                )

                SectionLabel("配置目标")
                SimCardSelector(
                    simCards = simCards,
                    selectedSimCard = effectiveSimCard,
                    isExpanded = isSimCardMenuExpanded,
                    onExpandedChange = { isSimCardMenuExpanded = it },
                    onSimCardSelected = {
                        selectedSlot = it.slot
                        selectedSimCard = it
                    }
                )

                SectionLabel("覆盖内容")
                CountryCodeSelector(
                    selectedCountryCode = selectedCountryCode,
                    isCustomCountryCode = isCustomCountryCode,
                    customCountryCode = customCountryCode,
                    isExpanded = isCountryCodeMenuExpanded,
                    onExpandedChange = { isCountryCodeMenuExpanded = it },
                    onCountryCodeSelected = { code ->
                        selectedCountryCode = code
                        isCustomCountryCode = false
                        if (selectedCarrier?.region != code) {
                            selectedCarrier = null
                            customCarrierName = ""
                        }
                    },
                    onCustomSelected = {
                        isCustomCountryCode = true
                        selectedCountryCode = customCountryCode
                    }
                )

                if (isCustomCountryCode) {
                    CustomCountryCodeInput(
                        value = customCountryCode,
                        onValueChange = {
                            if (it.length <= 2 && it.all { char -> char.isLetter() }) {
                                customCountryCode = it.uppercase()
                                selectedCountryCode = it.uppercase()
                            }
                        }
                    )
                }

                CarrierSelector(
                    selectedCarrier = selectedCarrier,
                    selectedCountryCode = selectedCountryCode,
                    isCustomCountryCode = isCustomCountryCode,
                    isExpanded = isCarrierMenuExpanded,
                    onExpandedChange = { isCarrierMenuExpanded = it },
                    onCarrierSelected = { carrier ->
                        selectedCarrier = carrier
                        customCarrierName = carrier.displayName
                        if (carrier.region.length == 2) {
                            selectedCountryCode = carrier.region
                            isCustomCountryCode = false
                        }
                    }
                )

                if (selectedCarrier?.name == "自定义") {
                    CustomCarrierNameInput(
                        value = customCarrierName,
                        onValueChange = { customCarrierName = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
            }

            SoftStatusBanner(
                message = statusMessage,
                isError = statusIsError,
                onDismiss = { statusMessage = null },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 72.dp)
            )

            // 无底栏白卡：按钮直接浮在页面背景上
            ActionButtons(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                effectiveSimCard = effectiveSimCard,
                selectedCountryCode = selectedCountryCode,
                isCustomCountryCode = isCustomCountryCode,
                customCountryCode = customCountryCode,
                selectedCarrier = selectedCarrier,
                customCarrierName = customCarrierName,
                isApplyingConfig = isApplyingConfig,
                activeAction = activeAction,
                onReset = { simCard ->
                    if (!isApplyingConfig && OperationState.begin()) {
                        coroutineScope.launch {
                            isApplyingConfig = true
                            activeAction = "reset"
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    CarrierConfigManager.resetCarrierConfig(appContext, simCard.subId)
                                }
                                statusIsError = false
                                statusMessage = buildOperationMessage("设置已还原", result.warnings)
                                refreshSimCards()
                                selectedCountryCode = ""
                                selectedCarrier = null
                                customCarrierName = ""
                            } catch (e: Exception) {
                                statusIsError = true
                                statusMessage = "还原失败: ${formatErrorMessage(e)}"
                            } finally {
                                OperationState.end()
                                isApplyingConfig = false
                                activeAction = null
                            }
                        }
                    }
                },
                onSave = { simCard ->
                    if (!isApplyingConfig && OperationState.begin()) {
                        val carrierName = if (selectedCarrier?.name == "自定义") {
                            customCarrierName.takeIf { it.isNotEmpty() }
                        } else {
                            selectedCarrier?.displayName
                        }
                        val countryCode = if (isCustomCountryCode) {
                            customCountryCode.takeIf { it.length == 2 }
                        } else {
                            selectedCountryCode
                        }

                        coroutineScope.launch {
                            isApplyingConfig = true
                            activeAction = "save"
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    CarrierConfigManager.setCarrierConfig(
                                        appContext,
                                        simCard.subId,
                                        countryCode,
                                        carrierName
                                    )
                                }
                                statusIsError = false
                                statusMessage = buildOperationMessage("设置已保存", result.warnings)
                                refreshSimCards()
                            } catch (e: Exception) {
                                statusIsError = true
                                statusMessage = "保存失败: ${formatErrorMessage(e)}"
                            } finally {
                                OperationState.end()
                                isApplyingConfig = false
                                activeAction = null
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimCardSelector(
    simCards: List<SimCardInfo>,
    selectedSimCard: SimCardInfo?,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSimCardSelected: (SimCardInfo) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    SelectField(
        label = "SIM 卡",
        value = selectedSimCard?.let { "SIM ${it.slot} · ${it.carrierName}" } ?: "",
        expanded = isExpanded,
        iconGlyph = "SIM",
        onClick = { onExpandedChange(true) }
    )

    if (isExpanded) {
        ModalBottomSheet(
            onDismissRequest = { onExpandedChange(false) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = NrfrCardShape,
            tonalElevation = 0.dp
        ) {
            SheetTitle("选择 SIM 卡")
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .padding(horizontal = 8.dp)
            ) {
                items(simCards) { simCard ->
                    SelectorSheetOption(
                        text = "SIM ${simCard.slot} · ${simCard.carrierName}",
                        supportingText = formatConfigSummary(simCard),
                        selected = selectedSimCard?.subId == simCard.subId,
                        onClick = {
                            onSimCardSelected(simCard)
                            onExpandedChange(false)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CurrentConfigsOverview(
    simCards: List<SimCardInfo>,
    isLoading: Boolean,
    errorMessage: String?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = NrfrCardShape,
        color = NrfrAdaptive.card(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "当前状态",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = NrfrAdaptive.link()
                    )
                }
            }

            when {
                errorMessage != null && simCards.isEmpty() -> {
                    Text(
                        errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                simCards.isEmpty() -> {
                    Text(
                        if (isLoading) "正在读取 SIM…" else "未检测到可用 SIM 卡",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                else -> {
                    simCards.sortedBy { it.slot }.forEach { simCard ->
                        SimConfigSummary(simCard = simCard)
                    }
                }
            }
        }
    }
}

@Composable
private fun SimConfigSummary(simCard: SimCardInfo) {
    val hasOverride = simCard.currentConfig.isNotEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(NrfrAdaptive.wash())
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .height(34.dp)
                .widthIn(min = 52.dp)
                .clip(NrfrPill)
                .background(MaterialTheme.colorScheme.onSurface)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "SIM${simCard.slot}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.surface
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                simCard.carrierName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                formatConfigSummary(simCard),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = if (hasOverride) FontFamily.Monospace else FontFamily.SansSerif
                ),
                color = if (hasOverride) NrfrAdaptive.codeAccent() else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            if (hasOverride) "覆盖" else "默认",
            modifier = Modifier
                .clip(NrfrPill)
                .background(if (hasOverride) NrfrAdaptive.linkSoft() else NrfrAdaptive.card())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (hasOverride) NrfrAdaptive.link() else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatConfigSummary(simCard: SimCardInfo): String {
    if (simCard.currentConfig.isEmpty()) {
        return "无覆盖配置"
    }
    return simCard.currentConfig.entries.joinToString(" · ") { (key, value) ->
        "$key: $value"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountryCodeSelector(
    selectedCountryCode: String,
    isCustomCountryCode: Boolean,
    customCountryCode: String,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCountryCodeSelected: (String) -> Unit,
    onCustomSelected: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    val displayText = when {
        isCustomCountryCode -> "自定义"
        selectedCountryCode.isEmpty() -> ""
        else -> CountryPresets.countries.find { it.code == selectedCountryCode }
            ?.let { "${it.name} (${it.code})" }
            ?: selectedCountryCode
    }

    SelectField(
        label = "国家码",
        value = displayText,
        expanded = isExpanded,
        iconGlyph = "CN",
        onClick = { onExpandedChange(true) }
    )

    if (isExpanded) {
        ModalBottomSheet(
            onDismissRequest = { onExpandedChange(false) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = NrfrCardShape,
            tonalElevation = 0.dp
        ) {
            SheetTitle("选择国家码")
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .padding(horizontal = 8.dp)
            ) {
                items(CountryPresets.countries) { countryInfo ->
                    SelectorSheetOption(
                        text = "${countryInfo.name} (${countryInfo.code})",
                        selected = !isCustomCountryCode && selectedCountryCode == countryInfo.code,
                        onClick = {
                            onCountryCodeSelected(countryInfo.code)
                            onExpandedChange(false)
                        }
                    )
                }
                item {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    SelectorSheetOption(
                        text = "自定义",
                        selected = isCustomCountryCode,
                        onClick = {
                            onCustomSelected()
                            onExpandedChange(false)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CustomCountryCodeInput(
    value: String,
    onValueChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("自定义国家码（2 位字母）") },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        singleLine = true,
        shape = NrfrSelectShape,
        modifier = Modifier.fillMaxWidth()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarrierSelector(
    selectedCarrier: PresetCarriers.CarrierPreset?,
    selectedCountryCode: String,
    isCustomCountryCode: Boolean,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onCarrierSelected: (PresetCarriers.CarrierPreset) -> Unit
) {
    val carrierMenuItems = remember(selectedCountryCode, isCustomCountryCode) {
        buildCarrierMenuItems(
            selectedCountryCode = selectedCountryCode,
            isCustomCountryCode = isCustomCountryCode
        )
    }
    val sheetState = rememberModalBottomSheetState()

    SelectField(
        label = "运营商名称",
        value = selectedCarrier?.name ?: "",
        expanded = isExpanded,
        iconGlyph = "OP",
        onClick = { onExpandedChange(true) }
    )

    if (isExpanded) {
        ModalBottomSheet(
            onDismissRequest = { onExpandedChange(false) },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = NrfrCardShape,
            tonalElevation = 0.dp
        ) {
            SheetTitle("选择运营商")
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .padding(horizontal = 8.dp)
            ) {
                items(carrierMenuItems) { item ->
                    when (item) {
                        is CarrierMenuItem.Header -> {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is CarrierMenuItem.Option -> {
                            SelectorSheetOption(
                                text = item.carrier.name,
                                selected = selectedCarrier == item.carrier,
                                onClick = {
                                    onCarrierSelected(item.carrier)
                                    onExpandedChange(false)
                                }
                            )
                        }
                        CarrierMenuItem.Divider -> {
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SelectField(
    label: String,
    value: String,
    expanded: Boolean,
    iconGlyph: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(NrfrSelectShape)
            .background(NrfrAdaptive.card())
            .border(
                width = if (expanded) 1.5.dp else 1.dp,
                color = if (expanded) NrfrAdaptive.link() else MaterialTheme.colorScheme.outline,
                shape = NrfrSelectShape
            )
            .clickableNoIndication(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NrfrAdaptive.linkSoft()),
            contentAlignment = Alignment.Center
        ) {
            Text(
                iconGlyph,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                ),
                color = NrfrAdaptive.link()
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                value.ifEmpty { "点击选择" },
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = if (value.isEmpty()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (expanded) NrfrAdaptive.linkSoft() else NrfrAdaptive.wash()),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = if (expanded) NrfrAdaptive.link() else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SheetTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
    )
}

@Composable
private fun SelectorSheetOption(
    text: String,
    onClick: () -> Unit,
    supportingText: String? = null,
    selected: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) NrfrAdaptive.linkSoft() else Color.Transparent)
            .clickableNoIndication(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) NrfrAdaptive.link() else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!supportingText.isNullOrBlank()) {
            Text(
                supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildCarrierMenuItems(
    selectedCountryCode: String = "",
    isCustomCountryCode: Boolean = false
): List<CarrierMenuItem> {
    val carriersByRegion = PresetCarriers.presets
        .filter { it.region.isNotEmpty() }
        .groupBy { it.region }
    val countryNames = CountryPresets.countries.associate { it.code to it.name }
    val result = mutableListOf<CarrierMenuItem>()

    val regionOrder = when {
        !isCustomCountryCode && selectedCountryCode.length == 2 &&
            carriersByRegion.containsKey(selectedCountryCode.uppercase()) -> {
            listOf(selectedCountryCode.uppercase())
        }
        else -> CountryPresets.countries.map { it.code }
    }

    regionOrder.forEach { region ->
        val carriers = carriersByRegion[region] ?: return@forEach
        result.add(CarrierMenuItem.Header(countryNames[region] ?: region))
        carriers.sortedBy { it.name }.forEach { carrier ->
            result.add(CarrierMenuItem.Option(carrier))
        }
        if (regionOrder.size > 1) {
            result.add(CarrierMenuItem.Divider)
        }
    }

    if (result.lastOrNull() is CarrierMenuItem.Divider) {
        result.removeAt(result.lastIndex)
    }

    PresetCarriers.presets
        .filter { it.region.isEmpty() }
        .sortedBy { it.name }
        .forEach { carrier ->
            result.add(CarrierMenuItem.Option(carrier))
        }

    return result
}

private sealed class CarrierMenuItem {
    data class Header(val title: String) : CarrierMenuItem()
    data class Option(val carrier: PresetCarriers.CarrierPreset) : CarrierMenuItem()
    object Divider : CarrierMenuItem()
}

@Composable
private fun CustomCarrierNameInput(
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("自定义运营商名称") },
        shape = NrfrSelectShape,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun buildOperationMessage(successMessage: String, warnings: List<String>): String {
    if (warnings.isEmpty()) return successMessage
    return "$successMessage，${warnings.joinToString("；")}".take(320)
}

private fun formatErrorMessage(error: Throwable): String {
    var current: Throwable = error
    while (current.cause != null && current.message.isNullOrBlank()) {
        current = current.cause!!
    }
    return current.message?.lineSequence()?.firstOrNull()?.take(160)
        ?: current.javaClass.simpleName.ifBlank { "未知错误" }
}

@Composable
private fun SoftStatusBanner(
    message: String?,
    isError: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        modifier = modifier,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 3 },
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 3 }
    ) {
        if (message.isNullOrBlank()) return@AnimatedVisibility
        val accent = if (isError) MaterialTheme.colorScheme.error else NrfrAdaptive.link()
        val container = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            NrfrAdaptive.okBg()
        }
        Surface(
            shape = NrfrPill,
            color = container,
            tonalElevation = 0.dp,
            shadowElevation = 4.dp,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
            modifier = Modifier.clickableNoIndication(onClick = onDismiss)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ActionButtons(
    modifier: Modifier = Modifier,
    effectiveSimCard: SimCardInfo?,
    selectedCountryCode: String,
    isCustomCountryCode: Boolean,
    customCountryCode: String,
    selectedCarrier: PresetCarriers.CarrierPreset?,
    customCarrierName: String,
    isApplyingConfig: Boolean,
    activeAction: String?,
    onReset: (SimCardInfo) -> Unit,
    onSave: (SimCardInfo) -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = { effectiveSimCard?.let(onReset) },
            modifier = Modifier
                .weight(1f)
                .height(40.dp),
            enabled = !isApplyingConfig && effectiveSimCard != null,
            shape = NrfrPill,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = NrfrAdaptive.card(),
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = NrfrAdaptive.card().copy(alpha = 0.7f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Text(
                when {
                    isApplyingConfig && activeAction == "reset" -> "还原中…"
                    isApplyingConfig -> "请稍候…"
                    else -> "还原配置"
                },
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }

        Button(
            onClick = { effectiveSimCard?.let(onSave) },
            modifier = Modifier
                .weight(1f)
                .height(40.dp),
            enabled = !isApplyingConfig && effectiveSimCard != null && (
                (isCustomCountryCode && customCountryCode.length == 2) ||
                    (!isCustomCountryCode && selectedCountryCode.isNotEmpty()) ||
                    (selectedCarrier != null &&
                        (selectedCarrier.name != "自定义" || customCarrierName.isNotEmpty()))
                ),
            shape = NrfrPill,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onSurface,
                contentColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.outline,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                disabledElevation = 0.dp
            )
        ) {
            Text(
                when {
                    isApplyingConfig && activeAction == "save" -> "保存中…"
                    isApplyingConfig -> "请稍候…"
                    else -> "保存配置"
                },
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }
    }
}
