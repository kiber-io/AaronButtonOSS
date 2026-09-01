package io.kiber.aaronbutton.oss

import android.Manifest
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val SLOT_COUNT = 3
private const val HIGHLIGHT_DURATION_MS = 1500L
private const val CUSTOM_INTENT_PREFIX = "custom_intent_"
private val CUSTOM_INTENT_EXTRA_TYPES = listOf("string", "int", "long", "boolean", "float", "double")
private val CUSTOM_INTENT_FIELDS = setOf("action", "data", "type", "package", "component", "flags")

private data class ActionOption(
    val label: String,
    val code: String,
    val hasArgument: Boolean,
    val icon: ImageVector
)

private data class AppChoice(
    val label: String,
    val packageName: String,
    val icon: Bitmap
)

private fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable) return bitmap
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
        setBounds(0, 0, bitmap.width, bitmap.height)
        draw(Canvas(bitmap))
    }
}

private fun loadAppIcon(context: Context, packageName: String): Bitmap? {
    if (packageName.isBlank()) return null
    return runCatching {
        context.packageManager.getApplicationIcon(packageName).toBitmap()
    }.getOrNull()
}

private fun loadInstalledApps(context: Context): List<AppChoice> {
    val packageManager = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(launcherIntent, 0)
        .mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            if (packageName == context.packageName) return@mapNotNull null
            AppChoice(
                label = info.loadLabel(packageManager)?.toString().orEmpty().ifBlank { packageName },
                packageName = packageName,
                icon = info.loadIcon(packageManager).toBitmap()
            )
        }
        .distinctBy(AppChoice::packageName)
        .sortedBy { it.label.lowercase(Locale.ROOT) }
}

private val ACTIONS = listOf(
    ActionOption("Flashlight", "flash_light", false, Icons.Filled.FlashOn),
    ActionOption("Camera", "system_camera", false, Icons.Filled.CameraAlt),
    ActionOption("Open app", "open_app_", true, Icons.Filled.Apps),
    ActionOption("Open link", "open_link_", true, Icons.Filled.Link),
    ActionOption("Run Termux command", "termux_", true, Icons.Filled.Code),
    ActionOption("Custom intent", CUSTOM_INTENT_PREFIX, true, Icons.Filled.Code),
    ActionOption("Sound: silent / ring", "sound", false, Icons.Filled.VolumeUp),
    ActionOption("NFC settings", "nfc_settings", false, Icons.Filled.Settings),
    ActionOption("Location settings", "location", false, Icons.Filled.LocationOn),
    ActionOption("Airplane mode settings", "airplane", false, Icons.Filled.AirplanemodeActive)
)

private data class CustomIntentExtra(
    val type: String,
    val name: String,
    val value: String
)

private data class ParsedCustomIntent(
    val fields: Map<String, String>,
    val categories: List<String>,
    val extras: List<CustomIntentExtra>
)

