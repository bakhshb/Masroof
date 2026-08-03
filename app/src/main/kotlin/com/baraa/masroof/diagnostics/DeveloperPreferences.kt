package com.baraa.masroof.diagnostics

/**
 * Persistent developer / testing preferences. Backed by
 * [androidx.preference.PreferenceManager] in production; tests can
 * drive this through a fake implementation.
 */
interface DeveloperPreferences {
    var showDevDetails: Boolean
    var testDataMode: Boolean
}
