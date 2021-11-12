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
package server

import cats.effect._
import cats.syntax.all._
import fs2._
import fs2.concurrent.Signal
import fs2.concurrent.SignallingRef
import org.http4s.internal.BackendBuilder

import java.net.InetSocketAddress
import scala.collection.immutable

trait ServerBuilder[F[_]] extends BackendBuilder[F, Server] {
  type Self <: ServerBuilder[F]

  implicit protected def F: Concurrent[F]

  def bindSocketAddress(socketAddress: InetSocketAddress): Self

  final def bindHttp(port: Int = defaults.HttpPort, host: String = defaults.IPv4Host): Self =
    bindSocketAddress(InetSocketAddress.createUnresolved(host, port))

  final def bindLocal(port: Int): Self = bindHttp(port, defaults.IPv4Host)

  final def bindAny(host: String = defaults.IPv4Host): Self = bindHttp(0, host)

  /** Sets the handler for errors thrown invoking the service.  Is not
    * guaranteed to be invoked on errors on the server backend, such as
    * parsing a request or handling a context timeout.
    */
  def withServiceErrorHandler(
      serviceErrorHandler: Request[F] => PartialFunction[Throwable, F[Response[F]]]
  ): Self

  /** Returns a Server resource.  The resource is not acquired until the
    * server is started and ready to accept requests.
    */
  def resource: Resource[F, Server]

  /** Runs the server as a process that never emits.  Useful for a server
    * that runs for the rest of the JVM's life.
    */
  final def serve: Stream[F, ExitCode] =
    for {
      signal <- Stream.eval(SignallingRef[F, Boolean](false))
      exitCode <- Stream.eval(F.ref(ExitCode.Success))
      serve <- serveWhile(signal, exitCode)
    } yield serve

  /** Runs the server as a Stream that emits only when the terminated signal becomes true.
    * Useful for servers with associated lifetime behaviors.
    */
  final def serveWhile(
      terminateWhenTrue: Signal[F, Boolean],
      exitWith: Ref[F, ExitCode],
  ): Stream[F, ExitCode] =
    Stream.resource(resource) *> (terminateWhenTrue.discrete
      .takeWhile(_ === false)
      .drain ++ Stream.eval(exitWith.get))

  /** Set the banner to display when the server starts up */
  def withBanner(banner: immutable.Seq[String]): Self

  /** Disable the banner when the server starts up */
  final def withoutBanner: Self = withBanner(immutable.Seq.empty)
}

object SSLKeyStoreSupport {
  final case class StoreInfo(path: String, password: String)
}
