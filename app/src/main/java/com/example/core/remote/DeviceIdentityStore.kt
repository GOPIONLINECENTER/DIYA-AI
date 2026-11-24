package com.example.core.remote

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.SecureRandom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.diyaDeviceDataStore: DataStore<Preferences> by preferencesDataStore(name = "diya_device_identity")

data class LocalDeviceIdentity(
    val mobileDeviceId: String,
    val mobileDeviceName: String,
    val relayHttpsBaseUrl: String
)

class DeviceIdentityStore(private val context: Context) {
    private val appContext = context.applicationContext
    private val secureRandom = SecureRandom()

    private companion object {
        val KEY_MOBILE_DEVICE_ID = stringPreferencesKey("mobile_device_id")
        val KEY_MOBILE_DEVICE_NAME = stringPreferencesKey("mobile_device_name")
        val KEY_RELAY_BASE_URL = stringPreferencesKey("relay_https_base_url")
    }

    val identityFlow: Flow<LocalDeviceIdentity> = appContext.diyaDeviceDataStore.data.map { prefs ->
        LocalDeviceIdentity(
            mobileDeviceId = prefs[KEY_MOBILE_DEVICE_ID] ?: "",
            mobileDeviceName = prefs[KEY_MOBILE_DEVICE_NAME] ?: defaultDeviceName(),
            relayHttpsBaseUrl = prefs[KEY_RELAY_BASE_URL] ?: ""
        )
    }

    suspend fun getOrCreateIdentity(): LocalDeviceIdentity {
        val current = appContext.diyaDeviceDataStore.data.first()
        val existingId = current[KEY_MOBILE_DEVICE_ID]
        val existingName = current[KEY_MOBILE_DEVICE_NAME]
        val relayUrl = current[KEY_RELAY_BASE_URL] ?: ""

        if (!existingId.isNullOrBlank() && !existingName.isNullOrBlank()) {
            return LocalDeviceIdentity(existingId, existingName, relayUrl)
        }

        val generatedId = existingId?.takeIf { it.isNotBlank() } ?: generateUniqueDeviceId()
        val resolvedName = existingName?.takeIf { it.isNotBlank() } ?: defaultDeviceName()

        appContext.diyaDeviceDataStore.edit { prefs ->
            prefs[KEY_MOBILE_DEVICE_ID] = generatedId
            prefs[KEY_MOBILE_DEVICE_NAME] = resolvedName
        }

        return LocalDeviceIdentity(generatedId, resolvedName, relayUrl)
    }

    suspend fun updateRelayUrl(httpsUrl: String): Result<String> {
        val trimmed = httpsUrl.trim().trimEnd('/')
        if (trimmed.isNotEmpty() && !trimmed.startsWith("https://")) {
            return Result.failure(IllegalArgumentException("Relay URL must use HTTPS (https://...) for encrypted transport."))
        }
        appContext.diyaDeviceDataStore.edit { prefs ->
            prefs[KEY_RELAY_BASE_URL] = trimmed
        }
        return Result.success(trimmed)
    }

    suspend fun updateDeviceName(name: String): Result<Unit> {
        val sanitized = name.trim().take(80)
        if (sanitized.isEmpty()) {
            return Result.failure(IllegalArgumentException("Device name cannot be empty."))
        }
        appContext.diyaDeviceDataStore.edit { prefs ->
            prefs[KEY_MOBILE_DEVICE_NAME] = sanitized
        }
        return Result.success(Unit)
    }

    private fun generateUniqueDeviceId(): String {
        val bytes = ByteArray(8)
        secureRandom.nextBytes(bytes)
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return "mob_$hex"
    }

    private fun defaultDeviceName(): String {
        val brand = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        val combined = "$brand $model".trim()
        return (combined.ifEmpty { "Android Mobile" }).take(80)
    }
}
