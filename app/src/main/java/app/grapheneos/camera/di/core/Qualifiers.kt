package app.grapheneos.camera.di.core

import javax.inject.Qualifier

/** Preferences that outlive the session writing them, whichever entry point that session is. */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class DurablePreferences

/** Preferences scoped to the running session. The default for anything the owner configured. */
@Retention(AnnotationRetention.BINARY)
@Qualifier
annotation class SessionPreferences
