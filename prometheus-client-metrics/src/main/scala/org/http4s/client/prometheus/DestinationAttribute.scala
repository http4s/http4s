package org.http4s.client.prometheus

import cats.data.Kleisli
import cats.effect.Sync
import org.http4s._
import org.http4s.client.Client

object DestinationAttribute {

  /** The value of this key in the request's attributes is used as the value for the destination metric label. */
  val Destination = AttributeKey[String]

  val EmptyDestination = ""

  /** The returned function can be used when creating the PrometheusClientMetrics middleware, to extract destination from request attributes. */
  def getDestination[F[_]](default: String = EmptyDestination): Request[F] => String =
    _.attributes.get(Destination).getOrElse(default)

  /** Client middleware that sets the destination attribute of every request to the specified value. */
  def setRequestDestination[F[_]: Sync](client: Client[F], destination: String): Client[F] =
    client.copy(open = Kleisli(r => client.open(r.withAttribute(Destination, destination))))

}
