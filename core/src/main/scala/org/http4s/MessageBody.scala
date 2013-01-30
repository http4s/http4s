package org.http4s

import scala.language.implicitConversions
import concurrent.{Promise, ExecutionContext, Future, future}

import play.api.libs.iteratee._
import play.api.libs.iteratee.Enumerator._

case class MessageBody(body: Enumerator[BodyChunk] = Enumerator.eof,
                       last: Promise[LastChunk] = LastChunk.EmptyPromise)

object MessageBody {
  implicit def fromBodyChunkEnumerator(enumerator: Enumerator[BodyChunk]): MessageBody = MessageBody(body = enumerator)

  implicit def toChunkEnumerator(messageBody: MessageBody)(implicit executor: ExecutionContext): Enumerator[Chunk] = {
    val bodyEnum = messageBody.body.mapInput[Chunk]({
      case Input.EOF => Input.Empty
      case in => in
    })
    val lastChunkEnum = messageBody.last.future.map(chunk => enumInput(Input.El[Chunk](chunk)))
    bodyEnum andThen flatten(lastChunkEnum) andThen Enumerator.eof
  }

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
  val EmptyPromise: Promise[LastChunk] = Promise.successful(Empty)
}

case class ChunkExtension(name: String, value: String)