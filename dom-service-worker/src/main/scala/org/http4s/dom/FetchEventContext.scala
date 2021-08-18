package org.http4s
package dom

import cats.data.OptionT
import cats.effect.kernel.Async
import cats.syntax.all._
import org.scalajs.dom.experimental.serviceworkers.FetchEvent
import org.scalajs.dom.experimental.{Response => DomResponse}

final case class FetchEventContext[F[_]](
    clientId: String,
    replacesClientId: String,
    resultingClientId: String,
    preloadResponse: F[Option[Response[F]]]
)

object FetchEventContext {
  private[dom] def apply[F[_]](event: FetchEvent)(implicit F: Async[F]): FetchEventContext[F] =
    FetchEventContext(
      event.clientId,
      event.replacesClientId,
      event.resultingClientId,
      OptionT(
        F.fromPromise(F.pure(
          // Another incorrectly typed facade :(
          event.preloadResponse.asInstanceOf[scalajs.js.Promise[scalajs.js.UndefOr[DomResponse]]]))
          .map(_.toOption)).semiflatMap(fromResponse[F]).value
    )
}
