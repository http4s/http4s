package com.example.http4s
package blaze

import cats.effect._
import com.example.http4s.ssl.SslExampleWithRedirect
import org.http4s.server.blaze.BlazeBuilder

class BlazeSslExampleWithRedirect(implicit timer: Timer[IO], ctx: ContextShift[IO])  extends SslExampleWithRedirect[IO] with IOApp {

  def builder: BlazeBuilder[IO] = BlazeBuilder[IO]

  override def run(args: List[String]): IO[ExitCode] = {
    sslStream.mergeHaltBoth(redirectStream).compile.toList.map(_.head)
  }
}
