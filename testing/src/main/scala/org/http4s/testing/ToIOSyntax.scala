package org.http4s.testing

import cats.effect.{Effect, IO}
import cats.implicits._

trait ToIOSyntax {
  implicit def http4sToIoSyntax[F[_]: Effect, A](fa: F[A]) =
    new ToIOOps(fa)
}

class ToIOOps[F[_], A](val fa: F[A])(implicit val F: Effect[F]) {
  def toIO: IO[A] = {
    var result: Option[A] = None
    val read: IO[A] = IO(result.get)
    F.runAsync(fa) {
      case Left(e) => IO.raiseError(e)
      case Right(a) => IO({result = Some(a)})
    } >> read
  }
}
