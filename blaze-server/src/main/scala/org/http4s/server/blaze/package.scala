package org.http4s.server

package object blaze {
  @deprecated("use org.http4s.blaze.server.BlazeServerBuilder", "0.22")
  type BlazeServerBuilder[F[_]] = org.http4s.blaze.server.BlazeServerBuilder[F]

  @deprecated("use org.http4s.blaze.server.BlazeServerBuilder", "0.22")
  val BlazeServerBuilder = org.http4s.blaze.server.BlazeServerBuilder
}
