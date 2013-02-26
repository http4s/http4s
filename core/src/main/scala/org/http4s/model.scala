package org.http4s

import attributes.{AttributesView, RequestScope, AttributeKey, Attributes}
import java.io.File
import java.net.{URI, InetAddress}
import java.util.UUID
import java.nio.charset.Charset
import akka.util.ByteString

// Our Http message "currency" types
sealed trait HasHeaders {
  def headers: Headers
}

sealed trait HttpPrelude extends HasHeaders

// IPC: Do we still need HttpChunk?
sealed trait HttpChunk {
  def bytes: ByteString
}

case class BodyChunk(bytes: ByteString) extends HttpChunk

object RequestPrelude {
  def apply(
    requestMethod: Method = Method.Get,
    scriptName: String = "",
    pathInfo: String = "",
    queryString: String = "",
    pathTranslated: Option[File] = None,
    protocol: ServerProtocol = HttpVersion.`Http/1.1`,
    headers: Headers = Headers.empty,
    cookies: RequestCookieJar = RequestCookieJar.empty,
    urlScheme: UrlScheme = UrlScheme.Http,
    serverName: String = InetAddress.getLocalHost.getHostName,
    serverPort: Int = 80,
    serverSoftware: ServerSoftware = ServerSoftware.Unknown,
    remote: InetAddress = InetAddress.getLocalHost): RequestPrelude =
    new RequestPrelude(
      requestMethod,
      scriptName,
      pathInfo,
      queryString,
      pathTranslated,
      protocol,
      headers,
      urlScheme,
      serverName,
      serverPort,
      serverSoftware,
      remote
    )

  def unapply(request: RequestPrelude): Option[(Method, String, String, String, Option[File], ServerProtocol, Headers, UrlScheme, String, Int, ServerSoftware, InetAddress)] =
    Some((request.requestMethod, request.scriptName, request.pathInfo, request.queryString, request.pathTranslated, request.protocol, request.headers, request.urlScheme, request.serverName, request.serverPort, request.serverSoftware, request.remote))
}
final class RequestPrelude private(
  val requestMethod: Method,
  val scriptName: String,
  val pathInfo: String,
  val queryString: String,
  val pathTranslated: Option[File],
  val protocol: ServerProtocol,
  val headers: Headers,
  val cookies: RequestCookieJar,
  val urlScheme: UrlScheme,
  val serverName: String,
  val serverPort: Int,
  val serverSoftware: ServerSoftware,
  val remote: InetAddress,
  scope: RequestScope) extends HttpPrelude {

  def this(
      requestMethod: Method = Method.Get,
      scriptName: String = "",
      pathInfo: String = "",
      queryString: String = "",
      pathTranslated: Option[File] = None,
      protocol: ServerProtocol = HttpVersion.`Http/1.1`,
      headers: Headers = Headers.empty,
      urlScheme: UrlScheme = UrlScheme.Http,
      serverName: String = InetAddress.getLocalHost.getHostName,
      serverPort: Int = 80,
      serverSoftware: ServerSoftware = ServerSoftware.Unknown,
      remote: InetAddress = InetAddress.getLocalHost) =
      this(
        requestMethod,
        scriptName,
        pathInfo,
        queryString,
        pathTranslated,
        protocol,
        headers,
        RequestCookieJar(headers.getAll("Cookie").collect({case c: HttpCookie => c}):_*), // TODO: Actually make work properly
        urlScheme,
        serverName,
        serverPort,
        serverSoftware,
        remote,
        RequestScope(UUID.randomUUID()))
  private[this] implicit val _scope = scope

  def uuid = scope.uuid
  lazy val contentLength: Option[Long] = headers.get("Content-Length").map(_.value.toLong)

  lazy val contentType: Option[ContentType] = headers.get("Content-Type").map(_.asInstanceOf[ContentType])

  lazy val charset: Charset = contentType.map(_.charset.nioCharset) getOrElse Charset.defaultCharset()

  lazy val uri: URI = new URI(urlScheme.toString, null, serverName, serverPort, scriptName+pathInfo, queryString, null)

  lazy val authType: Option[AuthType] = None

  lazy val remoteAddr = remote.getHostAddress
  lazy val remoteHost = remote.getHostName

  lazy val remoteUser: Option[String] = None

  val attributes = new AttributesView(GlobalState.forScope(scope))

  /* Attributes proxy */
  def updated[T](key: AttributeKey[T], value: T) = {
    attributes.updated(key, value)
    this
  }
  def apply[T](key: AttributeKey[T]): T = attributes(key)
  def get[T](key: AttributeKey[T]): Option[T] = attributes get key
  def getOrElse[T](key: AttributeKey[T], default: => T) = attributes.getOrElse(key, default)
  def +[T](kv: (AttributeKey[T], T)) = {
    attributes += kv
    this
  }

  def -[T](key: AttributeKey[T]) = {
    attributes -= key
    this
  }
  def contains[T](key: AttributeKey[T]): Boolean = attributes contains key
  /* Attributes proxy end */

  def copy(
      requestMethod: Method = requestMethod,
      scriptName: String = scriptName,
      pathInfo: String = pathInfo,
      queryString: String = queryString,
      pathTranslated: Option[File] = pathTranslated,
      protocol: ServerProtocol = protocol,
      headers: Headers = headers,
      urlScheme: UrlScheme = urlScheme,
      serverName: String = serverName,
      serverPort: Int = serverPort,
      serverSoftware: ServerSoftware =serverSoftware,
      remote: InetAddress = remote): RequestPrelude =
      new RequestPrelude(
        requestMethod,
        scriptName,
        pathInfo,
        queryString,
        pathTranslated,
        protocol,
        headers,
        RequestCookieJar(Nil), // TODO: Implement reading the new headers here
        urlScheme,
        serverName,
        serverPort,
        serverSoftware,
        remote,
        scope)

  override def hashCode(): Int = scope.uuid.##

  override def equals(obj: Any): Boolean = obj match {
    case req: RequestPrelude => req.uuid == uuid
    case _ => false
  }

  override def toString: String = {
    s"RequestPrelude(uuid: $uuid, method: $requestMethod, pathInfo: $pathInfo, queryString: $queryString, " +
    s"pathTranslated: $pathTranslated, protocol: $protocol, urlScheme: $urlScheme, serverName: $serverName, " +
    s"serverPort: $serverPort, serverSoftware: $serverSoftware, remote: $remote, headers: $headers, attributes: ${attributes.toMap}"
  }

  override def clone(): AnyRef = copy()
}
case class ResponsePrelude(status: Status, headers: Headers = Headers.empty) extends HttpPrelude

case class TrailerChunk(headers: Headers) extends HasHeaders with HttpChunk {
  final val bytes = ByteString.empty
}