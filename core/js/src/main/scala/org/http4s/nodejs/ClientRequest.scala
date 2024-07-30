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

import cats.effect.Async
import cats.effect.syntax.all._
import cats.syntax.all._
import fs2.io.Writable

import scala.scalajs.concurrent.QueueExecutionContext
import scala.scalajs.js

/** Facade for [[https://nodejs.org/api/http.html#class-httpclientrequest]]
  */
@js.native
trait ClientRequest extends js.Object with Writable {

  protected[nodejs] def setHeader(name: String, value: js.Array[String]): Unit = js.native

  protected[nodejs] def getHeader(name: String): js.UndefOr[js.Array[String]] = js.native

  protected[nodejs] def once[E](
      eventName: String,
      listener: js.Function1[E, Unit],
      dummy: Unit,
  ): ClientRequest =
    js.native

  protected[nodejs] def removeListener(
      eventName: String,
      listener: js.Function1[Nothing, Unit],
  ): ClientRequest =
    js.native
}

object ClientRequest {

  implicit def http4sNodeJsServerResponseOps(clientRequest: ClientRequest): ClientRequestOps =
    new ClientRequestOps(clientRequest)

  final class ClientRequestOps(private val clientRequest: ClientRequest) extends AnyVal {

    /** Writes the contents of an http4s [[Request]] to this [[ClientRequest]] */
    def writeRequest[F[_]](request: Request[F])(implicit
        F: Async[F]
    ): F[Unit] = {
      val write = for {
        _ <- F.delay {
          request.headers.foreach { case Header.Raw(name, value) =>
            clientRequest
              .getHeader(name.toString)
              .fold(clientRequest.setHeader(name.toString, js.Array(value))) { values =>
                values.push(value)
                ()
              }
          }
        }
        _ <- request.body
          .through(fs2.io.writeWritable[F](F.pure(clientRequest)))
          .compile
          .drain
      } yield ()

      val error = F.async[Unit] { cb =>
        val fn: js.Function1[js.Error, Unit] = e => cb(Left(js.JavaScriptException(e)))
        F.delay(clientRequest.once[js.Error]("error", fn, ()))
          .as(Some(F.delay {
            clientRequest.removeListener("error", fn)
            ()
          }))
      }

      // register the error listener with high-priority on the promises queue
      error.backgroundOn(QueueExecutionContext.promises()).use { error =>
        write.race(error.flatMap(_.embedNever)).void
      }
    }
  }

}
