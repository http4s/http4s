package org.http4s

import java.io.File
import java.net.{InetSocketAddress, InetAddress}
import org.http4s.headers._
import org.http4s.server.ServerSoftware
import scalaz.Id.Id
import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.syntax.monad._

/**
 * Represents a HTTP Message. The interesting subclasses are Request and Response
 * while most of the functionality is found in [[MessageSyntax]] and [[ResponseOps]]
  *
  * @see [[MessageSyntax]], [[ResponseOps]]
 */
sealed trait Message extends MessageOps[Id] { self =>
  type Self <: Message { type Self = self.Self }

  override protected def map[A, B](fa: A)(f: A => B): B =
    f(fa)

  override protected def taskMap[A, B](fa: A)(f: (A) => Task[B]): Task[B] =
    f(fa)

  override protected def streamMap[A, B](fa: A)(f: (A) => Process[Task, B]): Process[Task, B] =
    f(fa)

  override def transformAttributes(f: AttributeMap => AttributeMap): Self =
    change(attributes = f(attributes))

  override def transformHeaders(f: Headers => Headers): Self =
    change(headers = f(headers))

  protected def change(body: EntityBody = body,
                       headers: Headers = headers,
                       attributes: AttributeMap = attributes): Self

  override def withBody[T](b: T)(implicit w: EntityEncoder[T]): Task[Self] = {
    w.toEntity(b).map { entity =>
      val hs = entity.length match {
        case Some(l) => `Content-Length`(l)::w.headers.toList
        case None    => w.headers
      }
      change(body = entity.body, headers = headers ++ hs)
    }
  }

  /** Decode the [[Message]] to the specified type
    *
    * @param decoder [[EntityDecoder]] used to decode the [[Message]]
    * @tparam T type of the result
    * @return the `Task` which will generate the `DecodeResult[T]`
    */
  override def attemptAs[T](implicit decoder: EntityDecoder[T]): DecodeResult[T] =
    decoder.decode(this, strict = false)
}

object Message {
  object Keys {
    val TrailerHeaders = AttributeKey.http4s[Task[Headers]]("trailer-headers")
  }
}

/** Representation of an incoming HTTP message
  *
  * A Request encapsulates the entirety of the incoming HTTP request including the
  * status line, headers, and a possible request body.
  *
  * @param method [[Method.GET]], [[Method.POST]], etc.
  * @param uri representation of the request URI
  * @param httpVersion the HTTP version
  * @param headers collection of [[Header]]s
  * @param body scalaz.stream.Process[Task,Chunk] defining the body of the request
  * @param attributes Immutable Map used for carrying additional information in a type safe fashion
  */
final case class Request(
  method: Method = Method.GET,
  uri: Uri = Uri(path = "/"),
  httpVersion: HttpVersion = HttpVersion.`HTTP/1.1`,
  headers: Headers = Headers.empty,
  body: EntityBody = EmptyBody,
  attributes: AttributeMap = AttributeMap.empty
) extends Message with RequestOps[Id] {
  type Self = Request

  override protected def change(body: EntityBody, headers: Headers, attributes: AttributeMap): Request =
    copy(body = body, headers = headers, attributes = attributes)

  override def transformUri(f: Uri => Uri): Request =
    copy(uri = f(uri))

  lazy val (scriptName, pathInfo) = {
    val caret = attributes.get(Request.Keys.PathInfoCaret).getOrElse(0)
    uri.path.splitAt(caret)
  }

  def serverAddr: String =
    server.map(_.getHostString)
      .orElse(uri.host.map(_.value))
      .orElse(headers.get(Host).map(_.host))
      .getOrElse(InetAddress.getLocalHost.getHostName)

  def serverPort: Int =
    server.map(_.getPort)
      .orElse(uri.port)
      .orElse(headers.get(Host).flatMap(_.port))
      .getOrElse(80)

  def decodeWith[A](decoder: EntityDecoder[A], strict: Boolean)(f: A => Task[Response]): Task[Response] =
    decoder.decode(this, strict = strict).fold(_.toHttpResponse(httpVersion), f).join

  override def toString: String =
    s"""Request(method=$method, uri=$uri, headers=${headers}"""
}

object Request {

  case class Connection(local: InetSocketAddress, remote: InetSocketAddress, secure: Boolean)

  object Keys {
    val PathInfoCaret = AttributeKey.http4s[Int]("request.pathInfoCaret")
    val PathTranslated = AttributeKey.http4s[File]("request.pathTranslated")
    val ConnectionInfo = AttributeKey.http4s[Connection]("request.remote")
    val ServerSoftware = AttributeKey.http4s[ServerSoftware]("request.serverSoftware")
  }
}

/** Representation of the HTTP response to send back to the client
 *
 * @param status [[Status]] code and message
 * @param headers [[Headers]] containing all response headers
 * @param body scalaz.stream.Process[Task,Chunk] representing the possible body of the response
 * @param attributes [[AttributeMap]] containing additional parameters which may be used by the http4s
 *                   backend for additional processing such as java.io.File object
 */
case class Response(
  status: Status = Status.Ok,
  httpVersion: HttpVersion = HttpVersion.`HTTP/1.1`,
  headers: Headers = Headers.empty,
  body: EntityBody = EmptyBody,
  attributes: AttributeMap = AttributeMap.empty) extends Message with ResponseOps[Id] {
  type Self = Response

  override def withStatus(status: Status): Self = copy(status = status)

  override protected def change(body: EntityBody, headers: Headers, attributes: AttributeMap): Self =
    copy(body = body, headers = headers, attributes = attributes)

  override def toString: String =
    s"""Response(status=${status.code}, headers=$headers)"""
}

object Response {
  def notFound(request: Request): Task[Response] = {
    val body = s"${request.pathInfo} not found"
    Response(Status.NotFound).withBody(body)
  }
}
