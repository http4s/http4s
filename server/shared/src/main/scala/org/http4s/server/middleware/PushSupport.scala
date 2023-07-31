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

import cats.Monad
import cats.data.Kleisli
import cats.effect.SyncIO
import cats.syntax.all._
import org.typelevel.vault._

@deprecated("Obsolete. Not implemented by any backends.", "0.23.12")
object PushSupport {
  private[this] val logger = Platform.loggerFactory.getLogger

  implicit def http4sPushOps[F[_]](response: Response[F]): PushOps[F] =
    new PushOps[F](response)

  final class PushOps[F[_]](response: Response[F]) extends AnyRef {
    def push(url: String, cascade: Boolean = true)(implicit req: Request[F]): Response[F] = {
      val newUrl = {
        val script = req.scriptName
        if (script.nonEmpty) {
          val sb = new StringBuilder()
          sb.append(script.toAbsolute)
          sb.append(url).result()
        } else url
      }

      logger.trace(s"Adding push resource: $newUrl").unsafeRunSync()

      val newPushResouces = response.attributes
        .lookup(pushLocationKey)
        .map(_ :+ PushLocation(newUrl, cascade))
        .getOrElse(Vector(PushLocation(newUrl, cascade)))

      response.copy(
        body = response.body,
        attributes = response.attributes.insert(PushSupport.pushLocationKey, newPushResouces),
      )
    }
  }

  private def collectResponse[F[_]](
      r: Vector[PushLocation],
      req: Request[F],
      verify: String => Boolean,
      routes: HttpRoutes[F],
  )(implicit F: Monad[F]): F[Vector[PushResponse[F]]] = {
    val emptyCollect: F[Vector[PushResponse[F]]] = F.pure(Vector.empty[PushResponse[F]])

    def fetchAndAdd(facc: F[Vector[PushResponse[F]]], v: PushLocation): F[Vector[PushResponse[F]]] =
      routes(req.withPathInfo(Uri.Path.unsafeFromString(v.location))).value.flatMap {
        case None => emptyCollect
        case Some(response) =>
          if (v.cascade) {
            val pr = PushResponse(v.location, response)
            response.attributes.lookup(pushLocationKey) match {
              case Some(pushed) => // Need to gather the sub resources
                val fsubs = collectResponse(pushed, req, verify, routes)
                F.map2(facc, fsubs)(_ ++ _ :+ pr)
              case None => facc.map(_ :+ pr)
            }
          } else {
            facc.map(_ :+ PushResponse(v.location, response))
          }
      }

    r.filter(x => verify(x.location)).foldLeft(emptyCollect)(fetchAndAdd)
  }

  /** Transform the route such that requests will gather pushed resources
    *
    * @param routes HttpRoutes to transform
    * @param verify method that determines if the location should be pushed
    * @return      Transformed route
    */
  def apply[F[_]: Monad](
      routes: HttpRoutes[F],
      verify: String => Boolean = _ => true,
  ): HttpRoutes[F] = {
    def gather(req: Request[F])(resp: Response[F]): Response[F] =
      resp.attributes
        .lookup(pushLocationKey)
        .map { fresource =>
          val collected = collectResponse(fresource, req, verify, routes)
          resp.copy(
            attributes = resp.attributes.insert(pushResponsesKey[F], collected)
          )
        }
        .getOrElse(resp)

    Kleisli(req => routes(req).map(gather(req)))
  }

  private[PushSupport] final case class PushLocation(location: String, cascade: Boolean)
  private[http4s] final case class PushResponse[F[_]](location: String, resp: Response[F])

  private[PushSupport] val pushLocationKey =
    Key.newKey[SyncIO, Vector[PushLocation]].unsafeRunSync()
  private[http4s] def pushResponsesKey[F[_]]: Key[F[Vector[PushResponse[F]]]] =
    Keys.PushResponses.asInstanceOf[Key[F[Vector[PushResponse[F]]]]]

  private[this] object Keys {
    val PushResponses: Key[Any] = Key.newKey[SyncIO, Any].unsafeRunSync()
  }
}