private object CustomIntentSpec {
    fun toPayloadAction(definition: String): String {
        val normalized = definition.trim()
        buildIntent(normalized)
        return CUSTOM_INTENT_PREFIX + Base64.encodeToString(
            normalized.toByteArray(StandardCharsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    fun parse(definition: String): ParsedCustomIntent {
        val fields = linkedMapOf<String, String>()
        val categories = mutableListOf<String>()
        val extras = mutableListOf<CustomIntentExtra>()

        definition.lines().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachIndexed
            val separator = line.indexOf('=')
            if (separator <= 0) {
                throw IllegalArgumentException("Invalid custom intent line ${index + 1}")
            }
            val rawKey = line.substring(0, separator).trim()
            val key = rawKey.lowercase(Locale.ROOT)
            val value = line.substring(separator + 1).trim()
            if (key.startsWith("extra.")) {
                val extraSpec = rawKey.substringAfter('.')
                val typeAndName = extraSpec.split('.', limit = 2)
                val candidateType = typeAndName[0].lowercase(Locale.ROOT)
                val typed = typeAndName.size == 2 && candidateType in CUSTOM_INTENT_EXTRA_TYPES
                val type = if (typed) candidateType else "string"
                val name = if (typed) typeAndName[1] else extraSpec
                if (name.isBlank()) {
                    throw IllegalArgumentException("Custom intent extra name is empty")
                }
                extras += CustomIntentExtra(type, name, value)
            } else if (key == "category") {
                if (value.isEmpty()) throw IllegalArgumentException("Intent category is empty")
                categories += value
            } else if (key in CUSTOM_INTENT_FIELDS) {
                if (key in fields) throw IllegalArgumentException("Duplicate custom intent field: $key")
                fields[key] = value
            } else {
                throw IllegalArgumentException("Unknown custom intent field: $rawKey")
            }
        }

        return ParsedCustomIntent(fields, categories, extras)
    }

    fun buildIntent(definition: String): Intent {
        val parsed = parse(definition)

        val action = parsed.fields["action"].orEmpty()
        if (action.isEmpty()) throw IllegalArgumentException("Custom intent requires action=...")
        val intent = Intent(action)
        parsed.fields["data"]?.takeIf { it.isNotEmpty() }?.let { data ->
            val type = parsed.fields["type"]
            if (type.isNullOrEmpty()) intent.data = Uri.parse(data)
            else intent.setDataAndType(Uri.parse(data), type)
        } ?: parsed.fields["type"]?.takeIf { it.isNotEmpty() }?.let(intent::setType)
        parsed.fields["package"]?.takeIf { it.isNotEmpty() }?.let(intent::setPackage)
        parsed.fields["component"]?.takeIf { it.isNotEmpty() }?.let { component ->
            intent.component = ComponentName.unflattenFromString(component)
                ?: throw IllegalArgumentException("Invalid intent component: $component")
        }
        parsed.fields["flags"]?.takeIf { it.isNotEmpty() }?.let { flags ->
            intent.addFlags(parseFlags(flags))
        }
        parsed.categories.forEach(intent::addCategory)
        parsed.extras.forEach { extra ->
            when (extra.type) {
                "string" -> intent.putExtra(extra.name, extra.value)
                "int" -> intent.putExtra(extra.name, extra.value.toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid int extra: ${extra.name}"))
                "long" -> intent.putExtra(extra.name, extra.value.toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid long extra: ${extra.name}"))
                "boolean" -> intent.putExtra(extra.name, parseBoolean(extra.value, extra.name))
                "float" -> intent.putExtra(extra.name, extra.value.toFloatOrNull()
                    ?: throw IllegalArgumentException("Invalid float extra: ${extra.name}"))
                "double" -> intent.putExtra(extra.name, extra.value.toDoubleOrNull()
                    ?: throw IllegalArgumentException("Invalid double extra: ${extra.name}"))
            }
        }
        return intent
    }

    private fun parseBoolean(value: String, name: String): Boolean {
        return when (value.lowercase(Locale.ROOT)) {
            "true" -> true
            "false" -> false
            else -> throw IllegalArgumentException("Invalid boolean extra: $name")
        }
    }

    private fun parseFlags(value: String): Int {
        val parsed = if (value.startsWith("0x", ignoreCase = true)) {
            value.substring(2).toLongOrNull(16)?.takeIf { it <= 0xFFFFFFFFL }?.toInt()
        } else {
            value.toLongOrNull()?.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
        }
        return parsed ?: throw IllegalArgumentException("Invalid intent flags")
    }
}

private data class CustomIntentDraft(
    val action: String = "",
    val data: String = "",
    val type: String = "",
    val packageName: String = "",
    val component: String = "",
    val flags: String = "",
    val categories: List<String> = emptyList(),
    val extras: List<CustomIntentExtra> = emptyList()
) {
    companion object {
        fun fromDefinition(definition: String): CustomIntentDraft {
            val parsed = runCatching { CustomIntentSpec.parse(definition) }.getOrNull()
                ?: return CustomIntentDraft()
            return CustomIntentDraft(
                action = parsed.fields["action"].orEmpty(),
                data = parsed.fields["data"].orEmpty(),
                type = parsed.fields["type"].orEmpty(),
                packageName = parsed.fields["package"].orEmpty(),
                component = parsed.fields["component"].orEmpty(),
                flags = parsed.fields["flags"].orEmpty(),
                categories = parsed.categories,
                extras = parsed.extras
            )
        }
    }

    fun toDefinition(): String = buildString {
        append("action=").append(action.trim()).append('\n')
        if (data.isNotBlank()) append("data=").append(data.trim()).append('\n')
        if (type.isNotBlank()) append("type=").append(type.trim()).append('\n')
        if (packageName.isNotBlank()) append("package=").append(packageName.trim()).append('\n')
        if (component.isNotBlank()) append("component=").append(component.trim()).append('\n')
        if (flags.isNotBlank()) append("flags=").append(flags.trim()).append('\n')
        categories.map(String::trim).filter(String::isNotEmpty).forEach {
            append("category=").append(it).append('\n')
        }
        extras.filter { it.name.isNotBlank() }.forEach {
            val prefix = if (it.type == "string") "extra." else "extra.${it.type}."
            append(prefix).append(it.name.trim()).append('=').append(it.value.trim()).append('\n')
        }
    }.trim()
}

private object MdSpacing {
    val small = 8.dp
    val medium = 16.dp
    val maxContent = 840.dp
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF415F91),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF284777)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9C7FF),
    onPrimary = Color(0xFF0D305F),
    primaryContainer = Color(0xFF284777),
    onPrimaryContainer = Color(0xFFD6E3FF)
)

@Composable
private fun AaronButtonTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

open class MainActivity : ComponentActivity() {

