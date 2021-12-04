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

package org.http4s.ember.core.h2

import cats.syntax.all._
private[h2] sealed abstract class H2Error(val value: Int) {
  def toGoAway(highest: Int): H2Frame.GoAway =
    H2Frame.GoAway(0, highest, value, None)
  def toRst(stream: Int): H2Frame.RstStream =
    H2Frame.RstStream(stream, value)

}
private[h2] object H2Error {

  def fromInt(int: Int): Option[H2Error] = int match {
    case NoError.value => NoError.some
    case ProtocolError.value => ProtocolError.some
    case InternalError.value => InternalError.some
    case FlowControlError.value => FlowControlError.some
    case SettingsTimeout.value => SettingsTimeout.some
    case StreamClosed.value => StreamClosed.some
    case FrameSizeError.value => FrameSizeError.some
    case RefusedStream.value => RefusedStream.some
    case Cancel.value => Cancel.some
    case CompressionError.value => CompressionError.some
    case ConnectError.value => ConnectError.some
    case EnhanceYourCalm.value => EnhanceYourCalm.some
    case InadequateSecurity.value => InadequateSecurity.some
    case Http_1_1_Required.value => Http_1_1_Required.some
    case _ => None
  }

  /*
    The associated condition is not a result of an
    error.  For example, a GOAWAY might include this code to indicate
    graceful shutdown of a connection.
   */
  case object NoError extends H2Error(0)

  /*
    The endpoint detected an unspecific protocol
    error.  This error is for use when a more specific error code is
    not available.
   */
  case object ProtocolError extends H2Error(0x1)

  /*
    The endpoint encountered an unexpected
    internal error.
   */
  case object InternalError extends H2Error(0x2)

  /*
    The endpoint detected that its peer
    violated the flow-control protocol.
   */
  case object FlowControlError extends H2Error(0x3)

  /*
    The endpoint sent a SETTINGS frame but did
    not receive a response in a timely manner.
   */
  case object SettingsTimeout extends H2Error(0x4)

  /*
    The endpoint received a frame after a stream
    was half-closed
   */
  case object StreamClosed extends H2Error(0x5)

  /*
    The endpoint received a frame with an
    invalid size
   */
  case object FrameSizeError extends H2Error(0x6)

  /*
    The endpoint refused the stream prior to
    performing any application processing
   */
  case object RefusedStream extends H2Error(0x7)

  /*
    Used by the endpoint to indicate that the stream is no
    longer needed.
   */
  case object Cancel extends H2Error(0x8)

  /*
    The endpoint is unable to maintain the
    header compression context for the connection.
   */
  case object CompressionError extends H2Error(0x9)

  /*
    The connection established in response to a
    CONNECT request (Section 8.3) was reset or abnormally closed.
   */
  case object ConnectError extends H2Error(0xa)

  /*
    The endpoint detected that its peer is
    exhibiting a behavior that might be generating excessive load.
   */
  case object EnhanceYourCalm extends H2Error(0xb)

  /*
    The underlying transport has properties
    that do not meet minimum security requirements (see Section 9.2).
   */
  case object InadequateSecurity extends H2Error(0xc)

  /*
    The endpoint requires that HTTP/1.1 be used
    instead of HTTP/2.
   */
  case object Http_1_1_Required extends H2Error(0xd)
}
