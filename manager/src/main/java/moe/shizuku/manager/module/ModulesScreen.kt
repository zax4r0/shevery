@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package moe.shizuku.manager.module

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import moe.shizuku.manager.R
import moe.shizuku.manager.ui.compose.ExpressiveSwitch
import moe.shizuku.manager.ui.compose.MonospaceLog
import moe.shizuku.manager.ui.compose.ShizukuIcon
import moe.shizuku.manager.ui.compose.ShizukuLazyScaffold
import moe.shizuku.manager.utils.AiExplainUtil

private val MODULE_MIME_TYPES = arrayOf(
    "application/zip",
    "application/octet-stream",
    "application/x-zip-compressed"
)

@Composable
fun ModulesScreen(onOpenWebUi: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) } // 0: Installed, 1: Catalog
    var showCatalog by remember { mutableStateOf(false) }
    var modules by remember { mutableStateOf<List<AdbModule>>(emptyList(), neverEqualPolicy()) }
    var catalogModules by remember { mutableStateOf<List<CatalogModule>>(emptyList(), neverEqualPolicy()) }
    var catalogLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var checkingUpdates by remember { mutableStateOf(false) }
    var updatingModuleId by remember { mutableStateOf<String?>(null) }
    var downloadingCatalogId by remember { mutableStateOf<String?>(null) }
    var output by remember { mutableStateOf<Pair<String, String>?>(null) }
    var deleteTarget by remember { mutableStateOf<AdbModule?>(null) }
    var pendingCommand by remember { mutableStateOf<ModuleCommandRequest?>(null) }
    var runningModuleId by remember { mutableStateOf<String?>(null) }
    var lastRunModule by remember { mutableStateOf<AdbModule?>(null) }
    var aiExplanation by remember { mutableStateOf<String?>(null) }
    var aiLoading by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            modules = AdbModuleManager.listModules(context)
        }
    }

    fun checkAllUpdates() {
        scope.launch {
            checkingUpdates = true
            Toast.makeText(context, context.getString(R.string.modules_checking_updates), Toast.LENGTH_SHORT).show()
            var foundUpdates = 0
            val updated = modules.map { m ->
                val info = CatalogModuleManager.checkModuleUpdate(m)
                if (info != null) foundUpdates++
                m.copy(updateInfo = info)
            }
            modules = updated
            checkingUpdates = false
            val msg = if (foundUpdates > 0) {
                context.getString(R.string.modules_update_available, "$foundUpdates")
            } else {
                context.getString(R.string.modules_no_updates)
            }
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun updateModule(module: AdbModule) {
        val info = module.updateInfo ?: return
        scope.launch {
            updatingModuleId = module.id
            Toast.makeText(context, context.getString(R.string.modules_updating), Toast.LENGTH_SHORT).show()
            runCatching {
                val filename = "${module.id}-update-${info.newVersion}.zip"
                CatalogModuleManager.downloadAndInstall(context, info.zipUrl, filename)
            }.onSuccess {
                Toast.makeText(
                    context,
                    context.getString(R.string.modules_update_success, info.newVersion),
                    Toast.LENGTH_SHORT
                ).show()
                reload()
            }.onFailure {
                Toast.makeText(
                    context,
                    "Update failed: ${it.message ?: it.javaClass.simpleName}",
                    Toast.LENGTH_LONG
                ).show()
            }
            updatingModuleId = null
        }
    }

    fun installFromCatalog(item: CatalogModule) {
        scope.launch {
            downloadingCatalogId = item.id
            Toast.makeText(context, context.getString(R.string.modules_downloading), Toast.LENGTH_SHORT).show()
            runCatching {
                val filename = "${item.id}-v${item.version}.zip"
                CatalogModuleManager.downloadAndInstall(context, item.downloadUrl, filename)
            }.onSuccess { m ->
                Toast.makeText(
                    context,
                    context.getString(R.string.modules_install_success, m.name),
                    Toast.LENGTH_SHORT
                ).show()
                reload()
            }.onFailure {
                Toast.makeText(
                    context,
                    context.getString(R.string.modules_install_failed, it.message ?: it.javaClass.simpleName),
                    Toast.LENGTH_LONG
                ).show()
            }
            downloadingCatalogId = null
        }
    }

    fun runModuleAction(module: AdbModule) {
        lastRunModule = module
        scope.launch {
            runningModuleId = module.id
            output = runCatching {
                val result = AdbModuleManager.runActionStreaming(module)
                context.getString(R.string.modules_action_result, result.exitCode) to result.combinedOutput
            }.getOrElse {
                context.getString(R.string.modules_action_failed) to (it.message ?: it.javaClass.simpleName)
            }
            runningModuleId = null
        }
    }

    val zipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                AdbModuleManager.install(context, uri)
            }.onSuccess { module ->
                Toast.makeText(
                    context,
                    context.getString(R.string.modules_install_success, module.name),
                    Toast.LENGTH_SHORT
                ).show()
                reload()
            }.onFailure {
                Toast.makeText(
                    context,
                    context.getString(R.string.modules_install_failed, it.message ?: it.javaClass.simpleName),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        modules = AdbModuleManager.listModules(context)
    }

    if (showCatalog) {
        moe.shizuku.manager.module.catalog.CatalogScreen(
            onNavigateUp = {
                showCatalog = false
                reload()
            }
        )
    } else {
        ShizukuLazyScaffold(
            title = stringResource(R.string.modules_title),
        onNavigateUp = null,
        actions = {
            if (selectedTab == 0) {
                OutlinedButton(
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    onClick = { checkAllUpdates() },
                    enabled = !checkingUpdates
                ) {
                    if (checkingUpdates) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        ShizukuIcon(
                            R.drawable.ic_outline_file_download_24,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(16.dp)
                        )
                        Text(stringResource(R.string.modules_check_updates))
                    }
                }
                Spacer(modifier = Modifier.size(8.dp))
                FilledTonalButton(
                    modifier = Modifier.height(36.dp),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    onClick = {
                        zipLauncher.launch(MODULE_MIME_TYPES)
                    }
                ) {
                    ShizukuIcon(
                        R.drawable.ic_outline_arrow_upward_24,
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(16.dp)
                    )
                    Text(stringResource(R.string.modules_install_zip))
                }
            } else {
                OutlinedButton(
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    onClick = {
                        scope.launch {
                            catalogLoading = true
                            catalogModules = CatalogModuleManager.loadCatalog()
                            catalogLoading = false
                        }
                    },
                    enabled = !catalogLoading
                ) {
                    Text(stringResource(R.string.modules_tab_catalog))
                }
            }
        }
    ) {
        item {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.modules_tab_installed)) }
                )
                Tab(
                    selected = false,
                    onClick = {
                        showCatalog = true
                    },
                    text = { Text(stringResource(R.string.modules_tab_catalog)) }
                )
            }
        }

        if (selectedTab == 0) {
            item {
                AnimatedContent(targetState = modules.isEmpty(), label = "module-empty-state") { empty ->
                    if (empty) {
                        EmptyModulesCard(onInstall = { zipLauncher.launch(MODULE_MIME_TYPES) })
                    }
                }
            }
            items(modules, key = { it.id }) { module ->
                ModuleCard(
                    module = module,
                    busy = runningModuleId == module.id,
                    updating = updatingModuleId == module.id,
                    trusted = ModuleSettings.isModuleTrusted(module.id),
                    modifier = Modifier.animateItem(),
                    onToggle = {
                        scope.launch {
                            AdbModuleManager.setEnabled(module, !module.enabled)
                            reload()
                        }
                    },
                    onRunAction = {
                        if (ModuleSettings.recommandForAction()) {
                            pendingCommand = ModuleCommandRequest(
                                module = module,
                                source = ModuleCommandSource.ACTION,
                                command = module.actionCommandPreview()
                            )
                        } else {
                            runModuleAction(module)
                        }
                    },
                    onRunService = {
                        scope.launch {
                            runningModuleId = module.id
                            output = runCatching {
                                val result = AdbModuleManager.runService(module)
                                context.getString(R.string.modules_service_result, result.exitCode) to result.combinedOutput
                            }.getOrElse {
                                context.getString(R.string.modules_service_failed) to (it.message ?: it.javaClass.simpleName)
                            }
                            runningModuleId = null
                        }
                    },
                    onOpenWebUi = {
                        onOpenWebUi(module.id)
                    },
                    onDelete = { deleteTarget = module },
                    onTrustChange = { trusted ->
                        scope.launch {
                            ModuleSettings.setModuleTrusted(module.id, trusted)
                            AdbModuleManager.setEnabled(module, trusted)
                            reload()
                        }
                    },
                    onUpdateModule = { updateModule(module) }
                )
            }
        }
    }
}

    output?.let { (title, text) ->
        AlertDialog(
            onDismissRequest = {
                output = null
                aiExplanation = null
                aiLoading = false
            },
            title = { Text(title) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    MonospaceLog(text)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Gemini AI Explanation",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (aiLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (aiExplanation != null) {
                        Text(
                            text = aiExplanation!!,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        val apiKey = ModuleSettings.getComputApiKey()
                        val hasApiKey = apiKey.isNotBlank()
                        Button(
                            onClick = {
                                aiLoading = true
                                scope.launch {
                                    val moduleInfo = lastRunModule?.let { "Module: ${it.name} (${it.id})" } ?: "Unknown Module"
                                    val scriptName = lastRunModule?.actionScript?.name ?: "action.sh"
                                    aiExplanation = AiExplainUtil.explainFailure(
                                        contextStr = "Shevery Android app, running module action script",
                                        inputDetail = "$moduleInfo, script = $scriptName",
                                        outputLog = text,
                                        apiKey = apiKey
                                    )
                                    aiLoading = false
                                }
                            },
                            enabled = hasApiKey,
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Ask Gemini")
                        }
                        if (!hasApiKey) {
                            Text(
                                text = "Please configure your Google AI Studio API Key in Shevery Settings to use Gemini AI Explanation.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        output = null
                        aiExplanation = null
                        aiLoading = false
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    deleteTarget?.let { module ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.modules_delete_title)) },
            text = { Text(stringResource(R.string.modules_delete_message, module.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            AdbModuleManager.delete(module)
                            deleteTarget = null
                            reload()
                        }
                    }
                ) {
                    Text(stringResource(R.string.modules_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.extraLarge
        )
    }

    pendingCommand?.let { request ->
        ReCommandDialog(
            request = request,
            busy = runningModuleId == request.module.id,
            onDismiss = { pendingCommand = null },
            onReject = { pendingCommand = null },
            onApprove = {
                pendingCommand = null
                runModuleAction(request.module)
            }
        )
    }
}

private fun AdbModule.actionCommandPreview(): String {
    val script = actionScript ?: return "sh action.sh"
    val content = runCatching { script.readText().trim() }.getOrDefault("")
    return buildString {
        append("sh ")
        append(script.absolutePath)
        if (content.isNotBlank()) {
            appendLine()
            appendLine()
            append(content)
        }
    }.take(MAX_RECOMMAND_COMMAND_CHARS)
}

private const val MAX_RECOMMAND_COMMAND_CHARS = 64 * 1024

@Composable
private fun EmptyModulesCard(onInstall: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.modules_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.modules_empty_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(onClick = onInstall) {
                ShizukuIcon(
                    R.drawable.ic_outline_arrow_upward_24,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(18.dp)
                )
                Text(stringResource(R.string.modules_install_zip))
            }
        }
    }
}

@Composable
private fun ModuleCard(
    module: AdbModule,
    busy: Boolean,
    updating: Boolean,
    trusted: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onRunAction: () -> Unit,
    onRunService: () -> Unit,
    onOpenWebUi: () -> Unit,
    onDelete: () -> Unit,
    onTrustChange: (Boolean) -> Unit,
    onUpdateModule: () -> Unit
) {
    var expanded by remember(module.id) { mutableStateOf(true) }
    var showTrustAction by remember(module.id, trusted) { mutableStateOf(false) }
    val containerColor by animateColorAsState(
        targetValue = if (trusted) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else if (module.enabled) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        animationSpec = tween(260),
        label = "module-container"
    )
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = {
                    expanded = true
                    showTrustAction = !showTrustAction
                }
            )
            .animateContentSize(animationSpec = tween(280)),
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        tonalElevation = 1.dp
    ) {
        Column {
            ModuleBanner(module)
            Column(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                module.updateInfo?.let { updateInfo ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.modules_update_available, updateInfo.newVersion),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Button(
                                onClick = onUpdateModule,
                                enabled = !updating,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                if (updating) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(stringResource(R.string.modules_update_button))
                                }
                            }
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = module.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        ModuleInfoLine(label = R.string.modules_version_label, value = module.version ?: "-")
                        ModuleInfoLine(label = R.string.modules_author_label, value = module.author ?: "-")
                    }
                    ExpressiveSwitch(
                        checked = module.enabled,
                        onCheckedChange = { onToggle() }
                    )
                }
                module.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) 4 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ModuleChips(module, trusted)
                AnimatedVisibility(
                    visible = busy,
                    enter = fadeIn(tween(180)) + expandVertically(tween(180)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(120))
                ) {
                    RunningRow()
                }
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(120)) + shrinkVertically(tween(160))
                ) {
                    ModuleActions(
                        module = module,
                        busy = busy,
                        onRunAction = onRunAction,
                        onRunService = onRunService,
                        onOpenWebUi = onOpenWebUi,
                        onDelete = onDelete,
                        trusted = trusted,
                        showTrustAction = showTrustAction,
                        onTrustChange = {
                            showTrustAction = false
                            onTrustChange(it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogModuleCard(
    item: CatalogModule,
    isInstalled: Boolean,
    busy: Boolean,
    onDownloadToDownloads: () -> Unit,
    onDownloadAndInstall: () -> Unit,
    onOpenGitHub: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (item.isOfficial || item.tags.any { it.equals("OFFICIAL", ignoreCase = true) }) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    text = "OFFICIAL",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "v${item.version} • ${item.author}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isInstalled) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Installed") }
                    )
                }
            }

            Text(
                text = item.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (item.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item.tags.forEach { tag ->
                        AssistChip(
                            onClick = {},
                            label = { Text(tag) }
                        )
                    }
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onDownloadToDownloads,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    ShizukuIcon(
                        R.drawable.ic_outline_file_download_24,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(16.dp)
                    )
                    Text(stringResource(R.string.modules_download_downloads))
                }

                Button(
                    onClick = onDownloadAndInstall,
                    enabled = !busy,
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    } else {
                        Text(if (isInstalled) stringResource(R.string.modules_update_button) else stringResource(R.string.modules_download_install))
                    }
                }

                if (item.githubRepo.isNotBlank()) {
                    TextButton(
                        onClick = onOpenGitHub,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("GitHub")
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleChips(module: AdbModule, trusted: Boolean) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(
            onClick = {},
            label = { Text(module.id) }
        )
        AssistChip(onClick = {}, label = { Text(module.formattedSize) })
        if (trusted) {
            AssistChip(onClick = {}, label = { Text(stringResource(R.string.modules_full_trust)) })
        }
    }
}

