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
import org.http4s.client.Client
import org.scalajs.dom.crypto._
import org.scalajs.dom.experimental.Fetch
import org.scalajs.dom.experimental.Headers
import org.scalajs.dom.experimental.HttpMethod
import org.scalajs.dom.experimental.RequestInit

object FetchClient {

  def apply[F[_]](implicit F: Async[F]): Client[F] = Client[F] { req: Request[F] =>
    Resource.eval(req.body.chunkAll.filter(_.nonEmpty).compile.last).flatMap { body =>
      Resource
        .makeCase {
          F.fromPromise {
            F.delay {
              val init = new RequestInit {}

              init.method = req.method.name.asInstanceOf[HttpMethod]
              init.headers = new Headers(toDomHeaders(req.headers))
              body.foreach { body =>
                init.body = arrayBuffer2BufferSource(body.toJSArrayBuffer)
              }

              Fetch.fetch(req.uri.renderString, init)
            }
          }
        } { case (r, exitCase) =>
          closeReadableStream(r.body, exitCase)
        }
        .evalMap(fromResponse[F])

    }
  }

}
