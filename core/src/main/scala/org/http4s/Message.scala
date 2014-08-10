package org.http4s

import java.io.File
import java.net.InetAddress
import org.http4s.Header.{`Content-Length`, `Content-Type`}
import org.http4s.server.ServerSoftware
import scalaz.concurrent.Task

/**
 * Represents a HTTP Message. The interesting subclasses are Request and Response
 * while most of the functionality is found in [[MessageSyntax]] and [[ResponseMethods]]
 * @see [[MessageSyntax]], [[ResponseMethods]]
 */
trait Message extends MessageMethods {
  type Self <: Message

  def httpVersion: HttpVersion
  
  def headers: Headers
  
  def body: EntityBody
  
  def attributes: AttributeMap
  
  protected def change(body: EntityBody = body,
                       headers: Headers = headers,
                       attributes: AttributeMap = attributes): Self

  /** Generates a new message object with the specified key/value pair appended to the [[org.http4s.AttributeMap]]
    *
    * @param key [[AttributeKey]] with which to associate the value
    * @param value value associated with the key
    * @tparam A type of the value to store
    * @return a new message object with the key/value pair appended
    */
  override def withAttribute[A](key: AttributeKey[A], value: A): Self =
    change(attributes = attributes.put(key, value))

  /** Replaces the [[Header]]s of the incoming Request object
    *
    * @param headers [[Headers]] containing the desired headers
    * @return a new Request object
    */
  override def withHeaders(headers: Headers): Self = change(headers = headers)

  /** Add the provided headers to the existing headers, replacing those of the same header name */
  override def putHeaders(headers: Header*): Self =
    change(headers = this.headers.put(headers:_*))

  override def filterHeaders(f: (Header) => Boolean): Self =
    change(headers = headers.filter(f))

  /** Add the provided headers to the existing headers */
  override def addHeaders(headers: Header*): Self =
    change(headers = this.headers ++ Headers(headers.toList))

  /** Replace the body of this message with a new body
    *
    * @param b body to attach to this method
    * @param w [[Writable]] with which to convert the body to an [[EntityBody]]
    * @tparam T type of the Body
    * @return a new message with the new body
    */
  def withBody[T](b: T)(implicit w: Writable[T]): Task[Self] = {
    w.toEntity(b).map { entity =>
      val hs = entity.length match {
        case Some(l) => `Content-Length`(l) +:w.headers
        case None    => w.headers
      }
      change(body = entity.body, headers = headers ++ hs)
    }
  }

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
  * status line, headers, and a possible request body.
  *
  * @param requestMethod [[Method.GET]], [[Method.POST]], etc.
  * @param requestUri representation of the request URI
  * @param httpVersion the HTTP version
  * @param headers collection of [[Header]]s
  * @param body scalaz.stream.Process[Task,Chunk] defining the body of the request
  * @param attributes Immutable Map used for carrying additional information in a type safe fashion
  */
case class Request(
  requestMethod: Method = Method.GET,
  requestUri: Uri = Uri(path = "/"),
  httpVersion: HttpVersion = HttpVersion.`HTTP/1.1`,
  headers: Headers = Headers.empty,
  body: EntityBody = EmptyBody,
  attributes: AttributeMap = AttributeMap.empty
) extends Message with MessageMethods {
  import Request._

  type Self = Request


  override protected def change(body: EntityBody, headers: Headers, attributes: AttributeMap): Self =
    copy(body = body, headers = headers, attributes = attributes)

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
  httpVersion: HttpVersion = HttpVersion.`HTTP/1.1`,
  headers: Headers = Headers.empty,
  body: EntityBody = EmptyBody,
  attributes: AttributeMap = AttributeMap.empty) extends Message with ResponseMethods {
  type Self = Response

  /** Response specific extension methods */
  override def withStatus[S <% Status](status: S): Self = copy(status = status)

  override protected def change(body: EntityBody, headers: Headers, attributes: AttributeMap): Self =
    copy(body = body, headers = headers, attributes = attributes)
}

