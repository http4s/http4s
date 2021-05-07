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

import cats.effect.concurrent._
import cats.effect._
import cats.syntax.all._
import fs2._
import fs2.concurrent._

/** A pseudo-typeclass for something which can build a server.
  *
  * There are primary three motivations for this class.
  *
  * - Provide an ad-hoc polymorphic way approach to configure a server, as
  *   opposed to the [[ServerBuilder]] subtyping approach. This is generally
  *   more consistent with the current design idioms in use in FP Scala code
  *   and (subjectively) is cleaner, e.g. no dealing with `Self` typing, etc.
  * - Provide a minimal definition for what you need to provide if you are
  *   implementing a server backend. [[ServerBuilder]] already does this to a
  *   degree, but due to the current design of that class, not all of our
  *   servers implement that, e.g. Ember.
  *
  * This class does not and ''can not'' replace backend specific
  * builders. Those must always exist as the precise feature set of a given
  * backend will likely never be totally shared with any other backend.
  */
trait ServerBuildable[F[_], A] {

  /** Returns the backend as a resource.  Resource acquire waits
    * until the backend is ready to process requests.
    */
  def resource(a: A): Resource[F, Server]

  // Abstract Settings

  /** Set the http host for the server. */
  def withHttpHost(a: A)(host: String): A

  /** Set the http port for the server. */
  def withHttpPort(a: A)(port: Int): A

  /** Set the [[HttpApp]] to serve on this server. */
  def withHttpApp(a: A)(app: HttpApp[F]): A

  // final

  /** Returns the backend as a single-element stream.  The stream
    * does not emit until the backend is ready to process requests.
    * The backend is shut down when the stream is finalized.
    */
  final def stream(a: A): Stream[F, Server] = Stream.resource(resource(a))

  /** Returns an effect that allocates a backend and an `F[Unit]` to
    * release it.  The returned `F` waits until the backend is ready
    * to process requests.  The second element of the tuple shuts
    * down the backend when run.
    *
    * Unlike [[resource]] and [[stream]], there is no automatic
    * release of the backend.  This function is intended for REPL
    * sessions, tests, and other situations where composing a
    * [[cats.effect.Resource]] or [[fs2.Stream]] is not tenable.
    * [[resource]] or [[stream]] is recommended wherever possible.
    */
  final def allocated(a: A)(implicit F: Bracket[F, Throwable]): F[(Server, F[Unit])] = resource(
    a).allocated

  /** Bind the server to a given port and host. */
  final def bindHttp(a: A)(port: Int = defaults.HttpPort, host: String = defaults.Host): A =
    withHttpPort(withHttpHost(a)(host))(port)

  /** Bind the server to localhost */
  final def bindLocal(a: A)(port: Int): A = bindHttp(a)(port, defaults.Host)

  /** Bind the server to localhost, on some dynamically determined free port. */
  final def bindAny(a: A)(host: String = defaults.Host): A = bindHttp(a)(0, host)

  /** Runs the server as a Stream that emits only when the terminated signal becomes true.
    * Useful for servers with associated lifetime behaviors.
    */
  final def serveWhile(a: A)(
      terminateWhenTrue: Signal[F, Boolean],
      exitWith: Ref[F, ExitCode]): Stream[F, ExitCode] =
    Stream.resource(resource(a)) *> (terminateWhenTrue.discrete
      .takeWhile(_ === false)
      .drain ++ Stream.eval(exitWith.get))

  /** Runs the server as a process that never emits.  Useful for a server
    * that runs for the rest of the JVM's life.
    */
  final def serve(a: A)(implicit F: Concurrent[F]): Stream[F, ExitCode] =
    for {
      signal <- Stream.eval(SignallingRef[F, Boolean](false))
      exitCode <- Stream.eval(Ref[F].of(ExitCode.Success))
      serve <- serveWhile(a)(signal, exitCode)
    } yield serve
}

object ServerBuildable {

  // TODO: Generate syntax with simulacrum? It's a two parameter class so I'm
  // not certain that is viable.

  def apply[F[_], A](implicit instance: ServerBuildable[F, A]): ServerBuildable[F, A] = instance

  trait Ops[F[_], A] extends Serializable {
    def typeClassInstance: ServerBuildable[F, A]
    def resource: Resource[F, Server]
    def withHttpHost(host: String): A
    def withHttpPort(port: Int): A
    def withHttpApp(app: HttpApp[F]): A
  }

  trait ToServerBuildableOps extends Serializable {
    implicit def toServerBuildableOps[F[_], A](target: A)(implicit
        tc: ServerBuildable[F, A]): Ops[F, A] =
      new Ops[F, A] {
        override val typeClassInstance: ServerBuildable[F, A] = tc
        override def resource: Resource[F, Server] =
          tc.resource(target)
        override def withHttpHost(host: String): A = tc.withHttpHost(target)(host)
        override def withHttpPort(port: Int): A = tc.withHttpPort(target)(port)
        override def withHttpApp(app: HttpApp[F]): A = tc.withHttpApp(target)(app)
      }
  }
}
