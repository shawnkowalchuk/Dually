package com.shawnkowalchuk.milo.core.time

import com.shawnkowalchuk.milo.core.text.Locale

actual fun testZone(id: String): ZoneId = zoneIdOf(id)

actual fun testLocale(tag: String): Locale = Locale(tag.replace('-', '_'))
