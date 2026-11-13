package com.shawnkowalchuk.milo.data

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * The folder the iPhone app keeps its files in: its Application Support folder, which iOS backs
 * up with the phone and never shows the user. Made the first time it is asked for.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun appFolderPath(): String {
    val folder = NSFileManager.defaultManager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )
    return checkNotNull(folder?.path) { "The app's Application Support folder could not be made" }
}
