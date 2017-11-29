package org.http4s

import cats.Contravariant
import org.http4s.headers.`Content-Type`

trait EntEncoder[F[_], A] { self =>
  def headers: Headers
  def toEntity(a: A): F[Entity[F]]

  /** Get the [[org.http4s.headers.Content-Type]] of the body encoded by this [[EntityEncoder]], if defined the headers */
  def contentType: Option[`Content-Type`] = headers.get(`Content-Type`)

  /** Get the [[Charset]] of the body encoded by this [[EntityEncoder]], if defined the headers */
  def charset: Option[Charset] = headers.get(`Content-Type`).flatMap(_.charset)

  /** Generate a new EntityEncoder that will contain the `Content-Type` header */
  def withContentType(tpe: `Content-Type`): EntityEncoder[F, A] = new EntityEncoder[F, A] {
    override def toEntity(a: A): F[Entity[F]] = self.toEntity(a)
    override val headers: Headers = self.headers.put(tpe)
  }
}

object EntEncoder {

  implicit def entEncoderContravariant[F[_]] : Contravariant[EntEncoder[F, ?]] = new Contravariant[EntEncoder[F, ?]] {
    override def contramap[A, B](fa: EntEncoder[F, A])(f: B => A): EntEncoder[F, B] = new EntEncoder[F, B] {
      override def headers: Headers = fa.headers
      override def toEntity(a: B): F[Entity[F]] = fa.toEntity(f(a))
    }
  }


}
