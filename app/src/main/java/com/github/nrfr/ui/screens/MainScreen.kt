package com.github.nrfr.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.nrfr.R
import com.github.nrfr.data.CountryPresets
import com.github.nrfr.data.PresetCarriers
import androidx.compose.runtime.saveable.rememberSaveable
import com.github.nrfr.manager.CarrierConfigManager
import com.github.nrfr.manager.OperationState
import com.github.nrfr.manager.toSimCardsUserMessage
import com.github.nrfr.model.SimCardInfo
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
    val coroutineScope = rememberCoroutineScope()

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

    // 保存/还原完成后立即用最新数据刷新 SIM 配置卡片与顶部选择器
    fun refreshSimCards() {
        refreshTrigger += 1
    }

    val effectiveSimCard = selectedSimCard ?: simCards.firstOrNull()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            modifier = Modifier.size(48.dp),
                            contentDescription = "App Icon",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nrfr")
                    }
                },
                actions = {
                    IconButton(onClick = onShowAbout) {
                        Icon(Icons.Default.Info, contentDescription = "关于")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SIM卡选择
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

            // 国家码选择
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

            // 自定义国家码输入框
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

            // 运营商选择（顺序与国家码列表一致；已选国家码时仅显示该地区）
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

            // 自定义运营商名称输入框
            if (selectedCarrier?.name == "自定义") {
                CustomCarrierNameInput(
                    value = customCarrierName,
                    onValueChange = { customCarrierName = it }
                )
            }

            // 在三个选择框下方展示全部 SIM 的当前覆盖配置。
            CurrentConfigsOverview(
                simCards = simCards,
                isLoading = isLoadingSimCards,
                errorMessage = simCardsError
            )

            Spacer(modifier = Modifier.weight(1f))

            // 按钮行
            ActionButtons(
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
                                Toast.makeText(
                                    context,
                                    buildOperationMessage("设置已还原", result.warnings),
                                    Toast.LENGTH_LONG
                                ).show()
                                refreshSimCards()
                                selectedCountryCode = ""
                                selectedCarrier = null
                                customCarrierName = ""
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "还原失败: ${formatErrorMessage(e)}",
                                    Toast.LENGTH_LONG
                                ).show()
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
                                Toast.makeText(
                                    context,
                                    buildOperationMessage("设置已保存", result.warnings),
                                    Toast.LENGTH_LONG
                                ).show()
                                refreshSimCards()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "保存失败: ${formatErrorMessage(e)}",
                                    Toast.LENGTH_LONG
                                ).show()
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

    DropdownLauncherField(
        value = selectedSimCard?.let { "SIM ${it.slot} (${it.carrierName})" } ?: "",
        label = "选择SIM卡",
        expanded = isExpanded,
        onClick = { onExpandedChange(true) }
    )

    if (isExpanded) {
        ModalBottomSheet(
            onDismissRequest = { onExpandedChange(false) },
            sheetState = sheetState
        ) {
            SheetTitle("选择SIM卡")
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                items(simCards) { simCard ->
                    SelectorSheetOption(
                        text = "SIM ${simCard.slot} (${simCard.carrierName})",
                        supportingText = formatConfigSummary(simCard),
                        onClick = {
                            onSimCardSelected(simCard)
                            onExpandedChange(false)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CurrentConfigsOverview(
    simCards: List<SimCardInfo>,
    isLoading: Boolean,
    errorMessage: String?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                "SIM 配置",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (errorMessage != null && simCards.isEmpty()) {
                Text(
                    errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (simCards.isEmpty()) {
                Text(
                    if (isLoading) "正在读取 SIM 配置..." else "未检测到可用 SIM 卡",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                simCards.sortedBy { it.slot }.forEachIndexed { index, simCard ->
                    SimConfigSummary(simCard = simCard)
                    if (index != simCards.lastIndex) {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
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
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "SIM ${simCard.slot} (${simCard.carrierName})",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                formatConfigSummary(simCard),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (hasOverride) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    "已覆盖",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
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

    DropdownLauncherField(
        value = displayText,
        label = "选择国家码",
        expanded = isExpanded,
        onClick = { onExpandedChange(true) }
    )

    if (isExpanded) {
        ModalBottomSheet(
            onDismissRequest = { onExpandedChange(false) },
            sheetState = sheetState
        ) {
            SheetTitle("选择国家码")
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                items(CountryPresets.countries) { countryInfo ->
                    SelectorSheetOption(
                        text = "${countryInfo.name} (${countryInfo.code})",
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
                        onClick = {
                            onCustomSelected()
                            onExpandedChange(false)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomCountryCodeInput(
    value: String,
    onValueChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("自定义国家码 (2位字母)") },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                focusManager.clearFocus()
            }
        ),
        singleLine = true,
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

    DropdownLauncherField(
        value = selectedCarrier?.name ?: "",
        label = "选择运营商",
        expanded = isExpanded,
        onClick = { onExpandedChange(true) }
    )

    if (isExpanded) {
        ModalBottomSheet(
            onDismissRequest = { onExpandedChange(false) },
            sheetState = sheetState
        ) {
            SheetTitle("选择运营商")
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
            ) {
                items(carrierMenuItems) { item ->
                    when (item) {
                        is CarrierMenuItem.Header -> {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        is CarrierMenuItem.Option -> {
                            SelectorSheetOption(
                                text = item.carrier.name,
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
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownLauncherField(
    value: String,
    label: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    value.ifEmpty { " " },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }
    }
}

@Composable
private fun SheetTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun SelectorSheetOption(
    text: String,
    onClick: () -> Unit,
    supportingText: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomCarrierNameInput(
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("自定义运营商名称") },
        modifier = Modifier.fillMaxWidth()
    )
}

private fun buildOperationMessage(successMessage: String, warnings: List<String>): String {
    if (warnings.isEmpty()) {
        return successMessage
    }
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
private fun ActionButtons(
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedButton(
            onClick = { effectiveSimCard?.let(onReset) },
            modifier = Modifier.weight(1f),
            enabled = !isApplyingConfig && effectiveSimCard != null
        ) {
            Text(
                when {
                    isApplyingConfig && activeAction == "reset" -> "还原中..."
                    isApplyingConfig -> "请稍候..."
                    else -> "还原设置"
                }
            )
        }

        Button(
            onClick = { effectiveSimCard?.let(onSave) },
            modifier = Modifier.weight(1f),
            enabled = !isApplyingConfig && effectiveSimCard != null && (
                    (isCustomCountryCode && customCountryCode.length == 2) ||
                            (!isCustomCountryCode && selectedCountryCode.isNotEmpty()) ||
                            (selectedCarrier != null && (selectedCarrier.name != "自定义" || customCarrierName.isNotEmpty()))
                    )
        ) {
            Text(
                when {
                    isApplyingConfig && activeAction == "save" -> "保存中..."
                    isApplyingConfig -> "请稍候..."
                    else -> "保存生效"
                }
            )
        }
    }
}
