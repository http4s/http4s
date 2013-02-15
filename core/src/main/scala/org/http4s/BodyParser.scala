package org.http4s

import scala.language.reflectiveCalls
import play.api.libs.iteratee._

object BodyParser {
  // TODO make configurable
  val DefaultMaxSize = 2 * 1024 * 1024

  def text(request: RequestPrelude, limit: Int = DefaultMaxSize)(f: String => Responder): Iteratee[HttpChunk, Responder] =
    consumeUpTo(RawConsumer, limit) { raw => f(new String(raw, request.charset)) }

  private val RawConsumer: Iteratee[Raw, Raw] = Iteratee.consume[Raw]()

  def consumeUpTo[A](consumer: Iteratee[Raw, A], limit: Int)(f: A => Responder): Iteratee[HttpChunk, Responder] =
    Enumeratee.map[HttpChunk](_.bytes) &>> (for {
      raw <- Traversable.takeUpTo[Raw](limit) &>> consumer
      tooLargeOrRaw <- Iteratee.eofOrElse(StatusLine.RequestEntityTooLarge())(raw)
    } yield (tooLargeOrRaw.right.map(f).merge))
}
