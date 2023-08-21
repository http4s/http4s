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
import cats.syntax.all._
import fs2.io.Writable
import org.typelevel.scalaccompat.annotation._

import scala.scalajs.js

/** Facade for [[https://nodejs.org/api/http.html#class-httpserverresponse]]
  */
@js.native
@nowarn212("cat=unused")
private[http4s] trait ServerResponse extends js.Object with Writable {

  protected[nodejs] def writeHead(
      statusCode: Int,
      statusMessage: String,
      headers: js.Array[String],
  ): ServerResponse = js.native

}

private[http4s] object ServerResponse {

  implicit def http4sNodeJsServerResponseOps(serverResponse: ServerResponse): ServerResponseOps =
    new ServerResponseOps(serverResponse)

  final class ServerResponseOps private[nodejs] (private val serverResponse: ServerResponse)
      extends AnyVal {

    /** Writes the contents of an http4s [[Response]] to this [[ServerResponse]] */
    def writeResponse[F[_]](response: Response[F])(implicit
        F: Async[F]
    ): F[Unit] =
      for {
        headers <- F.delay {
          val headers = new js.Array[String]
          response.headers.foreach { case Header.Raw(name, value) =>
            headers.push(name.toString, value)
            ()
          }
          headers
        }
        _ <- F.delay {
          serverResponse.writeHead(response.status.code, response.status.reason, headers)
        }
        _ <- response.body
          .through(fs2.io.writeWritable[F](F.pure(serverResponse)))
          .compile
          .drain
      } yield ()
  }

}