@Composable
private fun ModuleBanner(module: AdbModule) {
    val bannerFile = module.banner
    if (bannerFile != null && bannerFile.exists()) {
        val bitmap = remember(bannerFile.lastModified()) {
            BitmapFactory.decodeFile(bannerFile.absolutePath)
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun ModuleInfoLine(@StringRes label: Int, value: String) {
    Text(
        text = stringResource(label, value),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun RunningRow() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LoadingIndicator(Modifier.size(28.dp))
        Text(
            text = stringResource(R.string.modules_running),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ModuleActions(
    module: AdbModule,
    busy: Boolean,
    trusted: Boolean,
    showTrustAction: Boolean,
    onRunAction: () -> Unit,
    onRunService: () -> Unit,
    onOpenWebUi: () -> Unit,
    onDelete: () -> Unit,
    onTrustChange: (Boolean) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModuleButton(
            label = R.string.modules_run_action,
            icon = R.drawable.ic_outline_play_arrow_24,
            primary = true,
            enabled = !busy && module.enabled && module.hasAction && ModuleSettings.canRunAction(module),
            onClick = onRunAction
        )
        ModuleButton(
            label = R.string.modules_open_webui,
            icon = R.drawable.ic_outline_open_in_new_24,
            enabled = !busy && module.hasWebUi,
            onClick = onOpenWebUi
        )
        ModuleButton(
            label = R.string.modules_run_service,
            icon = R.drawable.ic_terminal_24,
            enabled = !busy &&
                    module.enabled &&
                    module.hasService &&
                    ModuleSettings.canRunService(module) &&
                    ModuleSettings.canRunBackground(module),
            onClick = onRunService
        )
        ModuleButton(
            label = R.string.modules_delete,
            icon = R.drawable.ic_delete_24,
            enabled = !busy,
            onClick = onDelete
        )
        if (showTrustAction) {
            ModuleButton(
                label = if (trusted) R.string.modules_untrust else R.string.modules_trust,
                icon = R.drawable.ic_outline_check_circle_24,
                enabled = !busy,
                onClick = { onTrustChange(!trusted) }
            )
        }
    }
}

@Composable
private fun ModuleButton(
    @StringRes label: Int,
    @DrawableRes icon: Int,
    enabled: Boolean,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            ShizukuIcon(
                icon,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(18.dp)
            )
            Text(stringResource(label))
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
        ) {
            ShizukuIcon(
                icon,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(18.dp)
            )
            Text(stringResource(label))
        }
    }
}
