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
package nodejs

import cats.effect.kernel.Async
import cats.syntax.all._
import fs2.Stream
import fs2.io.Readable
import org.typelevel.ci.CIString

import scala.scalajs.js

/** Facade for [[https://nodejs.org/api/http.html#class-httpincomingmessage]]
  */
@js.native
trait IncomingMessage extends js.Object with Readable {
  protected[nodejs] def httpVersionMajor: Int = js.native
  protected[nodejs] def httpVersionMinor: Int = js.native
  protected[nodejs] def rawHeaders: js.Array[String] = js.native

  protected[nodejs] def method: String = js.native
  protected[nodejs] def url: String = js.native

  protected[nodejs] def statusCode: Int = js.native
}

object IncomingMessage {

  implicit def http4sNodeJsIncomingMessageOps(
      incomingMessage: IncomingMessage
  ): IncomingMessageOps =
    new IncomingMessageOps(incomingMessage)

  final class IncomingMessageOps private[nodejs] (private val incomingMessage: IncomingMessage)
      extends AnyVal {

    def toRequest[F[_]](implicit
        F: Async[F]
    ): F[Request[F]] = for {
      method <- Method.fromString(incomingMessage.method).liftTo[F]
      uri <- Uri.fromString(incomingMessage.url).liftTo[F]
      httpVersion <- parseHttpVersion
    } yield Request(method, uri, httpVersion, headers, body)

    def toResponse[F[_]](implicit
        F: Async[F]
    ): F[Response[F]] = for {
      status <- Status.fromInt(incomingMessage.statusCode).liftTo[F]
      httpVersion <- parseHttpVersion
    } yield Response(status, httpVersion, headers, body)

    private def parseHttpVersion[F[_]: Async] = HttpVersion
      .fromVersion(
        incomingMessage.httpVersionMajor,
        incomingMessage.httpVersionMinor,
      )
      .liftTo[F]

    private def headers = {
      val rawHeaders = incomingMessage.rawHeaders
      val n = rawHeaders.length
      var i = 0
      val headers = List.newBuilder[Header.Raw]
      while (i < n) {
        headers += Header.Raw(CIString(rawHeaders(i)), rawHeaders(i + 1))
        i += 2
      }
      new Headers(headers.result())
    }

    private def body[F[_]: Async] =
      Stream.resource(fs2.io.suspendReadableAndRead()(incomingMessage)).flatMap(_._2)

  }

}
