package org.http4s
package server.blaze

import java.nio.ByteBuffer

import org.log4s.Logger

import scala.collection.mutable.ListBuffer
import scalaz.\/


private final class Http1ServerParser(logger: Logger) extends blaze.http.http_parser.Http1ServerParser {

  private var uri: String = null
  private var method: String = null
  private var minor: Int = -1
  private var major: Int = -1
  private val headers = new ListBuffer[Header]

  def minorVersion(): Int = minor

  def doParseRequestLine(buff: ByteBuffer): Boolean = parseRequestLine(buff)

  def doParseHeaders(buff: ByteBuffer): Boolean = parseHeaders(buff)

  def doParseContent(buff: ByteBuffer): Option[ByteBuffer] = Option(parseContent(buff))

  def collectMessage(body: EntityBody, attrs: AttributeMap): (ParseFailure,HttpVersion)\/Request = {
    val h = Headers(headers.result())
    headers.clear()
    val protocol = if (minorVersion() == 1) HttpVersion.`HTTP/1.1` else HttpVersion.`HTTP/1.0`

    (for {
      method <- Method.fromString(this.method)
      uri <- Uri.requestTarget(this.uri)
    } yield Request(method, uri, protocol, h, body, attrs)
    ).leftMap(_ -> protocol)
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
