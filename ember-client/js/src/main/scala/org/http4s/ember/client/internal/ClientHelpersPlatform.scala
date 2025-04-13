/*
 * Copyright 2019 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.ember.client.internal

import fs2.io.IOException

import scala.scalajs.js.JavaScriptException

private[client] trait ClientHelpersPlatform {

  private[this] val closedChannelIOExceptions =
    Set("Broken pipe", "Connection reset", "Connection reset by peer")

  private[this] val closedChannelJsExceptions =
    Set("ECONNRESET", "EPIPE")

  private[client] def isClosedChannelException(ex: Exception): Boolean = ex match {
    case jsEx: JavaScriptException =>
      closedChannelJsExceptions.exists(jsEx.getMessage.contains)
    case ioEx: IOException =>
      closedChannelIOExceptions.exists(ioEx.getMessage.contains)
    case _ =>
      false
  }
}
