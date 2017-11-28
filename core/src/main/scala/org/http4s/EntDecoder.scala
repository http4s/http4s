package org.http4s

//import cats.Functor

trait EntDecoder[F[_], T]{
  def decoder[M[_[_]]](m: M[F], strict: Boolean)(implicit mess: Mess[M, F]): DecodeResult[F, T]
  def consumes: Set[MediaRange]
}
/*
object EntDecoder {
  implicit def entDecoderFunctor[F[_]]: Functor[EntDecoder[F, ?]] = new Functor[EntDecoder[F, ?]] = {
    def map[A, B](fa: EntDecoder[F, A])(f: A => B): EntDecoder[F, B] = ???
  }
}
*/