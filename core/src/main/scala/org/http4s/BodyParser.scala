package org.http4s

import play.api.libs.iteratee._
import java.io.{File, PrintStream, FileInputStream, FileOutputStream}

object BodyParser {
  // TODO make configurable
  val DefaultMaxSize = 2 * 1024 * 1024
  private val RawConsumer: Iteratee[Raw, Raw] = Iteratee.consume[Raw]()

  def text(request: RequestPrelude, limit: Int = DefaultMaxSize)(f: String => Responder): Iteratee[HttpChunk, Responder] =
    tooLargeOrHandleRaw(limit) { raw => f(new String(raw, request.charset)) }

  def tooLargeOrHandleRaw(limit: Int)(f: Raw => Responder): Iteratee[HttpChunk, Responder] =
    for {
      raw <- Enumeratee.map[HttpChunk](_.bytes) ><> Traversable.takeUpTo(limit) &>> RawConsumer
      tooLargeOrRaw <- Iteratee.eofOrElse(StatusLine.RequestEntityTooLarge())(raw)
    } yield (tooLargeOrRaw.right.map(f).merge)

  def binFile(in: java.io.File)(f: => Responder): Iteratee[HttpChunk,Responder] = {
    val is = new java.io.FileOutputStream(in)
    Iteratee.foreach[HttpChunk]{d=>is.write(d.bytes)}.map{_ => is.close(); f }
  }

  def textFile(req: RequestPrelude, in: java.io.File)(f: => Responder): Iteratee[HttpChunk,Responder] = {
    val is = new java.io.PrintStream(new FileOutputStream(in))
    Iteratee.foreach[HttpChunk]{ d => is.print(new String(d.bytes, req.charset))}.map{ _ => is.close(); f }
  }
}
