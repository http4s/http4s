/*
 * Copyright 2014 http4s.org
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
package client

import cats.effect.kernel.Async
import cats.effect.kernel.Resource
import cats.syntax.all._
import org.http4s.internal.BackendBuilder
import org.http4s.nodejs.ClientRequest
import org.http4s.nodejs.IncomingMessage
import org.typelevel.scalaccompat.annotation._

import scala.scalajs.js
import scala.scalajs.js.JSConverters._

import NodeJSClientBuilder._

/** Builder for a [[Client]] backed by Node.js [[https://nodejs.org/api/http.html#httprequestoptions-callback http.request]].
  *
  * The Node.js client adds no dependencies beyond `http4s-client`.
  * This client is currently not production grade, but convenient for
  * experimentation and exploration.
  *
  * @define WHYNOSHUTDOWN Creation of the client allocates no
  * resources, and any resources allocated while using this client
  * are reclaimed by the Node.js runtime at its own leisure.
  */
private[client] sealed abstract class NodeJSClientBuilder[F[_]](implicit protected val F: Async[F])
    extends BackendBuilder[F, Client[F]] {

  /** Creates a [[Client]].
    *
    * The shutdown of this client is a no-op. $WHYNOSHUTDOWN
    */
  def create: Client[F] = Client { (req: Request[F]) =>
    Resource.make(F.delay(new AbortController))(ctr => F.delay(ctr.abort())).evalMap { abort =>
      F.async[IncomingMessage] { cb =>
        val options = new RequestOptions {
          method = req.method.renderString
          protocol = req.uri.scheme.map(_.value + ":").orUndefined
          host = req.uri.authority.map { authority =>
            authority.host match {
              case Uri.RegName(n) => n.toString
              case Uri.Ipv4Address(ip) => ip.toString
              case Uri.Ipv6Address(ip) => ip.toString
            }
          }.orUndefined
          port = req.uri.authority.flatMap(_.port).map(_.toInt).orUndefined
          path = req.uri.copy(scheme = None, authority = None).renderString
          signal = abort.signal
        }

        val clientRequest =
          if (req.uri.scheme.contains(Uri.Scheme.https))
            F.delay(httpsRequest(options, msg => cb(Right(msg))))
          else
            F.delay(httpRequest(options, msg => cb(Right(msg))))

        clientRequest
          .flatMap(_.writeRequest(req))
          .as(Some(F.unit)) // cancelation guaranteed by abort controller
      }.flatMap(_.toResponse)
    }
  }

  def resource: Resource[F, Client[F]] =
    Resource.pure(create)

}

private[client] object NodeJSClientBuilder {
  def apply[F[_]: Async]: NodeJSClientBuilder[F] = new NodeJSClientBuilder[F] {}

  @js.native
  @js.annotation.JSImport("http", "request")
  @nowarn212("cat=unused")
  private def httpRequest(
      options: RequestOptions,
      cb: js.Function1[IncomingMessage, Unit],
  ): ClientRequest = js.native

  @js.native
  @js.annotation.JSImport("https", "request")
  @nowarn212("cat=unused")
  private def httpsRequest(
      options: RequestOptions,
      cb: js.Function1[IncomingMessage, Unit],
  ): ClientRequest = js.native

  private trait RequestOptions extends js.Object {
    var method: js.UndefOr[String] = js.undefined
    var protocol: js.UndefOr[String] = js.undefined
    var host: js.UndefOr[String] = js.undefined
    var port: js.UndefOr[Int] = js.undefined
    var path: js.UndefOr[String] = js.undefined
    var signal: js.UndefOr[AbortSignal] = js.undefined
  }

  @js.native
  @js.annotation.JSGlobal("AbortController")
  private class AbortController extends js.Object {
    def abort(): Unit = js.native
    def signal: AbortSignal = js.native
  }

  private trait AbortSignal extends js.Object

}
