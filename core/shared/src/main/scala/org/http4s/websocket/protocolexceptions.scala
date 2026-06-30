/*
 * Copyright 2013 http4s.org
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

package org.http4s
package websocket

import fs2.io.net.ProtocolException

final class ReservedOpcodeException(opcode: Int)
    extends ProtocolException(s"Opcode $opcode is reserved for future use as per RFC 6455")
    with scala.util.control.NoStackTrace

final class UnknownOpcodeException(opcode: Int)
    extends ProtocolException(
      s"RFC 6455 protocol violation, unknown websocket frame opcode: $opcode"
    )
    with scala.util.control.NoStackTrace
