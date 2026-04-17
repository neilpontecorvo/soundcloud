package com.neilpontecorvo.soundcloudfiretv.auth

import android.os.Handler
import android.os.Looper
import com.neilpontecorvo.soundcloudfiretv.network.ApiException
import com.neilpontecorvo.soundcloudfiretv.network.DeviceSessionApiClient
import java.util.concurrent.Executors

class ApiBackedAuthGateway(
    private val apiClient: DeviceSessionApiClient,
    private val deviceName: String,
    private val appVersion: String
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
        setState(AuthSessionState())
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
        mainHandler.post {
            snapshot.forEach { listener -> listener.invoke(nextState) }
        }
    }
}
