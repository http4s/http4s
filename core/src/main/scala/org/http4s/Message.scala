package org.http4s

import java.io.File
import java.net.InetAddress
import org.http4s.Header.`Content-Type`
import org.http4s.server.ServerSoftware
import scalaz.concurrent.Task
import MessageSyntax.ResponseSyntax

/**
 * Represents a HTTP Message. The interesting subclasses are Request and Response
 * while most of the functionality is found in [[MessageSyntax]] and [[MessageSyntax.ResponseSyntax]]
 * @see [[MessageSyntax]], [[MessageSyntax.ResponseSyntax]]
 */
trait Message {
  type Self <: Message

  def protocol: ServerProtocol
  
  def headers: Headers
  
  def body: EntityBody
  
  def attributes: AttributeMap

  private[http4s] def withBHA(body: EntityBody = body,
                              headers: Headers = headers,
                              attributes: AttributeMap = attributes): Self

  def contentLength: Option[Int] = headers.get(Header.`Content-Length`).map(_.length)

  def contentType: Option[`Content-Type`] = headers.get(Header.`Content-Type`)

  def charset: Charset = contentType.map(_.charset) getOrElse Charset.`ISO-8859-1`

  def isChunked: Boolean = headers.get(Header.`Transfer-Encoding`)
    .map(_.values.list.contains(TransferCoding.chunked))
    .getOrElse(false)

  /**
   * The trailer headers, as specified in Section 3.6.1 of RFC 2616.  The resulting
   * task might not complete unless the entire body has been consumed.
   */
  def trailerHeaders: Task[Headers] = attributes.get(Message.Keys.TrailerHeaders).getOrElse(Task.now(Headers.empty))
}

object Message {
  object Keys {
    val TrailerHeaders = AttributeKey.http4s[Task[Headers]]("trailer-headers")
  }
}

/** Representation of an incoming HTTP message
  *
  * A Request encapsulates the entirety of the incoming HTTP request including the
  * status line, headers, and a possible request body. A Request will be the only argument
  * for a [[HttpService]].
  *
  * @param requestMethod [[Method.Get]], [[Method.Post]], etc.
  * @param requestUri representation of the request URI
  * @param protocol HTTP protocol, eg ServerProtocol.`HTTP/1.1`
  * @param headers collection of [[Header]]s
  * @param body scalaz.stream.Process[Task,Chunk] defining the body of the request
  * @param attributes Immutable Map used for carrying additional information in a type safe fashion
  */
case class Request(
  requestMethod: Method = Method.Get,
  requestUri: Uri = Uri(path = "/"),
  protocol: ServerProtocol = ServerProtocol.`HTTP/1.1`,
  headers: Headers = Headers.empty,
  body: EntityBody = EmptyBody,
  attributes: AttributeMap = AttributeMap.empty
) extends Message with MessageSyntax.MessageSyntax[Request, Request] {
  import Request._

  type Self = Request

  override protected def translateMessage(f: (Request) => Request): Request = f(this)

  override protected def translateWithTask(f: (Request) => Task[Request]): Task[Request] = f(this)

  lazy val authType: Option[AuthScheme] = headers.get(Header.Authorization).map(_.credentials.authScheme)

  lazy val (scriptName, pathInfo) = {
    val caret = attributes.get(Request.Keys.PathInfoCaret).getOrElse(0)
    requestUri.path.splitAt(caret)
  }

  def withPathInfo(pi: String) = copy(requestUri = requestUri.withPath(scriptName + pi))

  lazy val pathTranslated: Option[File] = attributes.get(Keys.PathTranslated)

  def queryString: String = requestUri.query.getOrElse("")

  /**
   * Representation of the query string as a map
   *
   * In case a parameter is available in query string but no value is there the
   * sequence will be empty. If the value is empty the the sequence contains an
   * empty string.
   *
   * =====Examples=====
   * <table>
   * <tr><th>Query String</th><th>Map</th></tr>
   * <tr><td><code>?param=v</code></td><td><code>Map("param" -> Seq("v"))</code></td></tr>
   * <tr><td><code>?param=</code></td><td><code>Map("param" -> Seq(""))</code></td></tr>
   * <tr><td><code>?param</code></td><td><code>Map("param" -> Seq())</code></td></tr>
   * <tr><td><code>?=value</code></td><td><code>Map("" -> Seq("value"))</code></td></tr>
   * <tr><td><code>?p1=v1&amp;p1=v2&amp;p2=v3&amp;p2=v3</code></td><td><code>Map("p1" -> Seq("v1","v2"), "p2" -> Seq("v3","v4"))</code></td></tr>
   * </table>
   *
   * The query string is lazily parsed. If an error occurs during parsing
   * an empty `Map` is returned.
   */
  def multiParams: Map[String, Seq[String]] = requestUri.multiParams

  /**
   * View of the head elements of the URI parameters in query string.
   *
   * In case a parameter has no value the map returns an empty string.
   *
   * @see multiParams
   */
  def params: Map[String, String] = requestUri.params

  lazy val remote: Option[InetAddress] = attributes.get(Keys.Remote)
  lazy val remoteAddr: Option[String] = remote.map(_.getHostAddress)
  lazy val remoteHost: Option[String] = remote.map(_.getHostName)

  lazy val remoteUser: Option[String] = None

  lazy val serverName: String = {
    requestUri.host.map(_.value)
      .orElse(headers.get(Header.Host).map(_.host))
      .getOrElse(InetAddress.getLocalHost.getHostName)
  }

  lazy val serverPort: Int = {
    requestUri.port
      .orElse(headers.get(Header.Host).flatMap(_.port))
      .getOrElse(80)
  }

  def serverSoftware: ServerSoftware = attributes.get(Keys.ServerSoftware).getOrElse(ServerSoftware.Unknown)

  override private[http4s] def withBHA(body: EntityBody, headers: Headers, attributes: AttributeMap): Request =
    copy(body = body, headers = headers, attributes = attributes)
}

object Request {
  object Keys {
    val PathInfoCaret = AttributeKey.http4s[Int]("request.pathInfoCaret")
    val PathTranslated = AttributeKey.http4s[File]("request.pathTranslated")
    val Remote = AttributeKey.http4s[InetAddress]("request.remote")
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
  protocol: ServerProtocol = ServerProtocol.`HTTP/1.1`,
  headers: Headers = Headers.empty,
  body: EntityBody = EmptyBody,
  attributes: AttributeMap = AttributeMap.empty) extends Message with ResponseSyntax[Response] {
  type Self = Response

  override protected def translateMessage(f: (Response) => Response): Response = f(this)

  override protected def translateWithTask(f: (Response) => Task[Response]): Task[Response] = f(this)

  override private[http4s] def withBHA(body: EntityBody, headers: Headers, attributes: AttributeMap): Response =
    copy(body = body, headers = headers, attributes = attributes)
}

