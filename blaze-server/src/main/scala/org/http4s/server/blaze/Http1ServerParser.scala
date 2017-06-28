package org.http4s
package server.blaze

import java.nio.ByteBuffer

import scala.collection.mutable.ListBuffer
import scala.util.Either

import cats.data._
import cats.implicits._
import fs2._
import org.log4s.Logger
import org.http4s.ParseResult.parseResultMonad

private final class Http1ServerParser(logger: Logger,
                                      maxRequestLine: Int,
                                      maxHeadersLen: Int)
  extends blaze.http.http_parser.Http1ServerParser(maxRequestLine, maxHeadersLen, 2*1024) {

  private var uri: String = null
  private var method: String = null
  private var minor: Int = -1
  private var major: Int = -1
  private val headers = new ListBuffer[Header]

  def minorVersion(): Int = minor

  def doParseRequestLine(buff: ByteBuffer): Boolean = parseRequestLine(buff)

  def doParseHeaders(buff: ByteBuffer): Boolean = parseHeaders(buff)

  def doParseContent(buff: ByteBuffer): Option[ByteBuffer] = Option(parseContent(buff))

  def collectMessage(body: EntityBody, attrs: AttributeMap): Either[(ParseFailure,HttpVersion), Request] = {
    val h = Headers(headers.result())
    headers.clear()
    val protocol = if (minorVersion() == 1) HttpVersion.`HTTP/1.1` else HttpVersion.`HTTP/1.0`

    val attrsWithTrailers =
      if (minorVersion() == 1 && isChunked) {
        attrs.put(Message.Keys.TrailerHeaders, Task.suspend {
          if (!contentComplete()) {
            Task.fail(new IllegalStateException("Attempted to collect trailers before the body was complete."))
          }
          else Task.now(Headers(headers.result()))
        })
      } else attrs // Won't have trailers without a chunked body

    // (for {
    //   method <- Method.fromString(this.method)
    //   uri <- Uri.requestTarget(this.uri)
    // } yield Request(method, uri, protocol, h, body, attrsWithTrailers)
    // ).leftMap(_ -> protocol)

    Method.fromString(this.method) flatMap { method =>
    Uri.requestTarget(this.uri) map { uri =>
      Request(method, uri, protocol, h, body, attrsWithTrailers)
    }} leftMap (_ -> protocol)

  }

  override def submitRequestLine(methodString: String, uri: String, scheme: String, majorversion: Int, minorversion: Int): Boolean = {
    logger.trace(s"Received request($methodString $uri $scheme/$majorversion.$minorversion)")
    this.uri = uri
    this.method = methodString
    this.major = majorversion
    this.minor = minorversion
    false
  }

  /////////////////// Stateful methods for the HTTP parser ///////////////////
  override protected def headerComplete(name: String, value: String) = {
    logger.trace(s"Received header '$name: $value'")
    headers += Header(name, value)
    false
  }

  override def reset(): Unit = {
    uri = null
    method = null
    minor = -1
    major = -1
    headers.clear()
    super.reset()
  }
}
