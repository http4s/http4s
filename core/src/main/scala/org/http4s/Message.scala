package org.http4s

import java.io.File
import java.net.InetAddress
import org.http4s.Header.`Content-Type`
import ResponseSyntax.ResponseSyntaxBase
import scalaz.concurrent.Task

abstract class Message(headers: Headers, body: HttpBody, attributes: AttributeMap) {
  type Self <: Message

  def withHeaders(headers: Headers): Self

  def contentLength: Option[Int] = headers.get(Header.`Content-Length`).map(_.length)

  def contentType: Option[`Content-Type`] = headers.get(Header.`Content-Type`)
  def withContentType(contentType: Option[`Content-Type`]): Self =
    withHeaders(contentType match {
      case Some(ct) => headers.put(ct)
      case None => headers.filterNot(_.is(Header.`Content-Type`))
    })

  def charset: CharacterSet = contentType.map(_.charset) getOrElse CharacterSet.`ISO-8859-1`

  def withBody(body: HttpBody): Self

  def addHeader(header: Header): Self = withHeaders(headers = header +: headers)

  def putHeader(header: Header): Self = withHeaders(headers = headers.put(header))

  def filterHeaders(f: Header => Boolean): Self =
    withHeaders(headers = headers.filter(f))

  def removeHeader(key: HeaderKey): Self = filterHeaders(_ isNot key)

  def isChunked: Boolean = headers.get(Header.`Transfer-Encoding`)
    .map(_.values.list.contains(TransferCoding.chunked))
    .getOrElse(false)

  /** Generates a new message object with the specified key/value pair appended to the [[org.http4s.AttributeMap]]
    *
    * @param key [[AttributeKey]] with which to associate the value
    * @param value value associated with the key
    * @tparam T type of the value to store
    * @return a new message object with the key/value pair appended
    */
  def withAttribute[T](key: AttributeKey[T], value: T): Self

  /**
   * The trailer headers, as specified in Section 3.6.1 of RFC 2616.  The resulting
   * task might not complete unless the entire body has been consumed.
   */
  def trailerHeaders: Task[Headers] = attributes.get(Message.Keys.TrailerHeaders).getOrElse(Task.now(Headers.empty))

  def withTrailerHeaders(trailerHeaders: Task[Headers]) = withAttribute(Message.Keys.TrailerHeaders, trailerHeaders)
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
  body: HttpBody = HttpBody.empty,
  attributes: AttributeMap = AttributeMap.empty
) extends Message(headers, body, attributes) {
  import Request._

  type Self = Request

  /** Replaces the [[Header]]s of the incoming Request object
    *
    * @param headers [[Headers]] containing the desired headers
    * @return a new Request object
    */
  def withHeaders(headers: Headers): Request = copy(headers = headers)

  /** Replace the body of the incoming Request object
    *
    * @param body scalaz.stream.Process[Task,Chunk] representing the new body
    * @return a new Request object
    */
  def withBody(body: HttpBody): Request = copy(body = body)

  def withAttribute[T](key: AttributeKey[T], value: T): Request = copy(attributes = attributes.put(key, value))

  lazy val authType: Option[AuthScheme] = headers.get(Header.Authorization).map(_.credentials.authScheme)

  lazy val (scriptName, pathInfo) = {
    val caret = attributes.get(Request.Keys.PathInfoCaret).getOrElse(0)
    requestUri.path.splitAt(caret)
  }
  def withPathInfo(pi: String) = copy(requestUri = requestUri.withPath(scriptName + pi))

  lazy val pathTranslated: Option[File] = attributes.get(Keys.PathTranslated)

  def queryString: String = requestUri.query.getOrElse("")

  /** Representation of the URI query as a Map[String, Seq[String]]
    *
    * The query string is lazily parsed. If an error occurs during parsing
    * an empty Map is returned
    */
  def multiParams: Map[String, Seq[String]] = requestUri.multiParams

  /** View of the head elements of the URI multiParams
    * @see multiParams
    */
  def params: Map[String, String] = requestUri.params

  lazy val remote: Option[InetAddress] = attributes.get(Keys.Remote)
  lazy val remoteAddr: Option[String] = remote.map(_.getHostAddress)
  lazy val remoteHost: Option[String] = remote.map(_.getHostName)

  lazy val remoteUser: Option[String] = None

  lazy val serverName = (requestUri.host orElse headers.get(Header.Host).map(_.host) getOrElse InetAddress.getLocalHost.getHostName)
  lazy val serverPort = (requestUri.port orElse headers.get(Header.Host).map(_.port) getOrElse 80)

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
 *                   backend for additional processing such as [[websocket.websocketKey]] or java.io.File objects
 */
case class Response(
  status: Status = Status.Ok,
  headers: Headers = Headers.empty,
  body: HttpBody = HttpBody.empty,
  attributes: AttributeMap = AttributeMap.empty
) extends Message(headers, body, attributes) with ResponseSyntaxBase[Response] {
  type Self = Response
  override protected def translateResponse(f: (Response) => Response): Response = f(this)

  /** Replace the body of this Response with a new scalaz.stream.Process[Task,Chunk] */
  def withBody(body: HttpBody): Response = copy(body = body)

  def withAttribute[T](key: AttributeKey[T], value: T): Response = copy(attributes = attributes.put(key, value))
}

