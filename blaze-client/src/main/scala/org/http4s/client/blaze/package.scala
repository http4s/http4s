package org.http4s.client

package object blaze {
  @deprecated("use org.http4s.blaze.client.BlazeClientBuilder", "0.22")
  type BlazeClientBuilder[F[_]] = org.http4s.blaze.client.BlazeClientBuilder[F]

  @deprecated("use org.http4s.blaze.client.BlazeClientBuilder", "0.22")
  val BlazeClientBuilder = org.http4s.blaze.client.BlazeClientBuilder
}
