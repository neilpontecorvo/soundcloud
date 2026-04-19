package com.neilpontecorvo.soundcloudfiretv.auth

import android.os.Handler
import android.os.Looper
import com.neilpontecorvo.soundcloudfiretv.network.ApiException
import com.neilpontecorvo.soundcloudfiretv.network.DeviceSessionApiClient
import java.util.concurrent.Executors

class ApiBackedAuthGateway(
    private val apiClient: DeviceSessionApiClient,
    private val deviceName: String,
    private val appVersion: String,
    private val sessionPersistence: SessionPersistence? = null
) : AuthGateway {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val listeners = mutableSetOf<(AuthSessionState) -> Unit>()
    private var state = AuthSessionState()

    override fun getCurrentState(): AuthSessionState = synchronized(lock) { state }

    override fun addListener(listener: (AuthSessionState) -> Unit) {
        synchronized(lock) {
            listeners.add(listener)
        }
        listener.invoke(getCurrentState())
    }

    override fun removeListener(listener: (AuthSessionState) -> Unit) {
        synchronized(lock) {
            listeners.remove(listener)
        }
    }

    override fun bootstrapSession() {
        setState(AuthSessionState(phase = AuthSessionPhase.BOOTSTRAPPING))
        runRequest {
            val response = apiClient.bootstrapDevice(deviceName, appVersion)
            AuthSessionState(
                phase = AuthSessionPhase.fromApiStatus(response.status),
                sessionId = response.sessionId,
                verificationUri = response.verificationUri,
                userCode = response.userCode,
                expiresAtIso = response.expiresAtIso
            )
        }
    }

    override fun pollSession() {
        val sessionId = getCurrentState().sessionId
        if (sessionId == null) {
            setState(getCurrentState().copy(phase = AuthSessionPhase.ERROR, lastErrorMessage = "No session to poll."))
            return
        }

        runRequest {
            val response = apiClient.getSession(sessionId)
            getCurrentState().copy(
                phase = AuthSessionPhase.fromApiStatus(response.status),
                sessionId = response.sessionId,
                expiresAtIso = response.expiresAtIso,
                authenticatedAtIso = response.authenticatedAtIso,
                accessTokenExpiresAtIso = response.accessTokenExpiresAtIso,
                lastErrorMessage = null
            )
        }
    }

    override fun exchangeAuthorizationCode(authorizationCode: String) {
        val sessionId = getCurrentState().sessionId
        if (sessionId == null) {
            setState(getCurrentState().copy(phase = AuthSessionPhase.ERROR, lastErrorMessage = "No session to exchange."))
            return
        }

        runRequest {
            val response = apiClient.exchangeAuth(sessionId, authorizationCode)
            getCurrentState().copy(
                phase = AuthSessionPhase.fromApiStatus(response.status),
                sessionId = response.sessionId,
                expiresAtIso = response.expiresAtIso,
                authenticatedAtIso = response.authenticatedAtIso,
                accessTokenExpiresAtIso = response.accessTokenExpiresAtIso,
                lastErrorMessage = null
            )
        }
    }

    override fun debugAuthenticateSession() {
        val sessionId = getCurrentState().sessionId
        if (sessionId == null) {
            setState(getCurrentState().copy(phase = AuthSessionPhase.ERROR, lastErrorMessage = "No session to debug-authenticate."))
            return
        }

        runRequest {
            val response = apiClient.debugAuthenticateSession(sessionId)
            getCurrentState().copy(
                phase = AuthSessionPhase.fromApiStatus(response.status),
                sessionId = response.sessionId,
                expiresAtIso = response.expiresAtIso,
                authenticatedAtIso = response.authenticatedAtIso,
                accessTokenExpiresAtIso = response.accessTokenExpiresAtIso,
                lastErrorMessage = null
            )
        }
    }

    override fun refreshSession() {
        val sessionId = getCurrentState().sessionId
        if (sessionId == null) {
            setState(getCurrentState().copy(phase = AuthSessionPhase.ERROR, lastErrorMessage = "No session to refresh."))
            return
        }

        setState(getCurrentState().copy(phase = AuthSessionPhase.REFRESHING, lastErrorMessage = null))
        runRequest {
            val response = apiClient.refreshAuth(sessionId)
            getCurrentState().copy(
                phase = AuthSessionPhase.fromApiStatus(response.status),
                sessionId = response.sessionId,
                expiresAtIso = response.expiresAtIso,
                authenticatedAtIso = response.authenticatedAtIso,
                accessTokenExpiresAtIso = response.accessTokenExpiresAtIso,
                lastErrorMessage = null
            )
        }
    }

    override fun clearSession() {
        sessionPersistence?.clear()
        setState(AuthSessionState())
    }

    override fun restoreOrBootstrap() {
        val persistedId = sessionPersistence?.getPersistedSessionId()
        if (persistedId.isNullOrBlank()) {
            bootstrapSession()
            return
        }

        setState(AuthSessionState(phase = AuthSessionPhase.BOOTSTRAPPING, sessionId = persistedId))
        executor.execute {
            try {
                val response = apiClient.getSession(persistedId)
                val phase = AuthSessionPhase.fromApiStatus(response.status)
                if (phase == AuthSessionPhase.AUTHENTICATED) {
                    setState(
                        AuthSessionState(
                            phase = phase,
                            sessionId = response.sessionId,
                            expiresAtIso = response.expiresAtIso,
                            authenticatedAtIso = response.authenticatedAtIso,
                            accessTokenExpiresAtIso = response.accessTokenExpiresAtIso
                        )
                    )
                    return@execute
                }
                // Session exists but is not authenticated (awaiting_auth / expired / error).
                // Drop the stale id and start a fresh bootstrap so the device never gets
                // stuck on a half-state session from a prior run.
                sessionPersistence?.clear()
                bootstrapSession()
            } catch (error: ApiException) {
                // 401 invalid_session is the normal "backend forgot us" case — drop id, bootstrap fresh.
                sessionPersistence?.clear()
                bootstrapSession()
            } catch (error: Exception) {
                // Network or parse error on startup — leave the persisted id in place so a
                // later retry can still succeed, but surface ERROR so the UI shows a
                // login-required state rather than hanging in BOOTSTRAPPING.
                setState(
                    AuthSessionState(
                        phase = AuthSessionPhase.ERROR,
                        sessionId = persistedId,
                        lastErrorMessage = error.message ?: error.javaClass.simpleName
                    )
                )
            }
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun runRequest(block: () -> AuthSessionState) {
        executor.execute {
            try {
                setState(block())
            } catch (error: ApiException) {
                setState(
                    getCurrentState().copy(
                        phase = AuthSessionPhase.ERROR,
                        lastErrorMessage = error.userMessage
                    )
                )
            } catch (error: Exception) {
                setState(
                    getCurrentState().copy(
                        phase = AuthSessionPhase.ERROR,
                        lastErrorMessage = error.message ?: error.javaClass.simpleName
                    )
                )
            }
        }
    }

    private fun setState(nextState: AuthSessionState) {
        val snapshot: List<(AuthSessionState) -> Unit>
        synchronized(lock) {
            state = nextState
            snapshot = listeners.toList()
        }
        if (nextState.phase == AuthSessionPhase.AUTHENTICATED && !nextState.sessionId.isNullOrBlank()) {
            sessionPersistence?.saveSessionId(nextState.sessionId)
        }
        mainHandler.post {
            snapshot.forEach { listener -> listener.invoke(nextState) }
        }
    }
}
