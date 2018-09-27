package org.http4s.client.metrics.core

import cats.effect.Sync
import org.http4s._
import org.http4s.client.Client

object DestinationAttribute {

  val Destination = AttributeKey[String]

  val EmptyDestination = ""

  /** The returned function can be used when creating the [[Metrics]] middleware, to extract destination from request attributes. */
  def getDestination[F[_]](default: String = EmptyDestination): Request[F] => String =
    _.attributes.get(Destination).getOrElse(default)

  /** Client middleware that sets the destination attribute of every request to the specified value. */
  def setRequestDestination[F[_]: Sync](client: Client[F], destination: String): Client[F] =
    Client { req =>
      client.run(req.withAttribute(Destination, destination))
    }
}
