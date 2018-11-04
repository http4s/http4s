package org.http4s.client.middleware

import cats.effect.Sync
import org.http4s._
import org.http4s.client.Client

/**
  * Client middleware that sets the destination attribute of every request to the specified value.
  */
object DestinationAttribute {

  def apply[F[_]: Sync](client: Client[F], destination: String): Client[F] =
    Client { req =>
      client.run(req.withAttribute(Destination, destination))
    }

  /**
    * The returned function can be used as classifier function when creating the [[Metrics]] middleware, to use the destination
    * attribute from the request as classifier.
    *
    * @return the classifier function
    */
  def getDestination[F[_]](): Request[F] => Option[String] = _.attributes.get(Destination)

  val Destination = AttributeKey[String]

  val EmptyDestination = ""

}
