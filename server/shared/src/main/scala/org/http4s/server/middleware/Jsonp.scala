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
package middleware

import cats.Applicative
import cats.data.Kleisli
import cats.syntax.all._
import fs2.Chunk
import fs2.Stream._
import org.http4s.headers._

import java.nio.charset.StandardCharsets

/** Middleware to support wrapping json responses in jsonp.
  *
  * Jsonp wrapping occurs when the request contains a parameter with the given name and
  * the request Content-Type is `application/json`.
  *
  * If the wrapping is done, the response Content-Type is changed into
  * `application/javascript` and the appropriate jsonp callback is
  * applied.
  */
object Jsonp {
  private val logger = Platform.loggerFactory.getLogger

  // A regex to match a valid javascript function name to shield the client from some jsonp related attacks
  private val ValidCallback =
    """^((?!(?:do|if|in|for|let|new|try|var|case|else|enum|eval|false|null|this|true|void|with|break|catch|class|const|super|throw|while|yield|delete|export|import|public|return|static|switch|typeof|default|extends|finally|package|private|continue|debugger|function|arguments|interface|protected|implements|instanceof)$)[$A-Z_a-z]*)$""".r
  def apply[F[_]: Applicative, G[_]](callbackParam: String)(http: Http[F, G]): Http[F, G] =
    Kleisli { req =>
      req.params.get(callbackParam) match {
        case Some(ValidCallback(callback)) =>
          http(req).map { response =>
            if (hasJsonContent(response)) jsonp(response, callback) else response
          }
        case Some(invalidCallback) =>
          logger
            .warn(s"Jsonp requested with invalid callback function name $invalidCallback")
            .unsafeRunSync()
          Response[G](Status.BadRequest).withEntity(s"Not a valid callback name.").pure[F]
        case None => http(req)
      }
    }

  private def hasJsonContent[F[_]](resp: Response[F]): Boolean =
    resp.contentType.map(_.mediaType).contains(MediaType.application.json)

  private def jsonp[F[_]](resp: Response[F], callback: String) = {
    val begin = beginJsonp(callback)
    val end = EndJsonp
    val jsonpBody = chunk(begin) ++ resp.body ++ chunk(end)
    val newLengthHeaderOption = resp.headers.get[`Content-Length`].flatMap { old =>
      old.modify(_ + begin.size + end.size)
    }
    resp
      .copy(body = jsonpBody)
      .transformHeaders(_ ++ Headers(newLengthHeaderOption))
      .withContentType(`Content-Type`(MediaType.application.javascript))
  }

  private def beginJsonp(callback: String) =
    Chunk.array((callback + "(").getBytes(StandardCharsets.UTF_8))

  private val EndJsonp =
    Chunk.array(");".getBytes(StandardCharsets.UTF_8))
}
