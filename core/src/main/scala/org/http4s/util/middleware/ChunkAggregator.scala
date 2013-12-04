package org.http4s
package util.middleware

import scalaz.concurrent.Task
import scalaz.stream.Process._
import com.typesafe.scalalogging.slf4j.Logging
import scala.annotation.tailrec
import org.http4s.Header.`Content-Length`

/**
 * @author Bryce Anderson
 *         Created on 11/30/13
 */
object ChunkAggregator extends Logging {

  @tailrec
  private[ChunkAggregator] def reduce(acc: BodyChunk, chunks: Seq[Chunk]): List[Chunk] = chunks.head match {
    case c: BodyChunk =>
      if (!chunks.tail.isEmpty) reduce(acc ++ c, chunks.tail)
      else acc ++ c :: Nil

    case c: TrailerChunk => acc::c::Nil
  }

  private[ChunkAggregator] def compact(body: HttpBody): List[Chunk] = {
    val (chunks, tail) = body.unemit
    if (!chunks.isEmpty &&
      tail.isInstanceOf[Halt] &&
      (tail.asInstanceOf[Halt].cause eq End)) reduce(BodyChunk(), chunks)
    else Nil
  }

  def apply(route: HttpService): HttpService = {

    def go(req: Request): Task[Response] = {
      route(req).map { response =>
        val chunks = compact(response.body)
        if (!chunks.isEmpty) {
          response.putHeader(`Content-Length`(chunks.head.length)).withBody(emitSeq(chunks))
        }
        else response
      }
    }
    go
  }

}
