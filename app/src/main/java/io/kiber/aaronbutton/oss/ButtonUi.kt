package io.kiber.aaronbutton.oss

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
private fun LanguageMenu(
    language: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(onClick = { expanded = true }) {
            Text(language.shortName)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.language_russian)) },
                onClick = {
                    expanded = false
                    onLanguageSelected(AppLanguage.RUSSIAN)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.language_english)) },
                onClick = {
                    expanded = false
                    onLanguageSelected(AppLanguage.ENGLISH)
                }
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun SetupWizard(
    step: Int,
    scanning: Boolean,
    status: String,
    onScan: () -> Unit,
    language: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit
) {
    val currentStep = step.coerceIn(0, SLOT_COUNT - 1)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setup_title)) },
                actions = {
                    LanguageMenu(language, onLanguageSelected)
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = MdSpacing.medium, vertical = MdSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(MdSpacing.medium)
        ) {
            Text(
                text = stringResource(R.string.setup_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.setup_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(
                    modifier = Modifier.padding(MdSpacing.medium),
                    verticalArrangement = Arrangement.spacedBy(MdSpacing.small)
                ) {
                    Text(
                        text = stringResource(R.string.setup_step, currentStep + 1, SLOT_COUNT),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (scanning) {
                            stringResource(R.string.setup_scanning, currentStep + 1)
                        } else {
                            stringResource(R.string.setup_hold_button, currentStep + 1)
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (status.isNotEmpty()) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !scanning,
                        onClick = onScan
                    ) {
                        Text(
                            if (scanning) {
                                stringResource(R.string.setup_scanning_button)
                            } else {
                                stringResource(R.string.setup_scan_button, currentStep + 1)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MainScreen(
    status: String,
    language: AppLanguage,
    writing: Boolean,
    configuredActions: List<Int>,
    configuredArguments: List<String>,
    configuredTagIds: List<String>,
    highlightedSlot: Int?,
    highlightToken: Long,
    onWrite: (Int, String, Int?, () -> Unit) -> Unit,
    onClear: (Int, () -> Unit) -> Unit,
    onCancelWrite: () -> Unit,
    writeFeedbackToken: Long,
    onLanguageSelected: (AppLanguage) -> Unit
) {
    var selectedActionIndex by rememberSaveable { mutableStateOf(0) }
    var argument by rememberSaveable { mutableStateOf("") }
    var detailsSlot by rememberSaveable { mutableStateOf(-1) }
    var customEditorSlot by rememberSaveable { mutableStateOf(-1) }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aaron Button") },
                actions = {
                    LanguageMenu(language, onLanguageSelected)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { contentPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .widthIn(max = MdSpacing.maxContent)
                    .verticalScroll(scrollState)
                    .imePadding()
                    .padding(horizontal = MdSpacing.medium, vertical = MdSpacing.small),
                verticalArrangement = Arrangement.spacedBy(MdSpacing.small)
            ) {
                StatusCard(status = status, writing = writing)

                Text(
                    text = stringResource(R.string.current_setup),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = MdSpacing.small)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max),
                    horizontalArrangement = Arrangement.spacedBy(MdSpacing.small),
                    verticalAlignment = Alignment.Top
                ) {
                    (0 until SLOT_COUNT).forEach { index ->
                        ButtonCard(
                            modifier = Modifier.weight(1f),
                            index = index,
                            actionIndex = configuredActions[index],
                            argument = configuredArguments[index],
                            tagId = configuredTagIds[index],
                            highlighted = highlightedSlot == index && highlightToken > 0,
                            highlightToken = highlightToken,
                            onClick = {
                                if (ACTIONS.getOrNull(configuredActions[index])?.code == CUSTOM_INTENT_PREFIX) {
                                    detailsSlot = -1
                                    customEditorSlot = index
                                } else {
                                    customEditorSlot = -1
                                    detailsSlot = index
                                }
                            }
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.configure_button),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = MdSpacing.small)
                )

                ConfigureCard(
                    actionIndex = selectedActionIndex,
                    argument = argument,
                    writing = writing,
                    scrollState = scrollState,
                    onActionSelected = {
                        selectedActionIndex = it
                        if (!ACTIONS[it].hasArgument || ACTIONS[it].code == CUSTOM_INTENT_PREFIX) {
                            argument = ""
                        }
                    },
                    onArgumentChanged = { argument = it },
                    onWrite = { onWrite(selectedActionIndex, argument, null, {}) }
                )
            }

            if (detailsSlot in 0 until SLOT_COUNT) {
                val detailsActionIndex = configuredActions[detailsSlot]
                if (detailsActionIndex in ACTIONS.indices && ACTIONS[detailsActionIndex].code != CUSTOM_INTENT_PREFIX) {
                    ActionDetailsSheet(
                        slotIndex = detailsSlot,
                        actionIndex = detailsActionIndex,
                        argument = configuredArguments[detailsSlot],
                        writing = writing,
                        status = status,
                        writeFeedbackToken = writeFeedbackToken,
                        onDismiss = { detailsSlot = -1 },
                        onSave = { editedArgument, onSuccess ->
                            onWrite(detailsActionIndex, editedArgument, detailsSlot, onSuccess)
                        },
                        onClear = { onSuccess ->
                            onClear(detailsSlot, onSuccess)
                        },
                        onCancelWrite = onCancelWrite
                    )
                }
            }

            val editorSlot = customEditorSlot
            if (editorSlot in 0 until SLOT_COUNT) {
                CustomIntentEditorSheet(
                    definition = configuredArguments[editorSlot],
                    writing = writing,
                    slotIndex = editorSlot,
                    status = status,
                    writeFeedbackToken = writeFeedbackToken,
                    onDismiss = { customEditorSlot = -1 },
                    onSave = { value, onSuccess ->
                        onWrite(configuredActions[editorSlot], value, editorSlot, onSuccess ?: {})
                    },
                    onClear = { onSuccess ->
                        onClear(editorSlot, onSuccess)
                    },
                    onCancelWrite = onCancelWrite
                )
            }
        }
    }
}

@Composable
internal fun StatusCard(status: String, writing: Boolean) {
    val containerColor = if (writing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(32.dp),
                shape = CircleShape,
                color = if (writing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (writing) "…" else "✓",
                        color = if (writing) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(if (writing) R.string.writing_status else R.string.nfc_status),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
internal fun ButtonCard(
    modifier: Modifier,
    index: Int,
    actionIndex: Int,
    argument: String,
    tagId: String,
    highlighted: Boolean,
    highlightToken: Long,
    onClick: () -> Unit
) {
    val action = ACTIONS.getOrNull(actionIndex)
    val cornerColor = MaterialTheme.colorScheme.primary
    var highlightVisible by remember { mutableStateOf(false) }
    val cardColor by animateColorAsState(
        targetValue = when {
            highlightVisible -> MaterialTheme.colorScheme.primaryContainer
            action != null -> MaterialTheme.colorScheme.surfaceContainerLow
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(durationMillis = 220),
        label = "button card color"
    )
    val iconBackgroundColor by animateColorAsState(
        targetValue = if (highlightVisible) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(durationMillis = 220),
        label = "button icon background color"
    )
    val iconColor by animateColorAsState(
        targetValue = if (highlightVisible) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(durationMillis = 220),
        label = "button icon color"
    )
    val scale = remember { Animatable(1f) }

    LaunchedEffect(highlightToken) {
        highlightVisible = highlighted
        if (highlighted) {
            scale.snapTo(1f)
            scale.animateTo(
                targetValue = 1.04f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            delay(HIGHLIGHT_DURATION_MS)
            highlightVisible = false
        }
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(MaterialTheme.shapes.large)
            .clickable(
                enabled = action != null,
                role = Role.Button,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = cardColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.button_number, index + 1),
                            style = MaterialTheme.typography.labelLarge
                        )
                        if (tagId.isNotEmpty()) {
                            Text(
                                text = tagId,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (action != null) {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = iconBackgroundColor
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = action.icon,
                                contentDescription = stringResource(action.labelRes),
                                tint = iconColor
                            )
                        }
                    }
                    Text(
                        text = stringResource(action.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Surface(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Nfc,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.button_not_configured),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (action?.hasArgument == true && argument.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(22.dp)
                        .drawBehind {
                            val corner = Path().apply {
                                moveTo(0f, 0f)
                                lineTo(size.width, 0f)
                                lineTo(size.width, size.height)
                                close()
                            }
                            drawPath(corner, cornerColor)
                        }
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun CustomIntentEditorSheet(
    definition: String,
    onDismiss: () -> Unit,
    onSave: (String, (() -> Unit)?) -> Unit,
    onClear: (() -> Unit) -> Unit,
    writing: Boolean = false,
    slotIndex: Int? = null,
    status: String = "",
    onCancelWrite: () -> Unit = {},
    writeFeedbackToken: Long = 0L
) {
    val currentWriting by rememberUpdatedState(writing)
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value -> !currentWriting || value != SheetValue.Hidden }
    )
    var draft by remember(definition) {
        mutableStateOf(CustomIntentDraft.fromDefinition(definition))
    }
    var typeMenuIndex by remember { mutableStateOf(-1) }
    var error by remember { mutableStateOf<String?>(null) }
    var saveCompleted by rememberSaveable { mutableStateOf(false) }
    var clearRequested by rememberSaveable { mutableStateOf(false) }
    val sheetScroll = rememberScrollState()

    LaunchedEffect(saveCompleted) {
        if (saveCompleted) {
            delay(850L)
            onDismiss()
        }
    }

    BackHandler(enabled = writing || saveCompleted) {
        if (saveCompleted) onDismiss() else onCancelWrite()
    }

    ModalBottomSheet(
        onDismissRequest = { if (writing) onCancelWrite() else onDismiss() },
        sheetState = sheetState
    ) {
        when {
            saveCompleted -> SavedButtonContent(slotIndex ?: 0, cleared = clearRequested)
            writing -> {
            SavingButtonContent(
                slotIndex = slotIndex ?: 0,
                status = status,
                feedbackToken = writeFeedbackToken,
                onCancel = onCancelWrite
            )
            }
            else -> {
                Column(
                    modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(sheetScroll)
                .imePadding()
                .padding(horizontal = MdSpacing.medium, vertical = MdSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(MdSpacing.small)
                ) {
            Text(
                text = stringResource(R.string.custom_intent_editor_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.custom_intent_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draft.action,
                onValueChange = { draft = draft.copy(action = it); error = null },
                label = { Text(stringResource(R.string.custom_intent_action)) },
                singleLine = true
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draft.data,
                onValueChange = { draft = draft.copy(data = it); error = null },
                label = { Text(stringResource(R.string.custom_intent_data)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draft.type,
                onValueChange = { draft = draft.copy(type = it); error = null },
                label = { Text(stringResource(R.string.custom_intent_type)) },
                singleLine = true
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draft.packageName,
                onValueChange = { draft = draft.copy(packageName = it); error = null },
                label = { Text(stringResource(R.string.custom_intent_package)) },
                singleLine = true
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draft.component,
                onValueChange = { draft = draft.copy(component = it); error = null },
                label = { Text(stringResource(R.string.custom_intent_component)) },
                singleLine = true
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = draft.flags,
                onValueChange = { draft = draft.copy(flags = it); error = null },
                label = { Text(stringResource(R.string.custom_intent_flags)) },
                singleLine = true
            )

            Text(
                text = stringResource(R.string.custom_intent_categories),
                style = MaterialTheme.typography.titleMedium
            )
            draft.categories.forEachIndexed { index, category ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MdSpacing.small)
                ) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = category,
                        onValueChange = { value ->
                            draft = draft.copy(
                                categories = draft.categories.mapIndexed { itemIndex, item ->
                                    if (itemIndex == index) value else item
                                }
                            )
                            error = null
                        },
                        label = { Text(stringResource(R.string.custom_intent_category)) },
                        singleLine = true
                    )
                    TextButton(
                        onClick = {
                            draft = draft.copy(
                                categories = draft.categories.filterIndexed { itemIndex, _ ->
                                    itemIndex != index
                                }
                            )
                        }
                    ) {
                        Text(stringResource(R.string.custom_intent_remove))
                    }
                }
            }
            TextButton(onClick = { draft = draft.copy(categories = draft.categories + "") }) {
                Text(stringResource(R.string.custom_intent_add_category))
            }

            Text(
                text = stringResource(R.string.custom_intent_extras),
                style = MaterialTheme.typography.titleMedium
            )
            draft.extras.forEachIndexed { index, extra ->
                Column(verticalArrangement = Arrangement.spacedBy(MdSpacing.small)) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = extra.name,
                        onValueChange = { value ->
                            draft = draft.copy(
                                extras = draft.extras.mapIndexed { itemIndex, item ->
                                    if (itemIndex == index) item.copy(name = value) else item
                                }
                            )
                            error = null
                        },
                        label = { Text(stringResource(R.string.custom_intent_extra_key)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = extra.value,
                        onValueChange = { value ->
                            draft = draft.copy(
                                extras = draft.extras.mapIndexed { itemIndex, item ->
                                    if (itemIndex == index) item.copy(value = value) else item
                                }
                            )
                            error = null
                        },
                        label = { Text(stringResource(R.string.custom_intent_extra_value)) },
                        singleLine = true
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MdSpacing.small)
                    ) {
                        Box {
                            OutlinedButton(onClick = { typeMenuIndex = index }) {
                                Text(extra.type)
                            }
                            DropdownMenu(
                                expanded = typeMenuIndex == index,
                                onDismissRequest = { typeMenuIndex = -1 }
                            ) {
                                CUSTOM_INTENT_EXTRA_TYPES.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type) },
                                        onClick = {
                                            draft = draft.copy(
                                                extras = draft.extras.mapIndexed { itemIndex, item ->
                                                    if (itemIndex == index) item.copy(type = type) else item
                                                }
                                            )
                                            typeMenuIndex = -1
                                            error = null
                                        }
                                    )
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.custom_intent_extra_type),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                draft = draft.copy(
                                    extras = draft.extras.filterIndexed { itemIndex, _ ->
                                        itemIndex != index
                                    }
                                )
                            }
                        ) {
                            Text(stringResource(R.string.custom_intent_remove))
                        }
                    }
                }
            }
            TextButton(
                onClick = {
                    draft = draft.copy(
                        extras = draft.extras + CustomIntentExtra("string", "", "")
                    )
                }
            ) {
                Text(stringResource(R.string.custom_intent_add_extra))
            }

            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (slotIndex == null) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val value = draft.toDefinition()
                        runCatching { CustomIntentSpec.buildIntent(value) }
                            .onSuccess { onSave(value, null) }
                            .onFailure {
                                error = localizedErrorMessage(
                                    context,
                                    it,
                                    R.string.invalid_custom_intent
                                )
                            }
                    }
                ) {
                    Text(stringResource(R.string.custom_intent_save))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(MdSpacing.small)
                ) {
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val value = draft.toDefinition()
                            runCatching { CustomIntentSpec.buildIntent(value) }
                                .onSuccess { onSave(value) { saveCompleted = true } }
                                .onFailure {
                                    error = localizedErrorMessage(
                                        context,
                                        it,
                                        R.string.invalid_custom_intent
                                    )
                                }
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onClear {
                                clearRequested = true
                                saveCompleted = true
                            }
                        }
                    ) {
                        Text(stringResource(R.string.disable_button))
                    }
                }
            }
                }
            }
        }
    }

}

@Composable
internal fun OpenAppArgumentField(
    value: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
    onPick: () -> Unit
) {
    val context = LocalContext.current
    val appIcon = remember(context, value) { loadAppIcon(context, value) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MdSpacing.small)
    ) {
        OutlinedTextField(
            modifier = modifier.weight(1f),
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            label = { Text(stringResource(R.string.app_package_hint)) },
            singleLine = true
        )
        IconButton(
            modifier = Modifier.offset(y = 2.dp),
            enabled = enabled,
            onClick = onPick
        ) {
            if (appIcon != null) {
                Image(
                    bitmap = appIcon.asImageBitmap(),
                    contentDescription = stringResource(R.string.choose_app),
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Apps,
                    contentDescription = stringResource(R.string.choose_app)
                )
            }
        }
    }
}

@Composable
internal fun AppPickerContent(
    onAppSelected: (AppChoice) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val apps by produceState<List<AppChoice>?>(null, context) {
        value = withContext(Dispatchers.IO) { loadInstalledApps(context) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    val visibleApps = apps.orEmpty().filter { app ->
        normalizedQuery.isEmpty()
            || app.label.lowercase(Locale.ROOT).contains(normalizedQuery)
            || app.packageName.lowercase(Locale.ROOT).contains(normalizedQuery)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = MdSpacing.medium, vertical = MdSpacing.small),
        verticalArrangement = Arrangement.spacedBy(MdSpacing.small)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            onBack?.let { backAction ->
                IconButton(onClick = backAction) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            }
            Text(
                text = stringResource(R.string.select_app_title),
                style = MaterialTheme.typography.headlineSmall
            )
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.search_apps)) },
            leadingIcon = {
                Icon(imageVector = Icons.Filled.Search, contentDescription = null)
            },
            singleLine = true
        )
        when {
            apps == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            visibleApps.isEmpty() -> {
                Text(
                    text = stringResource(
                        if (normalizedQuery.isEmpty()) {
                            R.string.no_launchable_apps
                        } else {
                            R.string.no_matching_apps
                        }
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> {
                LazyColumn(modifier = Modifier.heightIn(max = 480.dp)) {
                    items(visibleApps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) { onAppSelected(app) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MdSpacing.small)
                        ) {
                            Image(
                                bitmap = app.icon.asImageBitmap(),
                                contentDescription = app.label,
                                modifier = Modifier.size(40.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AppPickerSheet(
    onDismiss: () -> Unit,
    onAppSelected: (AppChoice) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        AppPickerContent(onAppSelected = onAppSelected)
    }
}

@Composable
internal fun SavedButtonContent(slotIndex: Int, cleared: Boolean = false) {
    val checkScale = remember { Animatable(0.6f) }

    LaunchedEffect(Unit) {
        checkScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MdSpacing.medium, vertical = MdSpacing.medium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MdSpacing.small)
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer {
                    scaleX = checkScale.value
                    scaleY = checkScale.value
                }
        )
        Text(
            text = stringResource(
                if (cleared) R.string.clear_success else R.string.write_success,
                slotIndex + 1
            ),
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
internal fun SavingButtonContent(
    slotIndex: Int,
    status: String,
    feedbackToken: Long,
    onCancel: () -> Unit
) {
    val feedbackScale = remember { Animatable(1f) }

    LaunchedEffect(feedbackToken) {
        if (feedbackToken > 0L) {
            feedbackScale.snapTo(1f)
            feedbackScale.animateTo(1.04f, tween(120))
            feedbackScale.animateTo(1f, tween(180))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = MdSpacing.medium, vertical = MdSpacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MdSpacing.small)
    ) {
        CircularProgressIndicator(
            modifier = Modifier
                .size(32.dp)
                .graphicsLayer {
                    scaleX = feedbackScale.value
                    scaleY = feedbackScale.value
                }
        )
        Text(
            text = stringResource(R.string.saving_button, slotIndex + 1),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.graphicsLayer {
                scaleX = feedbackScale.value
                scaleY = feedbackScale.value
            }
        )
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onCancel
        ) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ActionDetailsSheet(
    slotIndex: Int,
    actionIndex: Int,
    argument: String,
    writing: Boolean,
    status: String,
    writeFeedbackToken: Long,
    onDismiss: () -> Unit,
    onSave: (String, () -> Unit) -> Unit,
    onClear: (() -> Unit) -> Unit,
    onCancelWrite: () -> Unit
) {
    val action = ACTIONS[actionIndex]
    var editedArgument by rememberSaveable(actionIndex, argument) { mutableStateOf(argument) }
    var appPickerOpen by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var saveCompleted by rememberSaveable { mutableStateOf(false) }
    var clearRequested by rememberSaveable { mutableStateOf(false) }
    val currentWriting by rememberUpdatedState(writing)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value -> !currentWriting || value != SheetValue.Hidden }
    )
    val editScrollState = rememberScrollState()

    LaunchedEffect(saveCompleted) {
        if (saveCompleted) {
            delay(850L)
            onDismiss()
        }
    }

    BackHandler(enabled = saveCompleted || writing || editing || appPickerOpen) {
        if (saveCompleted) {
            onDismiss()
        } else if (writing) {
            onCancelWrite()
        } else if (appPickerOpen) {
            appPickerOpen = false
        } else {
            editing = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            when {
                saveCompleted -> onDismiss()
                writing -> onCancelWrite()
                else -> onDismiss()
            }
        },
        sheetState = sheetState
    ) {
        when {
            saveCompleted -> SavedButtonContent(slotIndex, cleared = clearRequested)
            writing -> SavingButtonContent(
                slotIndex = slotIndex,
                status = status,
                feedbackToken = writeFeedbackToken,
                onCancel = onCancelWrite
            )
            appPickerOpen -> AppPickerContent(
                onBack = { appPickerOpen = false },
                onAppSelected = { app ->
                    editedArgument = app.packageName
                    appPickerOpen = false
                }
            )
            editing -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(editScrollState)
                        .imePadding()
                        .padding(horizontal = MdSpacing.medium, vertical = MdSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(MdSpacing.small)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { editing = false }) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                        Text(
                            text = stringResource(R.string.edit_action),
                            style = MaterialTheme.typography.headlineSmall
                        )
                    }
                    when {
                        action.code == "open_app_" -> OpenAppArgumentField(
                            value = editedArgument,
                            enabled = true,
                            onValueChange = { editedArgument = it },
                            onPick = { appPickerOpen = true }
                        )
                        action.hasArgument -> OutlinedTextField(
                            modifier = Modifier.fillMaxWidth(),
                            value = editedArgument,
                            onValueChange = { editedArgument = it },
                            label = {
                                Text(stringResource(when (action.code) {
                                    "termux_" -> R.string.termux_argument_hint
                                    "open_link_" -> R.string.link_hint
                                    else -> R.string.argument_hint
                                }))
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = if (action.code == "termux_") {
                                    KeyboardType.Text
                                } else KeyboardType.Uri
                            )
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MdSpacing.small)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !action.hasArgument || editedArgument.isNotBlank(),
                            onClick = {
                                onSave(editedArgument.trim()) { saveCompleted = true }
                            }
                        ) {
                            Text(stringResource(R.string.save))
                        }
                        TextButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onClear {
                                    clearRequested = true
                                    saveCompleted = true
                                }
                            }
                        ) {
                            Text(stringResource(R.string.disable_button))
                        }
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(horizontal = MdSpacing.medium, vertical = MdSpacing.small),
                    verticalArrangement = Arrangement.spacedBy(MdSpacing.small)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MdSpacing.small)
                    ) {
                        Icon(
                            imageVector = action.icon,
                            contentDescription = stringResource(action.labelRes),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.action_details),
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = stringResource(R.string.button_number, slotIndex + 1),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = stringResource(action.labelRes),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = editedArgument,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            modifier = Modifier.weight(1f),
                            onClick = onDismiss
                        ) {
                            Text(stringResource(R.string.close))
                        }
                        if (action.hasArgument) {
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = { editing = true }
                            ) {
                                Text(stringResource(R.string.edit_action))
                            }
                        }
                        TextButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onClear {
                                    clearRequested = true
                                    saveCompleted = true
                                }
                            }
                        ) {
                            Text(stringResource(R.string.disable_button))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ConfigureCard(
    actionIndex: Int,
    argument: String,
    writing: Boolean,
    scrollState: ScrollState,
    onActionSelected: (Int) -> Unit,
    onArgumentChanged: (String) -> Unit,
    onWrite: () -> Unit
) {
    val selectedAction = ACTIONS[actionIndex.coerceIn(0, ACTIONS.lastIndex)]
    val isCustomIntent = selectedAction.code == CUSTOM_INTENT_PREFIX
    val isOpenApp = selectedAction.code == "open_app_"
    var expanded by rememberSaveable { mutableStateOf(false) }
    var customEditorOpen by rememberSaveable { mutableStateOf(false) }
    var appPickerOpen by rememberSaveable { mutableStateOf(false) }
    var argumentFocused by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    LaunchedEffect(argumentFocused, imeBottom, scrollState.maxValue) {
        if (argumentFocused && imeBottom > 0) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(MdSpacing.small)
        ) {
            Text(
                text = stringResource(R.string.configure_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MdSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !writing,
                        onClick = { expanded = true }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MdSpacing.small)
                        ) {
                            Icon(
                                imageVector = selectedAction.icon,
                                contentDescription = stringResource(selectedAction.labelRes)
                            )
                            Text(
                                text = stringResource(selectedAction.labelRes),
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(if (expanded) "▲" else "▼")
                        }
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        ACTIONS.forEachIndexed { optionIndex, option ->
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = option.icon,
                                        contentDescription = null
                                    )
                                },
                                text = { Text(stringResource(option.labelRes)) },
                                onClick = {
                                    onActionSelected(optionIndex)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Button(
                    modifier = Modifier.heightIn(min = 48.dp),
                    enabled = !writing,
                    onClick = onWrite
                ) {
                    Text(stringResource(R.string.write_button_short))
                }
            }

            if (selectedAction.hasArgument) {
                if (isOpenApp) {
                    OpenAppArgumentField(
                        value = argument,
                        enabled = !writing,
                        modifier = Modifier.onFocusChanged { argumentFocused = it.isFocused },
                        onValueChange = onArgumentChanged,
                        onPick = { appPickerOpen = true }
                    )
                } else if (isCustomIntent) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !writing,
                        onClick = { customEditorOpen = true }
                    ) {
                        Text(
                            stringResource(
                                if (argument.isBlank()) {
                                    R.string.custom_intent_set_up
                                } else {
                                    R.string.custom_intent_edit
                                }
                            )
                        )
                    }
                    if (argument.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.custom_intent_configured),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { argumentFocused = it.isFocused },
                        value = argument,
                        onValueChange = onArgumentChanged,
                        enabled = !writing,
                        label = {
                            Text(stringResource(when (selectedAction.code) {
                                "termux_" -> R.string.termux_argument_hint
                                "open_link_" -> R.string.link_hint
                                else -> R.string.argument_hint
                            }))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (selectedAction.code == "termux_") {
                                KeyboardType.Text
                            } else KeyboardType.Uri
                        )
                    )
                }
            }
        }
    }

    if (appPickerOpen) {
        AppPickerSheet(
            onDismiss = { appPickerOpen = false },
            onAppSelected = { app ->
                onArgumentChanged(app.packageName)
                appPickerOpen = false
            }
        )
    }

    if (customEditorOpen) {
        CustomIntentEditorSheet(
            definition = argument,
            onDismiss = { customEditorOpen = false },
            onSave = { value, _ ->
                onArgumentChanged(value)
                customEditorOpen = false
            },
            onClear = {}
        )
    }
}
