package org.http4s.ember.client

import cats.effect._
import org.http4s._
import org.http4s.client._
import io.chrisdavenport.keypool._
import io.chrisdavenport.vault._
import fs2.io.tls._

final class EmberClient[F[_]: Bracket[?[_], Throwable]] private[client] (
    private val client: Client[F],
    private val pool: KeyPool[
      F,
      (RequestKey, Option[TLSParameters]),
      (RequestKeySocket[F], F[Unit])]
) extends DefaultClient[F] {

  /**
    * The reason for this extra class. This allows you to see the present state
    * of the underlying Pool, without having access to the pool itself.
    *
    * The first element represents total connections in the pool, the second
    * is a mapping between the number of connections in the pool for each requestKey.
    */
  def state: F[(Int, Map[(RequestKey, Option[TLSParameters]), Int])] = pool.state

  def run(req: Request[F]): Resource[F, Response[F]] = client.run(req)
}

object EmberClient {
  val TLSParameters: Key[TLSParameters] = Key.newKey[IO, TLSParameters].unsafeRunSync
}
