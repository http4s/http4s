package org.http4s
package blaze

import http.http_parser.Http1ClientParser
import scala.collection.mutable.ListBuffer
import java.nio.ByteBuffer


import java.nio.charset.StandardCharsets
import scodec.bits.ByteVector

class ResponseParser extends Http1ClientParser {

  val headers = new ListBuffer[(String,String)]

  var code: Int = -1
  var reason = ""
  var scheme = ""
  var majorversion = -1
  var minorversion = -1

  def parseResponse(buff: Seq[ByteBuffer]): (Status, Set[Header], String) = {
    val b = ByteBuffer.wrap(buff.map(b => ByteVector(b).toArray).toArray.flatten)

    parseResponseLine(b)
    parseHeaders(b)

    if (!headersComplete()) sys.error("Headers didn't complete!")

    val body = new ListBuffer[ByteBuffer]
    while(!this.contentComplete() && b.hasRemaining) {
      body += parseContent(b)
    }

    val bp = new String(body.map(ByteVector(_)).foldLeft(ByteVector.empty)((c1,c2) => c1 ++ c2).toArray,
                                StandardCharsets.US_ASCII)

    val headers = this.headers.result.map{case (k,v) => Header(k,v): Header}.toSet

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
