package org.http4s

import java.io.File
import java.net.InetSocketAddress
import java.time.Instant

import org.http4s.Request.Keys
import org.http4s.headers._
import org.http4s.server.ServerSoftware

import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.stream.text.utf8Decode

trait MessageOps[F[_]] extends Any {
  type Self

  protected def map[A, B](fa: F[A])(f: A => B): F[B]
  protected def taskMap[A, B](fa: F[A])(f: A => Task[B]): Task[B]
  protected def streamMap[A, B](fa: F[A])(f: A => Process[Task, B]): Process[Task, B]

  def httpVersion: F[HttpVersion]

  def headers: F[Headers]

  def transformHeaders(f: Headers => Headers): F[Self]

  /** Remove headers that satisfy the predicate
    *
    * @param p predicate
    * @return a new message object which lacks the specified headers
    */
  def filterHeaders(p: Header => Boolean): F[Self] =
    transformHeaders(_.filter(p))

  def attributes: F[AttributeMap]

  def transformAttributes(f: AttributeMap => AttributeMap): F[Self]

  /** Generates a new message object with the specified key/value pair appended to the [[org.http4s.AttributeMap]]
    *
    * @param key [[AttributeKey]] with which to associate the value
    * @param value value associated with the key
    * @tparam A type of the value to store
    * @return a new message object with the key/value pair appended
    */
  def withAttribute[A](key: AttributeKey[A], value: A): F[Self] =
    transformAttributes(_.put(key, value))

  /** Generates a new message object with the specified key/value pair appended to the [[org.http4s.AttributeMap]]
    *
    * @param entry [[AttributeEntry]] entry to add
    * @tparam V type of the value to store
    * @return a new message object with the key/value pair appended
    */
  def withAttribute[V](entry: AttributeEntry[V]): F[Self] =
    withAttribute(entry.key, entry.value)

  /** Added the [[`Content-Type`]] header to the response */
  def withType(t: MediaType): F[Self] =
    putHeaders(`Content-Type`(t))

  def withContentType(contentType: Option[`Content-Type`]): F[Self] =
    contentType match {
      case Some(t) => putHeaders(t)
      case None => filterHeaders(_.is(`Content-Type`))
    }

  def contentLength: F[Option[Long]] =
    map(headers)(_.get(`Content-Length`).map(_.length))

  def isChunked: F[Boolean] =
    map(headers)(_.get(`Transfer-Encoding`).exists(_.values.list.contains(TransferCoding.chunked)))

  def removeHeader(key: HeaderKey): F[Self] =
    filterHeaders(_ isNot key)

  /** Replaces the [[Header]]s of the incoming Request object
    *
    * @param headers [[Headers]] containing the desired headers
    * @return a new Request object
    */
  def replaceAllHeaders(headers: Headers): F[Self] =
    transformHeaders(_ => headers)

  /** Replace the existing headers with those provided */
  def replaceAllHeaders(headers: Header*): F[Self] =
    replaceAllHeaders(Headers(headers.toList))

  /** Add the provided headers to the existing headers, replacing those of the same header name
    * The passed headers are assumed to contain no duplicate Singleton headers.
    */
  def putHeaders(headers: Header*): F[Self] =
    transformHeaders(_.put(headers:_*))

  def contentType: F[Option[`Content-Type`]] =
    map(headers)(_.get(`Content-Type`))

  /** Returns the charset parameter of the `Content-Type` header, if present.
    * Does not introspect the body for media types that define a charset internally. */
  def charset: F[Option[Charset]] =
    map(contentType)(_.flatMap(_.charset))

  /**
    * The trailer headers, as specified in Section 3.6.1 of RFC 2616.  The resulting
    * task might not complete unless the entire body has been consumed.
    */
  def trailerHeaders: Task[Headers] =
    taskMap(attributes)(_.get(Message.Keys.TrailerHeaders).getOrElse(Task.now(Headers.empty)))

  def withTrailerHeaders(trailerHeaders: Task[Headers]): F[Self] =
    withAttribute(Message.Keys.TrailerHeaders, trailerHeaders)

  def body: EntityBody

  def bodyAsText(implicit defaultCharset: Charset = DefaultCharset): Process[Task, String] =
    streamMap(charset) { _ getOrElse defaultCharset match {
      case Charset.`UTF-8` =>
        // suspect this one is more efficient, though this is superstition
        body |> utf8Decode
      case cs =>
        body |> util.decode(cs)
    } }

  /** Replace the body of this message with a new body
    *
    * @param b body to attach to this method
    * @param w [[EntityEncoder]] with which to convert the body to an [[EntityBody]]
    * @tparam T type of the Body
    * @return a new message with the new body
    */
  def withBody[T](b: T)(implicit w: EntityEncoder[T]): Task[Self]

  /** Decode the [[Message]] to the specified type
    *
    * @param decoder [[EntityDecoder]] used to decode the [[Message]]
    * @tparam T type of the result
    * @return the `Task` which will generate the `DecodeResult[T]`
    */
  def attemptAs[T](implicit decoder: EntityDecoder[T]): DecodeResult[T]

