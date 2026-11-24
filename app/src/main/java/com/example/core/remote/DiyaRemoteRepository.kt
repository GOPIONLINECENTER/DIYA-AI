package com.example.core.remote

import android.content.Context
import com.example.R
import com.example.core.firestore.OperationType
import com.example.core.firestore.handleFirestoreError
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import java.security.SecureRandom
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class DiyaRemoteRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val secureRandom = SecureRandom()

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must sign in with Google before accessing DIYA remote bridge.")

    fun observeDevices(userId: String): Flow<List<DeviceRecord>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Authenticated user mismatch." }
        val path = "users/$userId/devices"
        emitAll(
            db.collection("users").document(userId).collection("devices")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        DeviceRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observePairingCodes(userId: String): Flow<List<PairingCodeRecord>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Authenticated user mismatch." }
        val path = "users/$userId/pairingCodes"
        emitAll(
            db.collection("users").document(userId).collection("pairingCodes")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(10)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PairingCodeRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCommandRequests(userId: String): Flow<List<CommandRequestRecord>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Authenticated user mismatch." }
        val path = "users/$userId/commandRequests"
        emitAll(
            db.collection("users").document(userId).collection("commandRequests")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(25)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CommandRequestRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    val toLog = fsErr ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertMobileDeviceSession(
        userId: String,
        mobileDeviceId: String,
        mobileDeviceName: String,
        pairingStatus: String
    ): Result<Unit> {
        val docPath = "users/$userId/devices/$mobileDeviceId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(mobileDeviceId.matches(Regex("^[a-zA-Z0-9_\\-]{4,128}$")))
            val safeName = mobileDeviceName.trim().ifEmpty { "Android Mobile" }.take(80)
            val safePairing = if (pairingStatus in setOf("UNPAIRED", "WAITING_FOR_PC", "PAIRED")) {
                pairingStatus
            } else "UNPAIRED"

            val docRef = db.collection("users").document(uid)
                .collection("devices").document(mobileDeviceId)
            val existing = docRef.get().await()

            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "deviceName" to safeName,
                        "connectionStatus" to "ONLINE",
                        "pairingStatus" to safePairing,
                        "lastConnectedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                val payload = mapOf(
                    "userId" to uid,
                    "deviceType" to "ANDROID_MOBILE",
                    "deviceName" to safeName,
                    "connectionStatus" to "ONLINE",
                    "pairingStatus" to safePairing,
                    "allowedActions" to AllowedDesktopAction.entries.map { it.id },
                    "lastConnectedAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun generateTemporaryPairingCode(
        userId: String,
        mobileDeviceId: String,
        mobileDeviceName: String
    ): Result<PairingCodeRecord> {
        val collectionPath = "users/$userId/pairingCodes"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val safeName = mobileDeviceName.trim().ifEmpty { "Android Mobile" }.take(80)

            // Revoke existing active codes first
            val activeSnapshot = db.collection("users").document(uid)
                .collection("pairingCodes")
                .whereEqualTo("status", "ACTIVE")
                .get()
                .await()

            for (doc in activeSnapshot.documents) {
                doc.reference.update(
                    mapOf(
                        "status" to "REVOKED",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val code = buildCryptographicPairingCode()
            val expiresAt = Timestamp(Date(System.currentTimeMillis() + 5 * 60_000L))
            val docRef = db.collection("users").document(uid)
                .collection("pairingCodes").document()

            val payload = mapOf(
                "userId" to uid,
                "code" to code,
                "mobileDeviceId" to mobileDeviceId,
                "mobileDeviceName" to safeName,
                "status" to "ACTIVE",
                "expiresAt" to expiresAt,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()

            // Mark mobile device as waiting for PC handshake
            val mobileRef = db.collection("users").document(uid)
                .collection("devices").document(mobileDeviceId)
            if (mobileRef.get().await().exists()) {
                mobileRef.update(
                    mapOf(
                        "pairingStatus" to "WAITING_FOR_PC",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            Result.success(
                PairingCodeRecord(
                    id = docRef.id,
                    userId = uid,
                    code = code,
                    mobileDeviceId = mobileDeviceId,
                    mobileDeviceName = safeName,
                    status = "ACTIVE",
                    expiresAt = expiresAt
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, collectionPath)
            Result.failure(e)
        }
    }

    suspend fun revokePairingCode(userId: String, codeId: String): Result<Unit> {
        val docPath = "users/$userId/pairingCodes/$codeId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(codeId.isNotBlank())
            db.collection("users").document(uid)
                .collection("pairingCodes").document(codeId)
                .update(
                    mapOf(
                        "status" to "REVOKED",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun submitCommandRequest(
        userId: String,
        sourceDeviceId: String,
        targetDeviceId: String?,
        action: AllowedDesktopAction,
        summaryText: String,
        rawScope: String,
        isPcOnlineOverWss: Boolean
    ): Result<CommandRequestRecord> {
        val collectionPath = "users/$userId/commandRequests"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            val safeSummary = summaryText.trim().ifEmpty { action.title }.take(300)
            val safeScope = sanitizeScopeForAction(action, rawScope)

            val needsConfirm = action.requiresExplicitConfirmation
            val initialStatus = when {
                needsConfirm -> "PENDING_CONFIRMATION"
                isPcOnlineOverWss && !targetDeviceId.isNullOrBlank() -> "APPROVED_QUEUED"
                else -> "PC_OFFLINE_NOT_SENT"
            }

            val docRef = db.collection("users").document(uid)
                .collection("commandRequests").document()

            val payload = mutableMapOf<String, Any>(
                "userId" to uid,
                "sourceDeviceId" to sourceDeviceId,
                "actionType" to action.id,
                "summaryText" to safeSummary,
                "targetScope" to safeScope,
                "confirmationRequired" to needsConfirm,
                "confirmed" to !needsConfirm,
                "status" to initialStatus,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!targetDeviceId.isNullOrBlank()) {
                payload["targetDeviceId"] = targetDeviceId.take(128)
            }

            docRef.set(payload).await()
            Result.success(
                CommandRequestRecord(
                    id = docRef.id,
                    userId = uid,
                    sourceDeviceId = sourceDeviceId,
                    targetDeviceId = targetDeviceId.orEmpty(),
                    actionType = action.id,
                    summaryText = safeSummary,
                    targetScope = safeScope,
                    confirmationRequired = needsConfirm,
                    confirmed = !needsConfirm,
                    status = initialStatus
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, collectionPath)
            Result.failure(e)
        }
    }

    suspend fun confirmCommandRequest(
        userId: String,
        commandId: String,
        targetDeviceId: String?,
        isPcOnlineOverWss: Boolean
    ): Result<String> {
        val docPath = "users/$userId/commandRequests/$commandId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(commandId.isNotBlank())
            val nextStatus = if (isPcOnlineOverWss && !targetDeviceId.isNullOrBlank()) {
                "APPROVED_QUEUED"
            } else {
                "PC_OFFLINE_NOT_SENT"
            }

            val updates = mutableMapOf<String, Any>(
                "confirmed" to true,
                "status" to nextStatus,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (!targetDeviceId.isNullOrBlank()) {
                updates["targetDeviceId"] = targetDeviceId.take(128)
            }

            db.collection("users").document(uid)
                .collection("commandRequests").document(commandId)
                .update(updates)
                .await()
            Result.success(nextStatus)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun cancelCommandRequest(userId: String, commandId: String): Result<Unit> {
        val docPath = "users/$userId/commandRequests/$commandId"
        return try {
            val uid = requireUserId()
            check(uid == userId)
            require(commandId.isNotBlank())
            db.collection("users").document(uid)
                .collection("commandRequests").document(commandId)
                .update(
                    mapOf(
                        "status" to "CANCELLED",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    private fun buildCryptographicPairingCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val part1 = (1..4).map { alphabet[secureRandom.nextInt(alphabet.length)] }.joinToString("")
        val part2 = (1..4).map { alphabet[secureRandom.nextInt(alphabet.length)] }.joinToString("")
        return "$part1-$part2"
    }

    private fun sanitizeScopeForAction(action: AllowedDesktopAction, rawScope: String): String {
        val trimmed = rawScope.trim().take(300)
        return when (action) {
            AllowedDesktopAction.OPEN_BROWSER_URL -> {
                val candidate = trimmed.ifEmpty { action.defaultScopeHint }
                require(candidate.startsWith("https://")) {
                    "Only encrypted HTTPS URLs are permitted for browser actions."
                }
                candidate
            }
            else -> {
                require(!trimmed.contains("..") && !trimmed.contains(";") && !trimmed.contains("|") && !trimmed.contains("&&")) {
                    "Disallowed path traversal or shell characters in scope."
                }
                trimmed.ifEmpty { action.defaultScopeHint }
            }
        }
    }
}
