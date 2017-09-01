package org.http4s

trait JsonDecoder[F[_], A] {

  def decodeJson(message: Message[F]): F[A]

}
