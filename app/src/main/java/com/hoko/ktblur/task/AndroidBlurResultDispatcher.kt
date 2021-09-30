package com.hoko.ktblur.task

import android.os.Handler
import com.hoko.ktblur.api.BlurResultDispatcher
import com.hoko.ktblur.util.SingleMainHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class AndroidBlurResultDispatcher (handler: Handler) : BlurResultDispatcher {
    companion object {
        internal val MAIN_THREAD_DISPATCHER : BlurResultDispatcher by lazy { AndroidBlurResultDispatcher(SingleMainHandler.get()) }
    }

    //also handler.asCoroutineDispatcher()
    private val handlerCoroutineDispatcher = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            handler.post(block)
        }
    } + CoroutineName("blur-dispatcher")

    override suspend fun dispatch(block: () -> Unit) {
        withContext(handlerCoroutineDispatcher) {
            block()
        }
    }
}