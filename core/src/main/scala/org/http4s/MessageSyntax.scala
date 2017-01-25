package org.http4s

import cats._
import fs2._

object MessageSyntax extends MessageSyntax

trait MessageSyntax {
  implicit def requestSyntax(req: Task[Request]): TaskRequestOps = new TaskRequestOps(req)

  implicit def responseSyntax(resp: Task[Response]): TaskResponseOps = new TaskResponseOps(resp)
}

trait TaskMessageOps[M <: Message] extends Any with MessageOps {
  type Self = Task[M#Self]

  def self: Task[M]

  def transformHeaders(f: Headers => Headers): Self =
    self.map(_.transformHeaders(f))

  /** Add a body to the message
    * @see [[Message]]
    */
  def withBody[T](b: T)(implicit w: EntityEncoder[T]): Self = self.flatMap(_.withBody(b)(w))

  /** Generates a new message object with the specified key/value pair appended to the [[org.http4s.AttributeMap]]
    *
    * @param key [[AttributeKey]] with which to associate the value
    * @param value value associated with the key
    * @tparam A type of the value to store
    * @return a new message object with the key/value pair appended
    */
  override def withAttribute[A](key: AttributeKey[A], value: A): Self = self.map(_.withAttribute(key, value))

  /** Decode the [[Message]] to the specified type
    *
    * @param decoder [[EntityDecoder]] used to decode the [[Message]]
    * @tparam T type of the result
    * @return the `Task` which will generate the `ParseFailure\/T`
    */
  override def attemptAs[T](implicit decoder: EntityDecoder[T]): DecodeResult[T] =
    DecodeResult(self.flatMap { msg =>
      decoder.decode(msg, false).value
    })
}

final class TaskRequestOps(val self: Task[Request]) extends AnyVal with TaskMessageOps[Request] with RequestOps {
  def decodeWith[A](decoder: EntityDecoder[A], strict: Boolean)(f: A => Task[Response]): Task[Response] =
    self.flatMap(_.decodeWith(decoder, strict)(f))

  def withPathInfo(pi: String): Task[Request] =
    self.map(_.withPathInfo(pi))
}

final class TaskResponseOps(val self: Task[Response]) extends AnyVal with TaskMessageOps[Response] with ResponseOps {
  override def withStatus(status: Status): Self = self.map(_.withStatus(status))
}
