package org.http4s

import org.http4s.headers.`Content-Type`
import org.log4s.getLogger

final case class Entity[+F[_]](
    body: EntityBody[F],
    mediaType: Option[MediaType] = None,
    charset: Option[Charset] = None,
    length: Option[Long] = None
) {
  length.foreach {
    case l if l < 0 => Entity.logger.warn(s"Attempt to provide a negative content length of $l")
    case _ => ()
  }

  def headers: Headers = {
    var hdrs: List[Header] = Nil
    mediaType.foreach(mt => hdrs = `Content-Type`(mt, charset) :: hdrs)
    length.foreach(l => hdrs = Header("Content-Length", l.toString) :: hdrs)
    Headers(hdrs)
  }
}

object Entity {
  private[http4s] val logger = getLogger

  def fromMessage[F[_]](msg: Message[F]): Entity[F] = {
    val (mediaType, charset) = headers.`Content-Type`.from(msg.headers) match {
      case None => None -> None
      case Some(ct) => Some(ct.mediaType) -> ct.charset
    }

    Entity(
      body = msg.body,
      mediaType = mediaType,
      charset = charset,
      length = headers.`Content-Length`.from(msg.headers).map(_.length)
    )
  }

  val empty: Entity[Nothing] = Entity[Nothing](
    body = EmptyBody,
    length = Some(0L)
  )
}
