package org.http4s

import scala.language.reflectiveCalls

import concurrent.{Promise, ExecutionContext, Future, future}

import play.api.libs.iteratee._
import play.api.libs.iteratee.Enumerator._
import io.Codec

case class MessageBody(body: Enumerator[BodyChunk] = Enumerator.eof,
                       last: Promise[LastChunk] = LastChunk.EmptyPromise) {
  def enumerate(implicit executor: ExecutionContext): Enumerator[Chunk] = {
    val bodyEnum = body.mapInput[Chunk]({
      case Input.EOF => Input.Empty
      case in => in
    })
    val lastChunkEnum = last.future.map(chunk => enumInput(Input.El[Chunk](chunk)))
    bodyEnum andThen flatten(lastChunkEnum) andThen Enumerator.eof
  }

  def run[A](iteratee: Iteratee[Chunk, A])(implicit executor: ExecutionContext): Future[A] = enumerate.run(iteratee)

  def asBytes(implicit executor: ExecutionContext): Future[Array[Byte]] =
    run(Enumeratee.map[Chunk](_.bytes).transform(Iteratee.consume[Array[Byte]](): Iteratee[Array[Byte], Array[Byte]]))

  def asString(implicit codec: Codec, executor: ExecutionContext): Future[String] =
    asBytes.map(new String(_, codec.charSet))
}

object MessageBody {
  def apply[A](a: A*)(implicit writable: Writable[A], executor: ExecutionContext): MessageBody =
    new MessageBody(enumerate(a).map(x => BodyChunk(writable.toBytes(x))))

  def fromBodyChunkEnumerator(enumerator: Enumerator[BodyChunk]): MessageBody = MessageBody(body = enumerator)

  val Empty = new MessageBody()
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