    protected open val isNfcTrigger = false

    companion object {
        private const val TAG = "AaronButtonOSS"
        private const val MIME_TYPE = "application/com.pitapolis.nfc"
        private const val CAMERA_REQUEST = 41
        private const val TERMUX_REQUEST = 42
        private const val TERMUX_PACKAGE = "com.termux"
        private const val TERMUX_PERMISSION = "com.termux.permission.RUN_COMMAND"
        private const val PREFS = "button_config"
        private const val BUTTON_SETUP_COMPLETE = "button_setup_complete"
        private const val SETUP_BLOCK_UNTIL = "setup_block_until"
        private const val SETUP_BLOCK_MS = 5000L
        private const val IGNORE_PAYLOAD = "ignore_payload"
        private const val IGNORE_PAYLOAD_UNTIL = "ignore_payload_until"
        private const val IGNORE_PAYLOAD_WINDOW_MS = 5000L
        private const val WRITE_IN_PROGRESS = "write_in_progress"
        private const val WRITE_BLOCK_UNTIL = "write_block_until"
        private const val WRITE_MAX_WINDOW_MS = 30000L
        private const val WRITE_AFTER_WINDOW_MS = 1000L
        private const val LAST_SLOT = "last_slot"
        private const val LAST_EVENT = "last_event"
    }

    private lateinit var preferences: SharedPreferences
    private var nfcAdapter: NfcAdapter? = null
    private var androidId = ""
    private var pendingPayload: String? = null
    private var pendingCameraAction: String? = null
    private var pendingTermuxCommand: String? = null
    private var pendingActionIndex = 0
    private var pendingTargetSlot: Int? = null
    private var pendingArgument = ""
    private var pendingWriteSuccess: () -> Unit = {}
    private val mainHandler = Handler(Looper.getMainLooper())
    private var readerModeActive = false
    private var readerModeCooldownUntil = 0L
    private var buttonSetupComplete by mutableStateOf(false)
    private var learning by mutableStateOf(false)
    private var learningStep by mutableStateOf(0)
    private val learnedTagIds = mutableStateOf(List(SLOT_COUNT) { "" })
    private val learningStatus = mutableStateOf("")
    private var writing by mutableStateOf(false)
    private val status = mutableStateOf("")
    private val configuredActions = mutableStateOf(List(SLOT_COUNT) { -1 })
    private val configuredArguments = mutableStateOf(List(SLOT_COUNT) { "" })
    private val configuredTagIds = mutableStateOf(List(SLOT_COUNT) { "" })
    private val highlightedSlot = mutableStateOf<Int?>(null)
    private val highlightToken = mutableStateOf(0L)
    private val writeFeedbackToken = mutableStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE)
        val allTagIdsKnown = (0 until SLOT_COUNT).all {
            !preferences.getString("tag_id_$it", "").isNullOrEmpty()
        }
        buttonSetupComplete = preferences.getBoolean(BUTTON_SETUP_COMPLETE, allTagIdsKnown) && allTagIdsKnown
        androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        status.value = getString(if (nfcAdapter == null) R.string.nfc_unavailable else R.string.ready)

