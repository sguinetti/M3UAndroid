package com.m3u.data.local.http

import kotlinx.coroutines.flow.Flow

interface HttpServer {
    fun start(port: Int): Flow<Unit>
}