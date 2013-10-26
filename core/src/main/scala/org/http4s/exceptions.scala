package org.http4s

trait Http4sThrowable extends Throwable
abstract class Http4sException(message: String, cause: Exception = null) extends RuntimeException(message, cause) with Http4sThrowable
