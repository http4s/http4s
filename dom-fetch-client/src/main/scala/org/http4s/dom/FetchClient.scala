/*
 * Copyright 2021 http4s.org
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
package dom

import cats.effect.Async
import cats.effect.Resource
import cats.syntax.all._
import cats.effect.syntax.all._
import java.util.concurrent.TimeoutException
import org.http4s.client.Client
import org.scalajs.dom.crypto._
import org.scalajs.dom.experimental.AbortController
import org.scalajs.dom.experimental.Fetch
import org.scalajs.dom.experimental.Headers
import org.scalajs.dom.experimental.HttpMethod
import org.scalajs.dom.experimental.ReferrerPolicy
import org.scalajs.dom.experimental.RequestCache
import org.scalajs.dom.experimental.RequestCredentials
import org.scalajs.dom.experimental.RequestInit
import org.scalajs.dom.experimental.RequestMode
import org.scalajs.dom.experimental.RequestRedirect
import org.scalajs.dom.experimental.{Response => FetchResponse}
import scala.concurrent.duration._

object FetchClient {

  private[dom] def makeClient[F[_]](
      requestTimeout: Duration,
      cache: Option[RequestCache],
      credentials: Option[RequestCredentials],
      integrity: Option[String],
      keepAlive: Option[Boolean],
      mode: Option[RequestMode],
      redirect: Option[RequestRedirect],
      referrer: Option[String],
      referrerPolicy: Option[ReferrerPolicy]
  )(implicit F: Async[F]): Client[F] = Client[F] { (req: Request[F]) =>
    Resource.eval(req.body.chunkAll.filter(_.nonEmpty).compile.last).flatMap { body =>
      Resource
        .makeCase {
          F.delay(new AbortController()).flatMap { abortController =>
            val init = new RequestInit {}

            init.method = req.method.name.asInstanceOf[HttpMethod]
            init.headers = new Headers(toDomHeaders(req.headers))
            body.foreach { body =>
              init.body = arrayBuffer2BufferSource(body.toJSArrayBuffer)
            }
            init.signal = abortController.signal
            cache.foreach(init.cache = _)
            credentials.foreach(init.credentials = _)
            integrity.foreach(init.integrity = _)
            keepAlive.foreach(init.keepalive = _)
            mode.foreach(init.mode = _)
            redirect.foreach(init.redirect = _)
            referrer.foreach(init.referrer = _)
            referrerPolicy.foreach(init.referrerPolicy = _)

            val fetch = F
              .fromPromise(F.delay(Fetch.fetch(req.uri.renderString, init)))
              .onCancel(F.delay(abortController.abort()))

            requestTimeout match {
              case d: FiniteDuration =>
                fetch.timeoutTo(
                  d,
                  F.raiseError[FetchResponse](new TimeoutException(
                    s"Request to ${req.uri.renderString} timed out after ${d.toMillis} ms"))
                )
              case _ =>
                fetch
            }
          }
        } { case (r, exitCase) =>
          closeReadableStream(r.body, exitCase)
        }
        .evalMap { response =>
          F.fromEither(Status.fromInt(response.status)).map { status =>
            Response[F](
              status = status,
              headers = fromDomHeaders(response.headers),
              body = readableStreamToStream(response.body)
            )
          }
        }
    }
  }

}
