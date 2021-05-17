package com.apollographql.apollo3.cache.normalized.internal

actual class Guard<R: Any> actual constructor(name: String, producer: () -> R) {
  private val resource = producer()

  actual fun <T> access(block: (R) -> T): T {
    return block(resource)

  }

  actual fun writeAndForget(block: (R) -> Unit) {
      block(resource)
  }
}