package org.http4s
package middleware

import scalaz.concurrent.Task
import scalaz.stream.Process._
import com.typesafe.scalalogging.slf4j.Logging
import scala.annotation.tailrec
import org.http4s.Header.`Content-Length`
import scodec.bits.ByteVector

/**
 * @author Bryce Anderson
 *         Created on 11/30/13
 */
object ChunkAggregator extends Logging {

  @tailrec
  private[ChunkAggregator] def reduce(acc: ByteVector, chunks: Seq[ByteVector]): List[ByteVector] = {
    if (!chunks.tail.isEmpty) reduce(acc ++ chunks.head, chunks.tail)
    else (acc ++ chunks.head) :: Nil
  }

  private[ChunkAggregator] def compact(body: HttpBody): List[ByteVector] = {
    val (chunks, tail) = body.unemit
    if (!chunks.isEmpty &&
      tail.isInstanceOf[Halt] &&
      (tail.asInstanceOf[Halt].cause eq End)) reduce(ByteVector.empty, chunks)
    else Nil
  }

  def apply(route: HttpService): HttpService = route andThen (_.map { response =>
    val chunks = compact(response.body)
    if (!chunks.isEmpty)
      response.putHeader(`Content-Length`(chunks.head.length))
        .withBody(emitSeq(chunks))

    else response
  })
}
