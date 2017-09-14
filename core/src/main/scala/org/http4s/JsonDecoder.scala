package org.http4s

trait JsonDecoder[F[_], A] {

  def decodeJson(message: Message[F]): F[A]

}

object JsonDecoder {

  def apply[F[_], A](implicit jsonDecoder: JsonDecoder[F, A]): JsonDecoder[F, A] = jsonDecoder

}
