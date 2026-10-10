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
      else if (reset(current)) true
      else loop(current.getCause, remaining - 1)
    loop(error, 16)
  }
}
