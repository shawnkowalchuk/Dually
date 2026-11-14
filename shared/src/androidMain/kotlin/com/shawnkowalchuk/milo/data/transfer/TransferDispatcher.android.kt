package com.shawnkowalchuk.milo.data.transfer

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

internal actual val transferDispatcher: CoroutineDispatcher = Dispatchers.IO