        if (!isNfcTrigger) {
            preferences.edit()
                .remove(LAST_SLOT)
                .remove(LAST_EVENT)
                .apply()
        }
        loadConfiguredState()
        if (!isNfcTrigger) {
            setContent {
                AaronButtonTheme {
                    if (buttonSetupComplete) {
                        MainScreen(
                            status = status.value,
                            writing = writing,
                            configuredActions = configuredActions.value,
                            configuredArguments = configuredArguments.value,
                            configuredTagIds = configuredTagIds.value,
                            highlightedSlot = highlightedSlot.value,
                            highlightToken = highlightToken.value,
                            onWrite = { actionIndex, argument, targetSlot, onSuccess ->
                                beginWrite(actionIndex, argument, targetSlot, onSuccess)
                            },
                            onCancelWrite = ::cancelWrite,
                            writeFeedbackToken = writeFeedbackToken.value
                        )
                    } else {
                        SetupWizard(
                            step = learningStep,
                            scanning = learning,
                            status = learningStatus.value,
                            onScan = ::startLearning
                        )
                    }
                }
            }
        }
        if (buttonSetupComplete) handleNfcIntent(intent)
        if (isNfcTrigger && pendingCameraAction == null && pendingTermuxCommand == null) finish()
    }

    override fun onResume() {
        super.onResume()
        loadConfiguredState()
        if ((writing || learning) && nfcAdapter != null) enableReaderMode()
    }

    private fun loadConfiguredState() {
        if (!::preferences.isInitialized) return
        configuredActions.value = List(SLOT_COUNT) { preferences.getInt("action_$it", -1) }
        configuredArguments.value = List(SLOT_COUNT) { preferences.getString("argument_$it", "").orEmpty() }
        configuredTagIds.value = List(SLOT_COUNT) { preferences.getString("tag_id_$it", "").orEmpty() }
        val slot = preferences.getInt(LAST_SLOT, -1)
        highlightedSlot.value = slot.takeIf { it in 0 until SLOT_COUNT }
        highlightToken.value = preferences.getLong(LAST_EVENT, 0L)
    }

    override fun onPause() {
        stopReaderMode()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (buttonSetupComplete) handleNfcIntent(intent)
    }

    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == TERMUX_REQUEST) {
            val command = pendingTermuxCommand
            pendingTermuxCommand = null
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && command != null) {
                sendTermuxCommand(command)
            } else {
                toast(getString(R.string.termux_permission_required))
            }
            if (isNfcTrigger) finish()
            return
        }
        if (requestCode != CAMERA_REQUEST || pendingCameraAction == null) return
        val action = pendingCameraAction
        pendingCameraAction = null
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && action != null) {
            executeAction(action)
        } else {
            toast(getString(R.string.camera_permission_required))
        }
        if (isNfcTrigger) finish()
    }

    private fun beginWrite(
        actionIndex: Int,
        rawArgument: String,
        targetSlot: Int? = null,
        onSuccess: () -> Unit = {}
    ) {
        pendingWriteSuccess = {}
        writeFeedbackToken.value = 0L
        val adapter = nfcAdapter
        if (adapter == null) {
            toast(getString(R.string.nfc_unavailable))
            return
        }
        if (!adapter.isEnabled) {
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            return
        }

        pendingActionIndex = actionIndex.coerceIn(0, ACTIONS.lastIndex)
        val action = ACTIONS[pendingActionIndex]
        val argument = if (action.hasArgument) rawArgument.trim() else ""
        pendingTargetSlot = targetSlot?.takeIf { it in 0 until SLOT_COUNT }
        if (action.hasArgument && argument.isEmpty()) {
            toast(getString(
                when (action.code) {
                    "termux_" -> R.string.termux_argument_required
                    CUSTOM_INTENT_PREFIX -> R.string.custom_intent_required
                    else -> R.string.argument_required
                }
            ))
            return
        }

        val actionValue = try {
            if (action.code == CUSTOM_INTENT_PREFIX) {
                CustomIntentSpec.toPayloadAction(argument)
            } else {
                action.code + if (action.hasArgument) argument else ""
            }
        } catch (e: IllegalArgumentException) {
            toast(e.message.orEmpty())
            return
        }
        pendingArgument = argument
        pendingPayload = try {
            NfcPayload.encode(androidId, actionValue)
        } catch (e: IllegalArgumentException) {
            toast(e.message.orEmpty())
            return
        }
        pendingWriteSuccess = onSuccess
        writing = true
        readerModeCooldownUntil = 0L
        val blockUntil = System.currentTimeMillis() + WRITE_MAX_WINDOW_MS
        preferences.edit()
            .putBoolean(WRITE_IN_PROGRESS, true)
            .putLong(WRITE_BLOCK_UNTIL, blockUntil)
            .apply()
        status.value = pendingTargetSlot?.let {
            getString(R.string.touch_target_button, it + 1)
        } ?: getString(R.string.touch_tag)
        enableReaderMode()
    }

    private fun cancelWrite() {
        if (!writing) return
        writing = false
        pendingPayload = null
        pendingTargetSlot = null
        pendingArgument = ""
        pendingWriteSuccess = {}
        writeFeedbackToken.value = 0L
        preferences.edit()
            .putBoolean(WRITE_IN_PROGRESS, false)
            .remove(WRITE_BLOCK_UNTIL)
            .apply()
        stopReaderMode()
        status.value = getString(R.string.ready)
    }

    private fun enableReaderMode() {
        val adapter = nfcAdapter
        if (adapter == null || (!writing && !learning)) {
            return
        }
        adapter.enableReaderMode(
            this,
            { tag -> if (learning) learnTag(tag) else writeTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A
                or NfcAdapter.FLAG_READER_NFC_B
                or NfcAdapter.FLAG_READER_NFC_F
                or NfcAdapter.FLAG_READER_NFC_V
                or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null
        )
        readerModeActive = true
    }

    private fun startLearning() {
        if (buttonSetupComplete || learning || learningStep !in 0 until SLOT_COUNT) return
        val adapter = nfcAdapter
        if (adapter == null) {
            learningStatus.value = getString(R.string.nfc_unavailable)
            return
        }
        if (!adapter.isEnabled) {
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
            return
        }
        learning = true
        learningStatus.value = getString(R.string.setup_scanning, learningStep + 1)
        enableReaderMode()
    }

    private fun learnTag(tag: Tag) {
        val id = tagId(tag)
        runOnUiThread {
            if (!learning) {
                return@runOnUiThread
            }
            if (id.isEmpty()) {
                learning = false
                stopReaderMode()
                learningStatus.value = getString(R.string.nfc_tag_id_unavailable)
                return@runOnUiThread
            }
            val duplicateStep = learnedTagIds.value.indexOf(id)
            if (duplicateStep >= 0) {
                learning = false
                stopReaderMode()
                learningStatus.value = getString(R.string.setup_duplicate, duplicateStep + 1)
                return@runOnUiThread
            }

            val learned = learnedTagIds.value.toMutableList().also {
                it[learningStep] = id
            }
            val completedButton = learningStep + 1
            learning = false
            if (learningStep == SLOT_COUNT - 1) {
                val blockUntil = System.currentTimeMillis() + SETUP_BLOCK_MS
                val editor = preferences.edit()
                learned.forEachIndexed { index, tagId ->
                    editor.putString("tag_id_$index", tagId)
                }
                editor.putBoolean(BUTTON_SETUP_COMPLETE, true)
                    .putLong(SETUP_BLOCK_UNTIL, blockUntil)
                if (!editor.commit()) {
                    stopReaderMode()
                    learningStatus.value = getString(R.string.setup_save_failed)
                    Log.e(TAG, "button setup could not be persisted")
                    return@runOnUiThread
                }
                stopReaderMode()
                learnedTagIds.value = learned
                configuredTagIds.value = learned
                buttonSetupComplete = true
                learningStep = SLOT_COUNT
                learningStatus.value = getString(R.string.setup_complete)
            } else {
                stopReaderMode()
                learnedTagIds.value = learned
                learningStep++
                learningStatus.value = getString(R.string.setup_saved, completedButton, learningStep + 1)
            }
        }
    }

    private fun stopReaderMode() {
        readerModeCooldownUntil = 0L
        if (nfcAdapter != null && readerModeActive) {
            nfcAdapter?.disableReaderMode(this)
            readerModeActive = false
        }
    }

    private fun writeTag(tag: Tag) {
        val payload = pendingPayload
        if (!writing || payload == null) {
            return
        }

        var ndef: Ndef? = null
        var formatable: NdefFormatable? = null
        try {
            val id = tagId(tag)
            if (id.isEmpty()) throw IOException(getString(R.string.nfc_tag_id_unavailable))
            val slotIndex = findSlotForConfiguration(id)
            val targetSlot = pendingTargetSlot
            if (targetSlot != null && slotIndex != targetSlot) {
                runOnUiThread {
                    status.value = getString(R.string.wrong_button_for_save, targetSlot + 1)
                    writeFeedbackToken.value++
                }
                return
            }
            if (slotIndex < 0) throw IOException(getString(R.string.no_button_slot))
            val actionIndex = pendingActionIndex
            val argument = pendingArgument
            val message = NdefMessage(
                arrayOf(NdefRecord.createMime(MIME_TYPE, payload.toByteArray(StandardCharsets.UTF_8)))
            )
            val currentNdef = Ndef.get(tag)
            ndef = currentNdef
            if (currentNdef != null) {
                currentNdef.connect()
                if (!currentNdef.isWritable) throw IOException("NFC tag is read-only")
                if (currentNdef.maxSize < message.toByteArray().size) {
                    throw IOException(getString(R.string.nfc_tag_too_small))
                }
                currentNdef.writeNdefMessage(message)
            } else {
                formatable = NdefFormatable.get(tag)
                if (formatable == null) throw IOException(getString(R.string.ndef_not_supported))
                formatable.connect()
                formatable.format(message)
            }
            val eventToken = System.currentTimeMillis()
            val cooldownUntil = eventToken + WRITE_AFTER_WINDOW_MS
            preferences.edit()
                .putString("tag_id_$slotIndex", id)
                .putInt("action_$slotIndex", actionIndex)
                .putString("argument_$slotIndex", argument)
                .putInt(LAST_SLOT, slotIndex)
                .putLong(LAST_EVENT, eventToken)
                .putBoolean(WRITE_IN_PROGRESS, false)
                .putLong(WRITE_BLOCK_UNTIL, cooldownUntil)
                .putString(IGNORE_PAYLOAD, payload)
                .putLong(IGNORE_PAYLOAD_UNTIL, System.currentTimeMillis() + IGNORE_PAYLOAD_WINDOW_MS)
                .apply()
            runOnUiThread {
                val completion = pendingWriteSuccess
                pendingWriteSuccess = {}
                configuredActions.value = configuredActions.value.toMutableList().also {
                    it[slotIndex] = actionIndex
                }
                configuredArguments.value = configuredArguments.value.toMutableList().also {
                    it[slotIndex] = argument
                }
                configuredTagIds.value = configuredTagIds.value.toMutableList().also {
                    it[slotIndex] = id
                }
                highlightedSlot.value = slotIndex
                highlightToken.value = eventToken
                readerModeCooldownUntil = cooldownUntil
                writing = false
                pendingTargetSlot = null
                mainHandler.postDelayed({
                    if (!writing && readerModeCooldownUntil == cooldownUntil) {
                        stopReaderMode()
                    }
                }, WRITE_AFTER_WINDOW_MS)
                status.value = getString(R.string.write_success, slotIndex + 1)
                completion()
            }
        } catch (e: Exception) {
            Log.e(TAG, "writeTag failed", e)
            preferences.edit()
                .putBoolean(WRITE_IN_PROGRESS, false)
                .remove(WRITE_BLOCK_UNTIL)
                .apply()
            runOnUiThread {
                stopReaderMode()
                writing = false
                pendingTargetSlot = null
                pendingWriteSuccess = {}
                writeFeedbackToken.value = 0L
                status.value = getString(R.string.write_failed, e.message)
                toast(getString(R.string.write_failed, e.message))
            }
        } finally {
            try {
                ndef?.close()
            } catch (_: IOException) {
            }
            try {
                formatable?.close()
            } catch (_: IOException) {
            }
        }
    }

    private fun findConfiguredSlot(tagId: String): Int? {
        return (0 until SLOT_COUNT).firstOrNull {
            preferences.getString("tag_id_$it", "") == tagId
        }
    }

    private fun findSlotForConfiguration(tagId: String): Int {
        val existingSlot = findConfiguredSlot(tagId)
        if (existingSlot != null) {
            return existingSlot
        }
        val emptySlot = (0 until SLOT_COUNT).firstOrNull {
            preferences.getString("tag_id_$it", "").isNullOrEmpty()
        }
        return emptySlot ?: -1
    }

    private fun tagId(tag: Tag): String {
        return tag.id.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    }

    private fun isWriteBlocked(): Boolean {
        val now = System.currentTimeMillis()
        val inProgress = preferences.getBoolean(WRITE_IN_PROGRESS, false)
        val blockedUntil = preferences.getLong(WRITE_BLOCK_UNTIL, 0L)
        if (inProgress && blockedUntil <= now) {
            preferences.edit()
                .putBoolean(WRITE_IN_PROGRESS, false)
                .remove(WRITE_BLOCK_UNTIL)
                .apply()
            return false
        }
        val blocked = inProgress || blockedUntil > now
        return blocked
    }

    private fun handleNfcIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        if (action != NfcAdapter.ACTION_NDEF_DISCOVERED
            && action != NfcAdapter.ACTION_TAG_DISCOVERED
            && action != NfcAdapter.ACTION_TECH_DISCOVERED
        ) return
        val detectedTagId = (intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) as? Tag)?.let(::tagId)
        if (writing || isWriteBlocked()) return
        val setupBlockUntil = preferences.getLong(SETUP_BLOCK_UNTIL, 0L)
        if (setupBlockUntil > System.currentTimeMillis()) return
        val slotIndex = detectedTagId?.let(::findConfiguredSlot)
        if (slotIndex == null) return
        val configuredActionIndex = preferences.getInt("action_$slotIndex", -1)
        val configuredOption = ACTIONS.getOrNull(configuredActionIndex)
        if (configuredOption == null) return
        val configuredArgument = preferences.getString("argument_$slotIndex", "").orEmpty()
        val configuredAction = configuredOption.code +
            if (configuredOption.hasArgument) configuredArgument else ""
        val rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        if (rawMessages == null) return
        for (rawMessage in rawMessages) {
            if (rawMessage !is NdefMessage) continue
            for (record in rawMessage.records) {
                val payload = String(record.payload, StandardCharsets.UTF_8)
                if (isJustWrittenPayload(payload)) {
                    return
                }
                val payloadAction = NfcPayload.actionFor(payload, androidId)
                if (payloadAction != null) {
                    rememberDetectedEvent(slotIndex)
                    executeAction(configuredAction)
                    return
                }
            }
        }
    }

    private fun rememberDetectedEvent(slotIndex: Int) {
        val eventToken = System.currentTimeMillis()
        preferences.edit()
            .putInt(LAST_SLOT, slotIndex)
            .putLong(LAST_EVENT, eventToken)
            .apply()
        highlightedSlot.value = slotIndex
        highlightToken.value = eventToken
    }

    private fun isJustWrittenPayload(payload: String): Boolean {
        val ignored = preferences.getString(IGNORE_PAYLOAD, null) ?: return false
        val until = preferences.getLong(IGNORE_PAYLOAD_UNTIL, 0L)
        if (until < System.currentTimeMillis()) {
            preferences.edit().remove(IGNORE_PAYLOAD).remove(IGNORE_PAYLOAD_UNTIL).apply()
            return false
        }
        if (ignored != payload.trim()) return false
        preferences.edit().remove(IGNORE_PAYLOAD).remove(IGNORE_PAYLOAD_UNTIL).apply()
        return true
    }

    private fun executeAction(action: String) {
        if (writing || isWriteBlocked()) return
        val raw = action.trim()
        val normalized = raw.lowercase(Locale.ROOT)
        val logAction = when {
            normalized.startsWith("termux_") -> "termux_command"
            normalized.startsWith(CUSTOM_INTENT_PREFIX) -> "custom_intent"
            else -> raw
        }
        try {
            when {
                normalized == "flash_light" -> runWithCameraPermission(raw)
                normalized == "system_camera" -> startActivity(Intent("android.media.action.STILL_IMAGE_CAMERA"))
                normalized.startsWith("open_app_") -> openApp(raw.removePrefix("open_app_").trim())
                normalized.startsWith("open_link_") -> openLink(raw.removePrefix("open_link_").trim())
                normalized.startsWith("termux_") -> runTermuxCommand(raw.substring("termux_".length).trim())
                normalized.startsWith(CUSTOM_INTENT_PREFIX) -> runCustomIntent(raw.removePrefix(CUSTOM_INTENT_PREFIX).trim())
                normalized == "sound" -> toggleSound()
                normalized == "nfc_settings" -> startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                normalized == "location" -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                normalized == "airplane" -> startActivity(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
                else -> toast(getString(R.string.unknown_action, raw))
            }
        } catch (e: Exception) {
            Log.e(TAG, "executeAction failed activity=${javaClass.simpleName} action=$logAction", e)
            toast(getString(R.string.action_failed, e.message))
        }
    }

    private fun runWithCameraPermission(action: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingCameraAction = action
            requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA_REQUEST)
            return
        }
        try {
            toggleTorch()
        } catch (e: Exception) {
            toast(getString(R.string.action_failed, e.message))
        }
    }

    @Throws(Exception::class)
    private fun toggleTorch() {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        var cameraId: String? = null
        for (id in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (hasFlash == true && (facing == null || facing == CameraCharacteristics.LENS_FACING_BACK)) {
                cameraId = id
                break
            }
        }
        if (cameraId == null) throw IOException(getString(R.string.flash_not_available))
        val isOn = preferences.getBoolean("torch_on", false)
        cameraManager.setTorchMode(cameraId, !isOn)
        preferences.edit().putBoolean("torch_on", !isOn).apply()
    }

    private fun openApp(packageName: String) {
        if (packageName.isEmpty()) throw IllegalArgumentException(getString(R.string.app_package_required))
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: throw IllegalArgumentException(getString(R.string.app_not_installed, packageName))
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launch)
    }

    private fun openLink(link: String) {
        if (link.isEmpty()) throw IllegalArgumentException(getString(R.string.link_required))
        val view = Intent(Intent.ACTION_VIEW, Uri.parse(link)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(view)
        } catch (_: ActivityNotFoundException) {
            throw IllegalArgumentException(getString(R.string.no_link_handler))
        }
    }

    private fun runCustomIntent(definition: String) {
        val customIntent = CustomIntentSpec.buildIntent(definition).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(customIntent)
        } catch (_: ActivityNotFoundException) {
            throw IllegalArgumentException(getString(R.string.custom_intent_no_handler))
        }
    }

    private fun runTermuxCommand(command: String) {
        if (command.isEmpty()) throw IllegalArgumentException(getString(R.string.termux_argument_required))
        if (!isTermuxInstalled()) {
            toast(getString(R.string.termux_not_installed))
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
            && checkSelfPermission(TERMUX_PERMISSION) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingTermuxCommand = command
            requestPermissions(arrayOf(TERMUX_PERMISSION), TERMUX_REQUEST)
            return
        }
        sendTermuxCommand(command)
    }

    private fun isTermuxInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun sendTermuxCommand(command: String) {
        val intent = Intent("com.termux.RUN_COMMAND").apply {
            setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", command))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        try {
            startService(intent)
            toast(getString(R.string.termux_sent))
        } catch (e: Exception) {
            Log.e(TAG, "Termux command failed", e)
            toast(getString(R.string.termux_failed, e.message ?: e.javaClass.simpleName))
        }
    }

    private fun toggleSound() {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val notifications = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !notifications.isNotificationPolicyAccessGranted) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }
        audio.ringerMode = if (audio.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            AudioManager.RINGER_MODE_SILENT
        } else {
            AudioManager.RINGER_MODE_NORMAL
        }
    }

    private fun toast(message: String) {
        if (isNfcTrigger) return
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SetupWizard(
    step: Int,
    scanning: Boolean,
    status: String,
    onScan: () -> Unit
) {
    val currentStep = step.coerceIn(0, SLOT_COUNT - 1)
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.setup_title)) })
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
private fun MainScreen(
    status: String,
    writing: Boolean,
    configuredActions: List<Int>,
    configuredArguments: List<String>,
    configuredTagIds: List<String>,
    highlightedSlot: Int?,
    highlightToken: Long,
    onWrite: (Int, String, Int?, () -> Unit) -> Unit,
    onCancelWrite: () -> Unit,
    writeFeedbackToken: Long
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
                val sheetOpen = detailsSlot in 0 until SLOT_COUNT || customEditorSlot in 0 until SLOT_COUNT
                if (!sheetOpen) {
                    StatusCard(status = status, writing = writing)
                }

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
                    onCancelWrite = onCancelWrite
                )
            }
        }
    }
}

