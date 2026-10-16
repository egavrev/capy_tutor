package com.capytutor.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "capy_settings")

/** How generated text refers to the child (only this word leaves the phone, never the name). */
enum class ChildGender(val label: String, val promptForm: String) {
    GIRL("Fată", "o fată"),
    BOY("Băiat", "un băiat"),
    UNSAID("Nu spun", "un copil"),
}

data class Settings(
    /** False until the stored values were read once (avoids flashing the setup screen). */
    val loaded: Boolean = false,
    val setupDone: Boolean = false,
    val childName: String = "",
    val gender: ChildGender = ChildGender.UNSAID,
    val hasPin: Boolean = false,
    val hasGroqKey: Boolean = false,
    /** For display only: "…a1b2". The full key is never shown. */
    val groqKeyLast4: String = "",
    val voiceOn: Boolean = false,
)

/**
 * Settings and secrets (HANDOFF.md §9, decision 8): the child's name and the Groq key are
 * encrypted with the Keystore; the PIN is stored as a salted hash. Nothing here goes into
 * BuildConfig, resources or logs.
 */
class SettingsStore(context: Context, scope: CoroutineScope, private val box: SecretBox = SecretBox()) {
    private val store = context.applicationContext.dataStore

    val settings: StateFlow<Settings> = store.data.map { p ->
        val key = p[GROQ_KEY]?.let(box::decrypt)
        Settings(
            loaded = true,
            setupDone = p[SETUP_DONE] ?: false,
            childName = p[CHILD_NAME]?.let(box::decrypt).orEmpty(),
            gender = p[GENDER]?.let { g -> ChildGender.entries.firstOrNull { it.name == g } } ?: ChildGender.UNSAID,
            hasPin = p[PIN_HASH] != null,
            hasGroqKey = !key.isNullOrBlank(),
            groqKeyLast4 = key?.takeLast(4).orEmpty(),
            voiceOn = p[VOICE_ON] ?: false,
        )
    }.stateIn(scope, SharingStarted.Eagerly, Settings())

    /** The key for the next Groq call, decrypted on demand. Empty when not set. */
    @Volatile
    var groqKey: String = ""
        private set

    suspend fun load() {
        groqKey = store.data.first()[GROQ_KEY]?.let(box::decrypt).orEmpty()
    }

    suspend fun setChildName(name: String) {
        store.edit { it[CHILD_NAME] = box.encrypt(name.trim()) }
    }

    suspend fun setGender(g: ChildGender) {
        store.edit { it[GENDER] = g.name }
    }

    suspend fun setGroqKey(key: String) {
        val k = key.trim()
        store.edit { if (k.isEmpty()) it.remove(GROQ_KEY) else it[GROQ_KEY] = box.encrypt(k) }
        groqKey = k
    }

    suspend fun setVoiceOn(on: Boolean) {
        store.edit { it[VOICE_ON] = on }
    }

    suspend fun setPin(pin: String) {
        val salt = PinHash.newSalt()
        store.edit {
            it[PIN_SALT] = salt
            it[PIN_HASH] = PinHash.hash(pin, salt)
        }
    }

    suspend fun checkPin(pin: String): Boolean {
        val p = store.data.first()
        val salt = p[PIN_SALT] ?: return false
        return p[PIN_HASH] == PinHash.hash(pin, salt)
    }

    suspend fun finishSetup() {
        store.edit { it[SETUP_DONE] = true }
    }

    private companion object {
        val SETUP_DONE = booleanPreferencesKey("setup_done")
        val CHILD_NAME = stringPreferencesKey("child_name_enc")
        val GENDER = stringPreferencesKey("child_gender")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val GROQ_KEY = stringPreferencesKey("groq_key_enc")
        val VOICE_ON = booleanPreferencesKey("voice_on")
    }
}
