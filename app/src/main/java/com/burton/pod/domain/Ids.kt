package com.burton.pod.domain

import java.security.MessageDigest

object Ids {
    fun podcast(feedUrl: String): String = sha256(feedUrl).take(20)
}

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