@Composable
private fun StatusCard(status: String, writing: Boolean) {
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
private fun ButtonCard(
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
                enabled = action?.hasArgument == true && argument.isNotEmpty(),
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
                                contentDescription = action.label,
                                tint = iconColor
                            )
                        }
                    }
                    Text(
                        text = action.label,
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
private fun CustomIntentEditorSheet(
    definition: String,
    onDismiss: () -> Unit,
    onSave: (String, (() -> Unit)?) -> Unit,
    writing: Boolean = false,
    slotIndex: Int? = null,
    status: String = "",
    onCancelWrite: () -> Unit = {},
    writeFeedbackToken: Long = 0L
) {
    val currentWriting by rememberUpdatedState(writing)
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
            saveCompleted -> SavedButtonContent(slotIndex ?: 0)
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
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val value = draft.toDefinition()
                    runCatching { CustomIntentSpec.buildIntent(value) }
                        .onSuccess {
                            val completion: (() -> Unit)? = if (slotIndex != null) {
                                { saveCompleted = true }
                            } else null
                            onSave(value, completion)
                        }
                        .onFailure { error = it.message ?: "Invalid custom intent" }
                }
            ) {
                Text(stringResource(if (slotIndex == null) R.string.custom_intent_save else R.string.save))
            }
                }
            }
        }
    }
}

