package org.http4s

import java.io.File
import java.net.InetAddress
import org.http4s.Header.`Content-Type`
import MessageSyntax.ResponseSyntax
import scalaz.concurrent.Task

/** Represents a HTTP Message. The interesting subclasses are Request and Response
  * while most of the functionality is found in [[MessageSyntax]] and [[ResponseSyntax]]
  * @see [[MessageSyntax]], [[ResponseSyntax]]
  */
trait Message {
  type Self <: Message
  
  def headers: Headers
  
  def body: HttpBody
  
  def attributes: AttributeMap

  private[http4s] def withBHA(body: HttpBody = body,
                              headers: Headers = headers,
                              attributes: AttributeMap = attributes): Self

  /** Replace the body of the incoming Request object
    *
    * @param body body of type T
    * @param w [[Writable]] corresponding to the body
    * @tparam T type of the body
    * @return new message
    */
  def withBody[T](body: T)(implicit w: Writable[T]): Task[Self] = withBody(body, w.contentType)

  def withBody[T](body: T, contentType: `Content-Type`)(implicit w: Writable[T]): Task[Self] = {
    w.toBody(body).flatMap{ case (proc, len) =>
      val h = len match {
        case Some(l) => headers.put(Header.`Content-Length`(l), contentType)
        case None => headers.put(contentType)
      }
      Task.now(withBHA(body = proc, headers = h))
    }
  }

  def contentLength: Option[Int] = headers.get(Header.`Content-Length`).map(_.length)

  def contentType: Option[`Content-Type`] = headers.get(Header.`Content-Type`)

  def charset: CharacterSet = contentType.map(_.charset) getOrElse CharacterSet.`ISO-8859-1`

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
  body: HttpBody = HttpBody.empty,
  attributes: AttributeMap = AttributeMap.empty
) extends Message {
  import Request._

  type Self = Request

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

  override private[http4s] def withBHA(body: HttpBody, headers: Headers, attributes: AttributeMap): Self =
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
 *                   backend for additional processing such as [[websocket.websocketKey]] or java.io.File objects
 */
case class Response(
  status: Status = Status.Ok,
  headers: Headers = Headers.empty,
  body: HttpBody = HttpBody.empty,
  attributes: AttributeMap = AttributeMap.empty
) extends Message with ResponseSyntax[Response] {
  type Self = Response

  override protected def translateMessage(f: (Response) => Response): Response = f(this)

  override private[http4s] def withBHA(body: HttpBody, headers: Headers, attributes: AttributeMap): Self =
    copy(body = body, headers = headers, attributes = attributes)
}

