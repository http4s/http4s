package org.http4s

import attributes.Scope

trait Http4sThrowable extends Throwable
abstract class Http4sException(message: String, cause: Exception = null) extends RuntimeException(message, cause) with Http4sThrowable
class KeyNotFoundException(key: String, scope: Scope) extends Http4sException(s"Can't find key $key in $scope scope")