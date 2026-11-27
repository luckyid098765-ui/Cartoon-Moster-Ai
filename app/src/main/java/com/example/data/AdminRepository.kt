package com.example.data

import android.content.Context
import com.example.R
import com.example.model.AdminProviderConfig
import com.example.model.ErrorLogEntry
import com.example.model.GenerationJob
import com.example.model.ProviderMode
import com.example.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class AdminRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun isCurrentUserAdmin(): Boolean {
        val user = auth.currentUser ?: return false
        return user.isEmailVerified && user.email == BOOTSTRAPPED_ADMIN_EMAIL
    }

    suspend fun checkAdminAccess(): Boolean {
        val user = auth.currentUser ?: return false
        if (user.isEmailVerified && user.email == BOOTSTRAPPED_ADMIN_EMAIL) return true
        return try {
            db.collection("admins").document(user.uid).get().await().exists()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    fun observeProviderConfig(): Flow<AdminProviderConfig> = flow {
        val path = "adminConfig/providers"
        emitAll(
            db.collection("adminConfig").document("providers")
                .snapshots()
                .map { snap ->
                    if (snap.exists()) {
                        snap.toObject(
                            AdminProviderConfig::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        ) ?: AdminProviderConfig()
                    } else {
                        AdminProviderConfig()
                    }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser != null) {
                        handleFirestoreError(toLog, OperationType.GET, path)
                    }
                    emit(AdminProviderConfig())
                }
        )
    }

    suspend fun getProviderConfig(): AdminProviderConfig {
        return try {
            val snap = db.collection("adminConfig").document("providers").get().await()
            if (snap.exists()) {
                snap.toObject(
                    AdminProviderConfig::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: AdminProviderConfig()
            } else {
                AdminProviderConfig()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "adminConfig/providers")
            AdminProviderConfig()
        }
    }

    fun observeUserJobs(userId: String): Flow<List<GenerationJob>> = flow {
        require(userId.isNotBlank()) { "User ID required" }
        val path = "generationJobs"
        emitAll(
            db.collection("generationJobs")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        GenerationJob::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllJobsForAdmin(): Flow<List<GenerationJob>> = flow {
        val path = "generationJobs"
        emitAll(
            db.collection("generationJobs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        GenerationJob::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser != null) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllErrorLogsForAdmin(): Flow<List<ErrorLogEntry>> = flow {
        val path = "errorLogs"
        emitAll(
            db.collection("errorLogs")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(100)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        ErrorLogEntry::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser != null) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeAllUsersForAdmin(): Flow<List<UserProfile>> = flow {
        val path = "users"
        emitAll(
            db.collection("users")
                .limit(100)
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val fsError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsError == null && error is CancellationException) throw error
                    val toLog = fsError ?: (error as? Exception)
                    if (toLog != null && auth.currentUser != null) {
                        handleFirestoreError(toLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun saveProviderConfig(config: AdminProviderConfig): Result<Unit> {
        val path = "adminConfig/providers"
        return try {
            val email = auth.currentUser?.email ?: auth.currentUser?.uid
                ?: error("Authentication required")
            val activeMode = ProviderMode.safeValueOf(config.activeProvider)
            val payload = mapOf(
                "activeProvider" to activeMode.name,
                "providerLabel" to config.providerLabel.ifBlank { activeMode.label }.take(100),
                "remoteEndpointUrl" to config.remoteEndpointUrl.trim().take(500),
                "timeoutSeconds" to config.timeoutSeconds.coerceIn(10L, 600L),
                "maxRetries" to config.maxRetries.coerceIn(0L, 10L),
                "storageQuotaMb" to config.storageQuotaMb.coerceIn(100L, 1_000_000L),
                "adMobEnabled" to config.adMobEnabled,
                "adMobBannerUnitId" to config.adMobBannerUnitId.trim().take(200),
                "maintenanceMode" to config.maintenanceMode,
                "updatedBy" to email.take(150),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("adminConfig").document("providers").set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun resolveErrorLog(logId: String): Result<Unit> {
        val path = "errorLogs/$logId"
        return try {
            db.collection("errorLogs").document(logId)
                .update(mapOf("resolved" to true))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun setUserAccountStatus(targetUserId: String, status: String): Result<Unit> {
        val path = "users/$targetUserId"
        return try {
            val safeStatus = if (status == "SUSPENDED") "SUSPENDED" else "ACTIVE"
            db.collection("users").document(targetUserId)
                .update(
                    mapOf(
                        "accountStatus" to safeStatus,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    companion object {
        const val BOOTSTRAPPED_ADMIN_EMAIL = "sudarshanmanna06@gmail.com"
    }
}
