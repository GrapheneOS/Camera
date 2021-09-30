package com.hoko.ktblur.api

interface BlurResultDispatcher {

    suspend fun dispatch(block: () -> Unit)
}