@Composable
private fun OpenAppArgumentField(
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
private fun AppPickerContent(
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
private fun AppPickerSheet(
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
private fun SavedButtonContent(slotIndex: Int) {
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
            text = stringResource(R.string.write_success, slotIndex + 1),
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
private fun SavingButtonContent(
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
private fun ActionDetailsSheet(
    slotIndex: Int,
    actionIndex: Int,
    argument: String,
    writing: Boolean,
    status: String,
    writeFeedbackToken: Long,
    onDismiss: () -> Unit,
    onSave: (String, () -> Unit) -> Unit,
    onCancelWrite: () -> Unit
) {
    val action = ACTIONS[actionIndex]
    var editedArgument by rememberSaveable(actionIndex, argument) { mutableStateOf(argument) }
    var appPickerOpen by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var saveCompleted by rememberSaveable { mutableStateOf(false) }
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
            saveCompleted -> SavedButtonContent(slotIndex)
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
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !action.hasArgument || editedArgument.isNotBlank(),
                        onClick = {
                            onSave(editedArgument.trim()) { saveCompleted = true }
                        }
                    ) {
                        Text(stringResource(R.string.save))
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
                            contentDescription = action.label,
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
                    Text(text = action.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = editedArgument,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.close))
                        }
                        Button(onClick = { editing = true }) {
                            Text(stringResource(R.string.edit_action))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfigureCard(
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
                                contentDescription = selectedAction.label
                            )
                            Text(
                                text = selectedAction.label,
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
                                text = { Text(option.label) },
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
            }
        )
    }
}
