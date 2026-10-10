/*
 * Copyright 2026 http4s.org
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

package org.http4s.wire

import java.net.SocketException

import scala.scalanative.meta.LinktimeInfo

object RuntimeInfo {
  val platform: String = "native"
  val runtime: String = s"Scala Native ${LinktimeInfo.runtimeVersion}"

  private[wire] def isConnectionReset(error: Throwable): Boolean = {
    @annotation.tailrec
    def loop(current: Throwable, remaining: Int): Boolean =
      if (current == null || remaining == 0) false
      else
        current match {
          case socket: SocketException
              if socket.getMessage == "Connection reset" ||
                socket.getMessage == "Connection reset by peer" =>
            true
          case _ => loop(current.getCause, remaining - 1)
        }
    loop(error, 16)
  }
}
