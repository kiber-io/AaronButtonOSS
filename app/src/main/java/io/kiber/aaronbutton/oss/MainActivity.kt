package io.kiber.aaronbutton.oss

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale

open class MainActivity : ComponentActivity() {

    protected open val isNfcTrigger = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localizedContext(newBase))
    }

    companion object {
        private const val TAG = "AaronButtonOSS"
        private const val MIME_TYPE = "application/com.pitapolis.nfc"
        private const val DISABLED_MIME_TYPE = "application/vnd.aaronbutton.oss.disabled"
        private const val DISABLED_ACTION = "disabled"
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
    private lateinit var actionExecutor: ActionExecutor
    private var language = AppLanguage.ENGLISH
    private var nfcAdapter: NfcAdapter? = null
    private var androidId = ""
    private var pendingPayload: String? = null
    private var pendingActionIndex = 0
    private var pendingTargetSlot: Int? = null
    private var pendingArgument = ""
    private var pendingClear = false
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
        preferences = getSharedPreferences(CONFIG_PREFS, MODE_PRIVATE)
        language = appLanguage(this)
        actionExecutor = ActionExecutor(
            activity = this,
            preferences = preferences,
            isNfcTrigger = { isNfcTrigger },
            isWriting = { writing },
            isWriteBlocked = ::isWriteBlocked,
            toast = ::toast
        )
        val allTagIdsKnown = (0 until SLOT_COUNT).all {
            !preferences.getString("tag_id_$it", "").isNullOrEmpty()
        }
        buttonSetupComplete = preferences.getBoolean(BUTTON_SETUP_COMPLETE, allTagIdsKnown) && allTagIdsKnown
        androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        refreshNfcStatus()

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
                            nfcReady = nfcAdapter?.isEnabled == true,
                            language = language,
                            writing = writing,
                            configuredActions = configuredActions.value,
                            configuredArguments = configuredArguments.value,
                            configuredTagIds = configuredTagIds.value,
                            highlightedSlot = highlightedSlot.value,
                            highlightToken = highlightToken.value,
                            onWrite = { actionIndex, argument, targetSlot, onSuccess ->
                                beginWrite(actionIndex, argument, targetSlot, onSuccess)
                            },
                            onClear = { slot, onSuccess ->
                                beginWrite(-1, "", slot, onSuccess, clearButton = true)
                            },
                            onCancelWrite = ::cancelWrite,
                            writeFeedbackToken = writeFeedbackToken.value,
                            onLanguageSelected = ::changeLanguage
                        )
                    } else {
                        SetupWizard(
                            step = learningStep,
                            scanning = learning,
                            status = learningStatus.value,
                            onScan = ::startLearning,
                            language = language,
                            onLanguageSelected = ::changeLanguage
                        )
                    }
                }
            }
        }
        if (buttonSetupComplete) handleNfcIntent(intent)
        if (isNfcTrigger && !actionExecutor.hasPendingPermission()) finish()
    }

    private fun changeLanguage(newLanguage: AppLanguage) {
        if (newLanguage == language) return
        setAppLanguage(this, newLanguage)
        recreate()
    }

    override fun onResume() {
        super.onResume()
        loadConfiguredState()
        if (!writing) refreshNfcStatus()
        if ((writing || learning) && nfcAdapter != null) enableReaderMode()
    }

    private fun refreshNfcStatus() {
        status.value = getString(
            when {
                nfcAdapter == null -> R.string.nfc_unavailable
                nfcAdapter?.isEnabled != true -> R.string.nfc_disabled
                else -> R.string.ready
            }
        )
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
        actionExecutor.handlePermissionResult(requestCode, grantResults)
    }

    private fun beginWrite(
        actionIndex: Int,
        rawArgument: String,
        targetSlot: Int? = null,
        onSuccess: () -> Unit = {},
        clearButton: Boolean = false
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

        pendingTargetSlot = targetSlot?.takeIf { it in 0 until SLOT_COUNT }
        if (clearButton) {
            if (pendingTargetSlot == null) return
            pendingActionIndex = -1
            pendingArgument = ""
            pendingPayload = try {
                NfcPayload.encode(androidId, DISABLED_ACTION)
            } catch (e: IllegalArgumentException) {
                toast(e.message.orEmpty())
                return
            }
            pendingClear = true
        } else {
            pendingActionIndex = actionIndex.coerceIn(0, ACTIONS.lastIndex)
            val action = ACTIONS[pendingActionIndex]
            val argument = when {
                action.code == CUSTOM_VALUE_CODE -> rawArgument
                action.hasArgument -> rawArgument.trim()
                else -> ""
            }
            if (action.hasArgument && argument.isEmpty()) {
                toast(getString(
                    when (action.code) {
                        "termux_" -> R.string.termux_argument_required
                        CUSTOM_INTENT_PREFIX -> R.string.custom_intent_required
                        CUSTOM_VALUE_CODE -> R.string.custom_value_required
                        else -> R.string.argument_required
                    }
                ))
                return
            }

            val actionValue = try {
                when {
                    action.code == CUSTOM_INTENT_PREFIX -> CustomIntentSpec.toPayloadAction(argument)
                    action.code == CUSTOM_VALUE_CODE -> argument
                    else -> action.code + if (action.hasArgument) argument else ""
                }
            } catch (e: IllegalArgumentException) {
                toast(localizedErrorMessage(this, e, R.string.invalid_custom_intent))
                return
            }
            pendingArgument = argument
            pendingPayload = try {
                NfcPayload.encode(androidId, actionValue)
            } catch (e: IllegalArgumentException) {
                toast(e.message.orEmpty())
                return
            }
            pendingClear = false
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
            getString(
                if (pendingClear) R.string.touch_target_button_clear else R.string.touch_target_button,
                it + 1
            )
        } ?: getString(R.string.touch_tag)
        enableReaderMode()
    }

    private fun cancelWrite() {
        if (!writing) return
        writing = false
        pendingPayload = null
        pendingTargetSlot = null
        pendingArgument = ""
        pendingClear = false
        pendingWriteSuccess = {}
        writeFeedbackToken.value = 0L
        preferences.edit()
            .putBoolean(WRITE_IN_PROGRESS, false)
            .remove(WRITE_BLOCK_UNTIL)
            .apply()
        stopReaderMode()
        refreshNfcStatus()
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
        val clear = pendingClear

        var ndef: Ndef? = null
        var formatable: NdefFormatable? = null
        try {
            val id = tagId(tag)
            if (id.isEmpty()) throw IOException(getString(R.string.nfc_tag_id_unavailable))
            val slotIndex = findSlotForConfiguration(id)
            val targetSlot = pendingTargetSlot
            if (targetSlot != null && slotIndex != targetSlot) {
                runOnUiThread {
                    status.value = getString(
                        if (clear) R.string.wrong_button_for_clear else R.string.wrong_button_for_save,
                        targetSlot + 1
                    )
                    writeFeedbackToken.value++
                }
                return
            }
            if (slotIndex < 0) throw IOException(getString(R.string.no_button_slot))
            val actionIndex = pendingActionIndex
            val argument = pendingArgument
            val message = NdefMessage(
                arrayOf(
                    NdefRecord.createMime(
                        if (clear) DISABLED_MIME_TYPE else MIME_TYPE,
                        payload.toByteArray(StandardCharsets.UTF_8)
                    )
                )
            )
            val currentNdef = Ndef.get(tag)
            ndef = currentNdef
            if (currentNdef != null) {
                currentNdef.connect()
                if (!currentNdef.isWritable) throw IOException(getString(R.string.nfc_tag_read_only))
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
            val preferencesEditor = preferences.edit()
                .putString("tag_id_$slotIndex", id)
                .putInt(LAST_SLOT, slotIndex)
                .putLong(LAST_EVENT, eventToken)
                .putBoolean(WRITE_IN_PROGRESS, false)
                .putLong(WRITE_BLOCK_UNTIL, cooldownUntil)
            if (clear) {
                preferencesEditor
                    .remove("action_$slotIndex")
                    .remove("argument_$slotIndex")
                    .remove(IGNORE_PAYLOAD)
                    .remove(IGNORE_PAYLOAD_UNTIL)
            } else {
                preferencesEditor
                    .putInt("action_$slotIndex", actionIndex)
                    .putString("argument_$slotIndex", argument)
                    .putString(IGNORE_PAYLOAD, payload)
                    .putLong(IGNORE_PAYLOAD_UNTIL, System.currentTimeMillis() + IGNORE_PAYLOAD_WINDOW_MS)
            }
            preferencesEditor.apply()
            runOnUiThread {
                val completion = pendingWriteSuccess
                pendingWriteSuccess = {}
                configuredActions.value = configuredActions.value.toMutableList().also {
                    it[slotIndex] = if (clear) -1 else actionIndex
                }
                configuredArguments.value = configuredArguments.value.toMutableList().also {
                    it[slotIndex] = if (clear) "" else argument
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
                status.value = getString(
                    if (clear) R.string.clear_success else R.string.write_success,
                    slotIndex + 1
                )
                pendingClear = false
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
                pendingClear = false
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
        if (intent.type == DISABLED_MIME_TYPE) return
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
                    actionExecutor.execute(configuredAction)
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
        if (ignored != payload) return false
        preferences.edit().remove(IGNORE_PAYLOAD).remove(IGNORE_PAYLOAD_UNTIL).apply()
        return true
    }

    private fun toast(message: String) {
        if (isNfcTrigger) return
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

}
