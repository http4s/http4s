package org.http4s

import play.api.libs.iteratee._

object BodyParser {
  // TODO make configurable
  val DefaultMaxSize = 2 * 1024 * 1024

  def text(request: RequestPrelude, limit: Int = DefaultMaxSize)(f: String => Responder): Iteratee[HttpChunk, Responder] =
    tooLargeOrHandleRaw(limit) { raw => f(new String(raw, request.charset)) }

  private val RawConsumer: Iteratee[Raw, Raw] = Iteratee.consume[Raw]()

  def tooLargeOrHandleRaw(limit: Int)(f: Raw => Responder): Iteratee[HttpChunk, Responder] =
    for {
      raw <- Enumeratee.map[HttpChunk](_.bytes) ><> Traversable.takeUpTo(limit) &>> RawConsumer
      tooLargeOrRaw <- Iteratee.eofOrElse(StatusLine.RequestEntityTooLarge())(raw)
    } yield (tooLargeOrRaw.right.map(f).merge)
}
