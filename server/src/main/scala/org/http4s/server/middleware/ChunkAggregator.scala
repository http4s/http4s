package org.http4s
package server
package middleware

import scalaz.stream.Process._
import scalaz.stream.Cause.End
import scala.annotation.tailrec
import org.http4s.headers.`Content-Length`
import scodec.bits.ByteVector
import scalaz.Kleisli._

object ChunkAggregator {

  @tailrec
  private[ChunkAggregator] def reduce(acc: ByteVector, chunks: Seq[ByteVector]): List[ByteVector] = {
    if (chunks.tail.nonEmpty) reduce(acc ++ chunks.head, chunks.tail)
    else (acc ++ chunks.head) :: Nil
  }

  private[ChunkAggregator] def compact(body: EntityBody): List[ByteVector] = {
    val (chunks, tail) = body.unemit
    if (chunks.nonEmpty &&
      tail.isInstanceOf[Halt] &&
      (tail.asInstanceOf[Halt].cause eq End)) reduce(ByteVector.empty, chunks)
    else Nil
  }

  def apply(service: HttpService): HttpService = service.map { response =>
    val chunks = compact(response.body)
    if (chunks.nonEmpty) {
      val h = response.headers.put(`Content-Length`(chunks.head.length))
      response.copy(body = emitAll(chunks), headers = h)
    }
    else response
  }
}
