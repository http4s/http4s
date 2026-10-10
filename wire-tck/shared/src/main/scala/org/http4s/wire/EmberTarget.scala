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

import cats.effect.Async
import cats.effect.Resource
import cats.effect.std.Queue
import cats.syntax.all._
import com.comcast.ip4s._
import fs2.Chunk
import fs2.Stream
import fs2.io.net.Network
import org.http4s.Header
import org.http4s.Headers
import org.http4s.HttpApp
import org.http4s.Method
import org.http4s.Request
import org.http4s.Response
import org.http4s.Status
import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.h2.H2Keys.Http2PriorKnowledge
import org.typelevel.ci.CIString
import scodec.bits.ByteVector

import scala.concurrent.duration._

/** Interprets application operations through Ember's public client and server APIs. */
final class EmberTarget[F[_]: Async: Network] extends WireTarget[F] {
  val name: String = "ember"
  val version: String = org.http4s.BuildInfo.version

  def server(protocol: Protocol, scenario: ServerScenario): Resource[F, ServerHandle[F]] =
    Resource.eval(Queue.unbounded[F, RequestData]).flatMap { received =>
      val builder = EmberServerBuilder
        .default[F]
        .withHost(ip"127.0.0.1")
        .withPort(port"0")
        .withRequestHeaderReceiveTimeout(15.seconds)
        .withIdleTimeout(20.seconds)
        .withShutdownTimeout(Duration.Zero)
        .withHttpApp(HttpApp[F](request => serve(protocol, request, scenario, received)))

      (protocol match {
        case Protocol.Http1 => builder.withoutHttp2
        case Protocol.Http2 => builder.withHttp2
      }).build.map(server => ServerHandle(server.addressIp4s, received.take, received.tryTake))
    }

  def client(
      protocol: Protocol,
      address: SocketAddress[IpAddress],
  ): Resource[F, ClientDriver[F]] = {
    val builder = EmberClientBuilder
      .default[F]
      .withTimeout(15.seconds)
      .withIdleConnectionTime(20.seconds)

    (protocol match {
      case Protocol.Http1 => builder.withoutHttp2
      case Protocol.Http2 => builder.withHttp2
    }).build.map { underlying =>
      new ClientDriver[F] {
        def exchange(request: RequestData): Resource[F, ResponseData[F]] =
          exchangeStream(request, bytes(request.body))

        def exchangeStream(
            request: RequestData,
            body: Stream[F, Byte],
        ): Resource[F, ResponseData[F]] =
          Resource
            .eval(toRequest(protocol, address, request, body))
            .flatMap(underlying.run)
            .map(response =>
              ResponseData(
                status = response.status.code,
                headers = fromHeaders(response.headers),
                body = response.body,
                trailers = response.trailerHeaders.map(fromHeaders),
              )
            )
      }
    }
  }

  private def toRequest(
      protocol: Protocol,
      address: SocketAddress[IpAddress],
      request: RequestData,
      body: Stream[F, Byte],
  ): F[Request[F]] =
    (Method.fromString(request.method).liftTo[F], Uri.fromString(request.path).liftTo[F])
      .mapN { (method, path) =>
        val uri = path.copy(
          scheme = Some(Uri.Scheme.http),
          authority = Some(
            Uri.Authority(
              host = Uri.Host.fromIp4sHost(address.host),
              port = Some(address.port.value),
            )
          ),
        )
        val result = Request[F](method = method, uri = uri, headers = toHeaders(request.headers))
          .withBodyStream(body)
        protocol match {
          case Protocol.Http1 => result
          case Protocol.Http2 => result.withAttribute(Http2PriorKnowledge, ())
        }
      }

  private def serve(
      protocol: Protocol,
      request: Request[F],
      scenario: ServerScenario,
      received: Queue[F, RequestData],
  ): F[Response[F]] = {
    val head = RequestData(
      method = request.method.name,
      // HTTP/2 carries scheme and authority separately from :path. Observe that
      // component without rewriting its path/query bytes or inventing a default.
      path = (protocol match {
        case Protocol.Http1 => request.uri
        case Protocol.Http2 => request.uri.copy(scheme = None, authority = None)
      }).renderString,
      headers = fromHeaders(request.headers),
    )

    // Head-only observations remain available even when a peer never finishes its upload.
    def reply(response: F[Response[F]]): F[Response[F]] = received.offer(head) *> response

    scenario match {
      case ServerScenario.EchoBody =>
        // Only the body-consuming scenario waits for the body before publishing its observation.
        request.body.take(EmberTarget.MaxBodyBytes + 1L).compile.to(Chunk).flatMap { body =>
          if (body.size > EmberTarget.MaxBodyBytes)
            Async[F].raiseError(new IllegalArgumentException("Wire fixture body exceeds 1 MiB"))
          else
            received
              .offer(head.copy(body = body.toByteVector))
              .as(
                Response[F]().withBodyStream(Stream.chunk(body))
              )
        }
      case ServerScenario.Reply(status, headers, body) =>
        reply(Status.fromInt(status).liftTo[F].map { code =>
          Response[F](status = code, headers = toHeaders(headers)).withBodyStream(bytes(body))
        })
      case ServerScenario.EchoHeaders =>
        val rendered = head.headers.map { case (name, value) => s"$name: $value\n" }.mkString
        reply(
          Async[F].pure(
            Response[F]().withBodyStream(Stream.emit(rendered).through(fs2.text.utf8.encode))
          )
        )
      case ServerScenario.ReplyChunks(chunks) =>
        reply(Async[F].pure(Response[F]().withBodyStream(Stream.emits(chunks).flatMap(bytes))))
    }
  }

  private def bytes(value: ByteVector): Stream[F, Byte] =
    Stream.chunk(Chunk.byteVector(value))

  private def fromHeaders(headers: Headers): Vector[(String, String)] =
    headers.headers.iterator.map(header => header.name.toString -> header.value).toVector

  private def toHeaders(headers: Vector[(String, String)]): Headers =
    Headers(headers.map { case (name, value) => Header.Raw(CIString(name), value) })
}

object EmberTarget {
  private val MaxBodyBytes = 1024 * 1024

  def apply[F[_]: Async: Network]: EmberTarget[F] = new EmberTarget[F]
}
