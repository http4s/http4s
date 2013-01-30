package org.http4s

import scala.language.implicitConversions
import scala.concurrent.{ExecutionContext, Future, future}

import play.api.libs.iteratee._
import play.api.libs.iteratee.Enumerator._

case class MessageBody(body: Enumerator[BodyChunk] = Enumerator.eof, last: LastChunk = LastChunk.Empty)
                       extends Enumerator[Chunk] {
  def apply[A](it: Iteratee[Chunk, A]): Future[Iteratee[Chunk, A]] = {
    body.mapInput[Chunk]({
      case Input.EOF => Input.Empty
      case in => in
    }).andThen(enumInput(Input.El[Chunk](last)).andThen(Enumerator.eof))(it)
  }
}

object MessageBody {
  implicit def fromBodyChunkEnumerator(enumerator: Enumerator[BodyChunk]) = MessageBody(body = enumerator)
  val Empty = MessageBody()
}

sealed trait Chunk {
  def bytes: Array[Byte]
}

case class BodyChunk(bytes: Array[Byte], extensions: Seq[ChunkExtension] = Nil) extends Chunk

case class LastChunk(extensions: Seq[ChunkExtension] = Nil, trailer: Headers = Headers.Empty) extends Chunk {
  val bytes = Array.empty[Byte]
}

object LastChunk {
  val Empty: LastChunk = LastChunk()
}

case class ChunkExtension(name: String, value: String)