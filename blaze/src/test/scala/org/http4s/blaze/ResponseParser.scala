package org.http4s.blaze

/**
 * Created by Bryce Anderson on 3/28/14.
 */

import http_parser.Http1ClientParser
import scala.collection.mutable.ListBuffer
import org.http4s._
import java.nio.ByteBuffer
import org.http4s.Response

import scalaz.stream.Process.emit
import java.nio.charset.StandardCharsets

class ResponseParser extends Http1ClientParser {

  val headers = new ListBuffer[(String,String)]

  var code: Int = -1
  var reason = ""
  var scheme = ""
  var majorversion = -1
  var minorversion = -1

  def parseResponse(buff: Seq[ByteBuffer]): (Status, Set[(String,String)], String) = {
    val b = ByteBuffer.wrap(buff.map(b => BodyChunk(b).toArray).toArray.flatten)

    parseResponseLine(b)
    parseHeaders(b)

    val body = new ListBuffer[ByteBuffer]
    while(!this.contentComplete()) {
      body += parseContent(b)
    }

    val bp = new String(body.map(BodyChunk(_)).foldLeft(BodyChunk())((c1,c2) => c1 ++ c2).toArray,
                                StandardCharsets.US_ASCII)

    val headers = this.headers.result.toSet

    (Status.apply(this.code, this.reason), headers, bp)
  }


  override def headerComplete(name: String, value: String): Boolean = {
    headers += ((name,value))
    false
  }

  override def submitResponseLine(code: Int,
                                  reason: String,
                                  scheme: String,
                                  majorversion: Int,
                                  minorversion: Int): Unit = {
    this.code = code
    this.reason = reason
    this.majorversion = majorversion
    this.minorversion = minorversion
  }
}

object ResponseParser {
  def apply(buff: Seq[ByteBuffer]) = new ResponseParser().parseResponse(buff)
  def apply(buff: ByteBuffer) = new ResponseParser().parseResponse(Seq(buff))
}
