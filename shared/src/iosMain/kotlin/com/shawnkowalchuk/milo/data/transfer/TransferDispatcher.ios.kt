package com.shawnkowalchuk.milo.data.transfer

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal actual val transferDispatcher: CoroutineDispatcher = Dispatchers.IO
