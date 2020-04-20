package org.http4s

// import cats.MonadError
// import cats.implicits._
import fs2.Stream
import fs2.text.utf8Decode
import org.http4s.headers._
import org.http4s.util.decode

trait Media[M[_[_]]]{
  def body[F[_]](media: M[F]): EntityBody[F]
  def headers[F[_]](media: M[F]): Headers

  final def bodyAsText[F[_]](media: M[F])(implicit defaultCharset: Charset = DefaultCharset): Stream[F, String] =
    charset(media).getOrElse(defaultCharset) match {
      case Charset.`UTF-8` =>
        // suspect this one is more efficient, though this is superstition
        body(media).through(utf8Decode)
      case cs =>
        body(media).through(decode(cs))
    }

  final def contentType[F[_]](media: M[F]): Option[`Content-Type`] =
    headers(media).get(`Content-Type`)

  final def contentLength[F[_]](media: M[F]): Option[Long] =
    headers(media).get(`Content-Length`).map(_.length)

  final def charset[F[_]](media: M[F]): Option[Charset] =
    contentType(media).flatMap(_.charset)
}


object Media {
  def apply[M[_[_]]](implicit ev: Media[M]): ev.type = ev
}
