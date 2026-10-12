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

import cats.effect.Resource
import com.comcast.ip4s.IpAddress
import com.comcast.ip4s.SocketAddress
import fs2.Stream
import scodec.bits.ByteVector

sealed abstract class Protocol(val id: String)
object Protocol {
  case object Http1 extends Protocol("h1")
  case object Http2 extends Protocol("h2")
}

sealed abstract class Role(val id: String)
object Role {
  case object Client extends Role("client")
  case object Server extends Role("server")
}

final case class Rfc(
    number: Int,
    section: String,
    requirement: String,
    strength: String = "MUST",
) {
  def url: String = {
    val kind = if (section.headOption.exists(_.isLetter)) "appendix" else "section"
    s"https://www.rfc-editor.org/rfc/rfc$number.html#$kind-$section"
  }
}

/** Application request observation/input. Server observations retain the HTTP/1 request-target
  * or HTTP/2 :path component (including the query); scheme and authority are separate in HTTP/2.
  */
final case class RequestData(
    method: String = "GET",
    path: String = "/",
    headers: Vector[(String, String)] = Vector.empty,
    body: ByteVector = ByteVector.empty,
)

final case class ResponseData[F[_]](
    status: Int,
    headers: Vector[(String, String)],
    body: Stream[F, Byte],
    trailers: F[Vector[(String, String)]],
)

sealed trait ServerScenario
object ServerScenario {
  final case class Reply(
      status: Int = 200,
      headers: Vector[(String, String)] = Vector.empty,
      body: ByteVector = ByteVector.encodeAscii("accepted").toOption.get,
  ) extends ServerScenario
  case object EchoBody extends ServerScenario
  case object EchoHeaders extends ServerScenario
  final case class ReplyChunks(chunks: Vector[ByteVector]) extends ServerScenario
}

final case class ServerHandle[F[_]](
    address: SocketAddress[IpAddress],
    received: F[RequestData],
    pollReceived: F[Option[RequestData]],
)

trait ClientDriver[F[_]] {
  def exchange(request: RequestData): Resource[F, ResponseData[F]]

  /** Build the upload inside its exchange-local cleanup boundary. Effect-owned finalizers
    * must use the supplied monitor (for example, `effect.onCancel(cleanup(release))`).
    * An adapter cannot recover cancellation cleanup failures hidden inside an opaque F.
    * FS2 scope finalizers remain owned and observed by the source compiler.
    */
  def exchangeStream(
      request: RequestData,
      body: FixtureCleanup[F] => Stream[F, Byte],
  ): Resource[F, ResponseData[F]]
}

/** A target interprets application operations only. All wire oracles live outside it. */
trait WireTarget[F[_]] {
  def name: String
  def version: String
  def settings(protocol: Protocol, role: Role): Vector[(String, String)] = {
    val _ = (protocol, role)
    Vector.empty
  }

  /** Bind case-local diagnostics. Adapters report fixture/cleanup faults independently of
    * native protocol outcomes; the callback must finish before their resources are released.
    */
  def withFailureObserver(observe: AdapterFailure => F[Unit]): WireTarget[F] = {
    val _ = observe
    this
  }
  def server(protocol: Protocol, scenario: ServerScenario): Resource[F, ServerHandle[F]]
  def client(
      protocol: Protocol,
      address: SocketAddress[IpAddress],
  ): Resource[F, ClientDriver[F]]
}
