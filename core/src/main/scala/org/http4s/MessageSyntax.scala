package org.http4s

import scalaz.{stream, EitherT}
import scalaz.concurrent.Task
import scalaz.stream.Process.eval

object MessageSyntax extends MessageSyntax

trait MessageSyntax {
  implicit def requestSyntax(req: Task[Request]): TaskMessageOps[Request] = new TaskRequestOps(req)

  implicit def responseSyntax(resp: Task[Response]): TaskResponseOps = new TaskResponseOps(resp)
}

trait TaskMessageOps[M <: Message] extends Any with MessageOps[Task] {
  type Self = M#Self

  def self: Task[M]

  override protected def map[A, B](fa: Task[A])(f: (A) => B): Task[B] =
    fa.map(f)

  override protected def taskMap[A, B](fa: Task[A])(f: A => Task[B]): Task[B] =
    fa.flatMap(f)

  override protected def streamMap[A, B](fa: Task[A])(f: A => stream.Process[Task, B]): stream.Process[Task, B] =
    eval(fa).flatMap(f)

  override def httpVersion: Task[HttpVersion] =
    self.map(_.httpVersion)

  override def body: EntityBody =
    eval(self).flatMap(_.body)

  override def attributes: Task[AttributeMap] =
    self.map(_.attributes)

  override def headers: Task[Headers] =
    self.map(_.headers)

  override def transformAttributes(f: AttributeMap => AttributeMap): Task[Self] =
    self.map(_.transformAttributes(f))

  override def transformHeaders(f: (Headers) => Headers): Task[Self] =
    self.map(_.transformHeaders(f))

  override def withBody[T](b: T)(implicit w: EntityEncoder[T]): Task[Self] =
    self.flatMap(_.withBody(b)(w))

  override def attemptAs[T](implicit decoder: EntityDecoder[T]): DecodeResult[T] = EitherT(self.flatMap { msg =>
    decoder.decode(msg, false).run
  })
}

final class TaskRequestOps(val self: Task[Request])
  extends AnyVal with TaskMessageOps[Request] with RequestOps[Task]
{
  override def uri: Task[Uri] =
    self.map(_.uri)

  override def transformUri(f: Uri => Uri): Task[Request] =
    self.map(_.transformUri(f))

  override def scriptName: Task[String] =
    self.map(_.scriptName)

  override def pathInfo: Task[String] =
    self.map(_.pathInfo)

  override def serverPort: Task[Int] =
    self.map(_.serverPort)

  override def serverAddr: Task[String] =
    self.map(_.serverAddr)

  override def decodeWith[A](decoder: EntityDecoder[A], strict: Boolean)(f: (A) => Task[Response]): Task[Response] =
    self.flatMap(_.decodeWith(decoder, strict)(f))
}

final class TaskResponseOps(val self: Task[Response])
  extends AnyVal with TaskMessageOps[Response] with ResponseOps[Task]
{
  override def withStatus(status: Status): Task[Response] =
    self.map(_.withStatus(status))
}
