package org.http4s

import cats.Functor

trait EntDecoder[F[_], T]{
  def decode[M[_[_]]](m: M[F], strict: Boolean)(implicit mess: Mess[M, F]): DecodeResult[F, T]
  def consumes: Set[MediaRange]

  def matchesMediaType(mediaType: MediaType): Boolean =
    consumes.exists(_.satisfiedBy(mediaType))
}

object EntDecoder {

  implicit def entDecoderFunctor[F[_]] : Functor[EntDecoder[F, ?]] = new Functor[EntDecoder[F, ?]]{
    override def map[A, B](fa: EntDecoder[F, A])(f: A => B): EntDecoder[F, B] = new EntDecoder[F, B] {
      override def consumes: Set[MediaRange] = fa.consumes
      override def decode[M[_[_]]](m: M[F], strict: Boolean)(implicit mess: Mess[M, F]): DecodeResult[F, B] =
        fa.decode(m, strict).map(f)
    }
  }

}
