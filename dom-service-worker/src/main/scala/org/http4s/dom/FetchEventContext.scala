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

import cats.data.OptionT
import cats.effect.kernel.Async
import cats.syntax.all._
import org.scalajs.dom.experimental.serviceworkers.FetchEvent
import org.scalajs.dom.experimental.{Response => DomResponse}

final class FetchEventContext[F[_]] private (
    val clientId: Option[String],
    val replacesClientId: Option[String],
    val resultingClientId: Option[String],
    val preloadResponse: F[Option[Response[F]]]
)

object FetchEventContext {
  private[dom] def apply[F[_]](_event: FetchEvent)(implicit F: Async[F]): FetchEventContext[F] = {
    val event = _event.asInstanceOf[scalajs.js.Dynamic] // Literally the entire facade is broken
    new FetchEventContext(
      event.clientId.asInstanceOf[scalajs.js.UndefOr[String]].toOption,
      event.replacesClientId.asInstanceOf[scalajs.js.UndefOr[String]].toOption,
      event.resultingClientId.asInstanceOf[scalajs.js.UndefOr[String]].toOption,
      OptionT(
        F.fromPromise(F.pure(
          event.preloadResponse.asInstanceOf[scalajs.js.Promise[scalajs.js.UndefOr[DomResponse]]]))
          .map(_.toOption)).semiflatMap(fromResponse[F]).value
    )
  }
}
