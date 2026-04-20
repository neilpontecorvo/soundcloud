package com.neilpontecorvo.soundcloudfiretv.auth

enum class AuthSessionPhase(val label: String) {
    IDLE("idle"),
    BOOTSTRAPPING("bootstrapping"),
    AWAITING_AUTH("awaiting_auth"),
    AUTHENTICATED("authenticated"),
    EXPIRED("expired"),
    REFRESHING("refreshing"),
    ERROR("error");

    companion object {
        fun fromApiStatus(status: String?): AuthSessionPhase = when (status) {
            "awaiting_auth" -> AWAITING_AUTH
            "authenticated" -> AUTHENTICATED
            "expired" -> EXPIRED
            "error" -> ERROR
            else -> IDLE
        }
    }
}

data class AuthSessionState(
    val phase: AuthSessionPhase = AuthSessionPhase.IDLE,
    val sessionId: String? = null,
    val verificationUri: String? = null,
    val verificationUriComplete: String? = null,
    val userCode: String? = null,
    val expiresAtIso: String? = null,
    val authenticatedAtIso: String? = null,
    val accessTokenExpiresAtIso: String? = null,
    val lastErrorMessage: String? = null
) {
    val isAuthenticated: Boolean
        get() = phase == AuthSessionPhase.AUTHENTICATED
}

interface AuthGateway {
    fun getCurrentState(): AuthSessionState
    fun addListener(listener: (AuthSessionState) -> Unit)
    fun removeListener(listener: (AuthSessionState) -> Unit)
    fun bootstrapSession()
    fun pollSession()
    fun exchangeAuthorizationCode(authorizationCode: String)
    fun debugAuthenticateSession()
    fun refreshSession()
    fun clearSession()

    /**
     * Silent startup path: if a sessionId is already persisted on device,
     * probe the backend. If the backend reports it authenticated, surface
     * AUTHENTICATED directly with no user interaction. Otherwise clear the
     * stale id and fall back to bootstrapSession().
     */
    fun restoreOrBootstrap()
}
