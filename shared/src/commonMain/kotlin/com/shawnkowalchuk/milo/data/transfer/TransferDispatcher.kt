package com.shawnkowalchuk.milo.data.transfer

import kotlinx.coroutines.CoroutineDispatcher

/** Where an export is written and an import read: the phone's pool for files, off the main thread. */
internal expect val transferDispatcher: CoroutineDispatcher
