package com.shawnkowalchuk.milo.core.time

import com.shawnkowalchuk.milo.core.text.Locale

/** The zone named [id], on whichever phone the test runs. */
expect fun testZone(id: String): ZoneId

/** The language of [tag] ("en-US"), on whichever phone the test runs. */
expect fun testLocale(tag: String): Locale
