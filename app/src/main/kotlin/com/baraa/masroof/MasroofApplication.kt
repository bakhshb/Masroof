package com.baraa.masroof

import android.app.Application

/**
 * Minimal application entry for the clean Masroof baseline.
 *
 * Composition-root wiring for domain, SMS, parsing, and persistence will be
 * introduced in later rewrite phases. P0 intentionally has no service locator,
 * Room database, or feature services.
 */
class MasroofApplication : Application()
