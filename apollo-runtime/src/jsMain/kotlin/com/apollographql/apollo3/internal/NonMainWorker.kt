package com.apollographql.apollo3.internal

actual class NonMainWorker {
  actual suspend fun <R> doWork(block: () -> R): R {
    return block()
  }
}