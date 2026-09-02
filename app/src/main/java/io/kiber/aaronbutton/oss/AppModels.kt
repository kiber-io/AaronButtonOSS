package io.kiber.aaronbutton.oss

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Base64
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.nio.charset.StandardCharsets
import java.util.Locale

internal class AppError(
    val messageRes: Int,
    vararg val messageArgs: Any
) : IllegalArgumentException()

internal fun localizedErrorMessage(
    context: Context,
    error: Throwable,
    fallbackRes: Int
): String {
    return if (error is AppError) {
        context.getString(error.messageRes, *error.messageArgs)
    } else {
        error.message ?: context.getString(fallbackRes)
    }
}

internal const val SLOT_COUNT = 3
internal const val HIGHLIGHT_DURATION_MS = 1500L
internal const val CUSTOM_INTENT_PREFIX = "custom_intent_"
internal val CUSTOM_INTENT_EXTRA_TYPES = listOf("string", "int", "long", "boolean", "float", "double")
private val CUSTOM_INTENT_FIELDS = setOf("action", "data", "type", "package", "component", "flags")

internal data class ActionOption(
    val labelRes: Int,
    val code: String,
    val hasArgument: Boolean,
    val icon: ImageVector
)

internal data class AppChoice(
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

internal fun loadAppIcon(context: Context, packageName: String): Bitmap? {
    if (packageName.isBlank()) return null
    return runCatching {
        context.packageManager.getApplicationIcon(packageName).toBitmap()
    }.getOrNull()
}

internal fun loadInstalledApps(context: Context): List<AppChoice> {
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

internal val ACTIONS = listOf(
    ActionOption(R.string.action_flashlight, "flash_light", false, Icons.Filled.FlashOn),
    ActionOption(R.string.action_camera, "system_camera", false, Icons.Filled.CameraAlt),
    ActionOption(R.string.action_open_app, "open_app_", true, Icons.Filled.Apps),
    ActionOption(R.string.action_open_link, "open_link_", true, Icons.Filled.Link),
    ActionOption(R.string.action_termux, "termux_", true, Icons.Filled.Code),
    ActionOption(R.string.action_custom_intent, CUSTOM_INTENT_PREFIX, true, Icons.Filled.Code),
    ActionOption(R.string.action_sound, "sound", false, Icons.Filled.VolumeUp),
    ActionOption(R.string.action_nfc_settings, "nfc_settings", false, Icons.Filled.Settings),
    ActionOption(R.string.action_location_settings, "location", false, Icons.Filled.LocationOn),
    ActionOption(R.string.action_airplane_mode, "airplane", false, Icons.Filled.AirplanemodeActive)
)

internal data class CustomIntentExtra(
    val type: String,
    val name: String,
    val value: String
)

internal data class ParsedCustomIntent(
    val fields: Map<String, String>,
    val categories: List<String>,
    val extras: List<CustomIntentExtra>
)

internal object CustomIntentSpec {
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
                throw AppError(R.string.custom_intent_invalid_line, index + 1)
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
                    throw AppError(R.string.custom_intent_extra_name_empty)
                }
                extras += CustomIntentExtra(type, name, value)
            } else if (key == "category") {
                if (value.isEmpty()) throw AppError(R.string.custom_intent_category_empty)
                categories += value
            } else if (key in CUSTOM_INTENT_FIELDS) {
                if (key in fields) throw AppError(R.string.custom_intent_duplicate_field, key)
                fields[key] = value
            } else {
                throw AppError(R.string.custom_intent_unknown_field, rawKey)
            }
        }

        return ParsedCustomIntent(fields, categories, extras)
    }

    fun buildIntent(definition: String): Intent {
        val parsed = parse(definition)

        val action = parsed.fields["action"].orEmpty()
        if (action.isEmpty()) throw AppError(R.string.custom_intent_action_required)
        val intent = Intent(action)
        parsed.fields["data"]?.takeIf { it.isNotEmpty() }?.let { data ->
            val type = parsed.fields["type"]
            if (type.isNullOrEmpty()) intent.data = Uri.parse(data)
            else intent.setDataAndType(Uri.parse(data), type)
        } ?: parsed.fields["type"]?.takeIf { it.isNotEmpty() }?.let(intent::setType)
        parsed.fields["package"]?.takeIf { it.isNotEmpty() }?.let(intent::setPackage)
        parsed.fields["component"]?.takeIf { it.isNotEmpty() }?.let { component ->
            intent.component = ComponentName.unflattenFromString(component)
                ?: throw AppError(R.string.custom_intent_component_invalid, component)
        }
        parsed.fields["flags"]?.takeIf { it.isNotEmpty() }?.let { flags ->
            intent.addFlags(parseFlags(flags))
        }
        parsed.categories.forEach(intent::addCategory)
        parsed.extras.forEach { extra ->
            when (extra.type) {
                "string" -> intent.putExtra(extra.name, extra.value)
                "int" -> intent.putExtra(extra.name, extra.value.toIntOrNull()
                    ?: throw AppError(R.string.custom_intent_int_invalid, extra.name))
                "long" -> intent.putExtra(extra.name, extra.value.toLongOrNull()
                    ?: throw AppError(R.string.custom_intent_long_invalid, extra.name))
                "boolean" -> intent.putExtra(extra.name, parseBoolean(extra.value, extra.name))
                "float" -> intent.putExtra(extra.name, extra.value.toFloatOrNull()
                    ?: throw AppError(R.string.custom_intent_float_invalid, extra.name))
                "double" -> intent.putExtra(extra.name, extra.value.toDoubleOrNull()
                    ?: throw AppError(R.string.custom_intent_double_invalid, extra.name))
            }
        }
        return intent
    }

    private fun parseBoolean(value: String, name: String): Boolean {
        return when (value.lowercase(Locale.ROOT)) {
            "true" -> true
            "false" -> false
            else -> throw AppError(R.string.custom_intent_boolean_invalid, name)
        }
    }

    private fun parseFlags(value: String): Int {
        val parsed = if (value.startsWith("0x", ignoreCase = true)) {
            value.substring(2).toLongOrNull(16)?.takeIf { it <= 0xFFFFFFFFL }?.toInt()
        } else {
            value.toLongOrNull()?.takeIf { it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong() }?.toInt()
        }
        return parsed ?: throw AppError(R.string.custom_intent_flags_invalid)
    }
}

internal data class CustomIntentDraft(
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

internal object MdSpacing {
    val small = 8.dp
    val medium = 16.dp
    val maxContent = 840.dp
}
