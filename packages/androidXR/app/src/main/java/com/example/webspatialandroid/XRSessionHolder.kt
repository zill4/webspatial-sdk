package com.example.webspatialandroid

import androidx.xr.runtime.Session
import java.lang.ref.WeakReference

/**
 * Singleton holder for the XR Session.
 * This bridges between Compose (where LocalSession is available) and
 * CommandManager (which needs Session for SceneCore operations).
 *
 * The Session is stored as a WeakReference to avoid memory leaks.
 */
object XRSessionHolder {

    private var sessionRef: WeakReference<Session>? = null

    /**
     * Set the XR Session. Should be called from Compose when session is available.
     */
    fun setSession(session: Session) {
        sessionRef = WeakReference(session)
    }

    /**
     * Get the XR Session if available.
     * Returns null if session hasn't been set or has been garbage collected.
     */
    fun getSession(): Session? {
        return sessionRef?.get()
    }

    /**
     * Clear the session reference.
     * Should be called when the activity is destroyed.
     */
    fun clearSession() {
        sessionRef = null
    }

    /**
     * Check if a valid session is available.
     */
    fun hasSession(): Boolean {
        return sessionRef?.get() != null
    }
}
