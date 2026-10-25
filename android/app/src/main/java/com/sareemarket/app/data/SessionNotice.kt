package com.sareemarket.app.data

import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/** Remembers that the server ended the session (expired token, disabled account), so the login screen can say why. */
@Singleton
class SessionNotice @Inject constructor() {
    private val expired = AtomicBoolean(false)

    fun markExpired() = expired.set(true)

    /** True once after a forced logout; reading it clears it. */
    fun takeExpired(): Boolean = expired.getAndSet(false)
}
