package com.tamarilog.codexblanche.network

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * [ApiFailureReport] が付いていれば、その表示行のみ（サーバー応答由来の事実）。
 * それ以外は JDK / OkHttp が返したメッセージや例外名のみ（憶測で文意を付けない）。
 */
fun formatApiErrorForUser(error: Throwable): String {
    error.apiFailureCause()?.displayLines?.joinToString("\n")?.trim()?.takeIf { it.isNotEmpty() }
        ?.let { return it }

    return when (error) {
        is SocketTimeoutException,
        is UnknownHostException,
        -> error.toFactualBrief()
        is IOException ->
            error.toFactualBrief()
        else -> {
            error.message?.trim()?.takeIf { it.isNotEmpty() }
                ?: error.javaClass.simpleName
        }
    }
}

private fun Throwable.toFactualBrief(): String = buildString {
    append(javaClass.name)
    message?.trim()?.takeIf { it.isNotEmpty() }?.let { msg ->
        append('\n').append(msg)
    }
}.trim()
