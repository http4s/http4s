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

import scala.scalajs.js

object RuntimeInfo {
  val platform: String = "js"
  val runtime: String =
    if (js.typeOf(js.Dynamic.global.process) == "undefined") "JavaScript"
    else s"Node.js ${js.Dynamic.global.process.version.asInstanceOf[String]}"

  private[wire] def isConnectionReset(error: Throwable): Boolean = {
    def reset(current: Throwable): Boolean = current match {
      case js.JavaScriptException(value) if value != null && js.typeOf(value) == "object" =>
        val code = value.asInstanceOf[js.Dynamic].selectDynamic("code")
        js.typeOf(code) == "string" && code.asInstanceOf[String] == "ECONNRESET"
      case _ => false
    }
    @annotation.tailrec
    def loop(current: Throwable, remaining: Int): Boolean =
      if (current == null || remaining == 0) false
      else
        current match {
          // These operation markers are also barriers inside an outer exception chain.
          // Never descend into a write callback's native ECONNRESET cause.
          case _: WirePeerTransport.WriteFailure => false
          case read: WirePeerTransport.ReadFailure =>
            read.getSuppressed.isEmpty &&
            WirePeerTransport.nativeCode(read.nativeError, "code").contains("ECONNRESET") &&
            WirePeerTransport.nativeCode(read.nativeError, "syscall").contains("read")
          case _ if reset(current) => true
          case _ => loop(current.getCause, remaining - 1)
        }
    loop(error, 16)
  }

  // FS2 wraps Node's EPIPE once. Match that exact observed shape, not arbitrary causes.
  private[wire] def isClosedWrite(error: Throwable): Boolean = error match {
    case write: WirePeerTransport.WriteFailure if write.getSuppressed.isEmpty =>
      (WirePeerTransport.nativeCode(write.nativeError, "syscall").contains("write") &&
        WirePeerTransport
          .nativeCode(write.nativeError, "code")
          .exists(code => code == "EPIPE" || code == "ECONNRESET")) ||
      (WirePeerTransport.nativeCode(write.nativeError, "code").contains("ERR_SOCKET_CLOSED") &&
        js.isUndefined(write.nativeError.asInstanceOf[js.Dynamic].syscall) &&
        write.precedingReadReset.exists(isConnectionReset))
    case io: java.io.IOException
        if io.getClass.getName == "fs2.io.JavaScriptIOException" && io.getMessage == "Broken pipe" && io.getSuppressed.isEmpty =>
      io.getCause match {
        case cause @ js.JavaScriptException(value)
            if cause.getCause == null && cause.getSuppressed.isEmpty && value != null && js.typeOf(
              value
            ) == "object" =>
          val code = value.asInstanceOf[js.Dynamic].selectDynamic("code")
          js.typeOf(code) == "string" && code.asInstanceOf[String] == "EPIPE"
        case _ => false
      }
    case _ => false
  }

}