  /** Decode the [[Message]] to the specified type
    *
    * If no valid [[Status]] has been described, allow Ok
    *
    * @param decoder [[EntityDecoder]] used to decode the [[Message]]
    * @tparam T type of the result
    * @return the `Task` which will generate the T
    */
  def as[T](implicit decoder: EntityDecoder[T]): Task[T] =
    attemptAs(decoder).fold(throw _, identity)
}

trait RequestOps[F[_]] extends Any with MessageOps[F] {
  def uri: F[Uri]
  def transformUri(f: Uri => Uri): F[Request]

  def withPathInfo(pi: String): F[Request] =
    transformUri(_.withPath(scriptName + pi))

  def authType: F[Option[AuthScheme]] =
    map(headers)(_.get(Authorization).map(_.credentials.authScheme))

  def scriptName: F[String]
  def pathInfo: F[String]

  def pathTranslated: F[Option[File]] =
    map(attributes)(_.get(Keys.PathTranslated))

  def queryString: F[String] =
    map(uri)(_.query.renderString)

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
  def multiParams: F[Map[String, Seq[String]]] =
    map(uri)(_.multiParams)

  /**
   * View of the head elements of the URI parameters in query string.
   *
   * In case a parameter has no value the map returns an empty string.
   *
   * @see multiParams
   */
  def params: F[Map[String, String]] =
    map(uri)(_.params)

  def connectionInfo: F[Option[Request.Connection]] =
    map(attributes)(_.get(Keys.ConnectionInfo))

  def remote: F[Option[InetSocketAddress]] =
    map(connectionInfo)(_.map(_.remote))

  def remoteAddr: F[Option[String]] =
    map(remote)(_.map(_.getHostString))

  def remoteHost: F[Option[String]] =
    map(remote)(_.map(_.getHostName))

  def remotePort: F[Option[Int]] =
    map(remote)(_.map(_.getPort))

  def server: F[Option[InetSocketAddress]] =
    map(connectionInfo)(_.map(_.local))

  def serverAddr: F[String]

  def serverPort: F[Int]

  /** Whether the Request was received over a secure medium */
  def isSecure: F[Option[Boolean]] =
    map(connectionInfo)(_.map(_.secure))

  def serverSoftware: F[ServerSoftware] =
    map(attributes)(_.get(Keys.ServerSoftware).getOrElse(ServerSoftware.Unknown))

  /** Helper method for decoding [[Request]]s
    *
    * Attempt to decode the [[Request]] and, if successful, execute the continuation to get a [[Response]].
    * If decoding fails, a BadRequest [[Response]] is generated.
    */
  def decode[A](f: A => Task[Response])(implicit decoder: EntityDecoder[A]): Task[Response] =
    decodeWith(decoder, strict = false)(f)

  /** Helper method for decoding [[Request]]s
    *
    * Attempt to decode the [[Request]] and, if successful, execute the continuation to get a [[Response]].
    * If decoding fails, a BadRequest [[Response]] is generated. If the decoder does not support the
    * [[MediaType]] of the [[Request]], a `UnsupportedMediaType` [[Response]] is generated instead.
    */
  def decodeStrict[A](f: A => Task[Response])(implicit decoder: EntityDecoder[A]): Task[Response] =
    decodeWith(decoder, true)(f)

  /** Like [[decode]], but with an explicit decoder.
    *
    * @param strict If strict, will return a [[Status.UnsupportedMediaType]] http Response if this message's
    *               [[MediaType]] is not supported by the provided decoder
    */
  def decodeWith[A](decoder: EntityDecoder[A], strict: Boolean)(f: A => Task[Response]): Task[Response]
}

trait ResponseOps[F[_]] extends Any with MessageOps[F] {

  /** Change the status of this response object
    *
    * @param status value to replace on the response object
    * @return a new response object with the new status code
    */
  def withStatus(status: Status): F[Self]

  /** Add a Set-Cookie header for the provided [[Cookie]] */
  def addCookie(cookie: Cookie): F[Self] =
    putHeaders(`Set-Cookie`(cookie))

  /** Add a Set-Cookie header with the provided values */
  def addCookie(name: String,
                content: String,
                expires: Option[Instant] = None): F[Self] =
    addCookie(Cookie(name, content, expires))

  /** Add a [[`Set-Cookie`]] which will remove the specified cookie from the client */
  def removeCookie(cookie: Cookie): F[Self] =
    putHeaders(`Set-Cookie`(cookie.copy(content = "", expires = Some(Instant.ofEpochSecond(0)), maxAge = Some(0))))

  /** Add a Set-Cookie which will remove the specified cookie from the client */
  def removeCookie(name: String): F[Self] =
    putHeaders(`Set-Cookie`(Cookie(name, "", expires = Some(Instant.ofEpochSecond(0)), maxAge = Some(0))
  ))
}

