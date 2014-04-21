package org.http4s
package blaze

import org.http4s.Status.Ok
import scalaz.stream.Process._
import scalaz.concurrent.Task
import java.nio.charset.StandardCharsets

import org.http4s.Http4s._

import org.http4s.Header._
import org.http4s.MediaRange._
import org.http4s.MediaType._


/**
 * Created by Bryce Anderson on 3/28/14.
 */
object TestRoutes {

  val textPlain: Header = `Content-Type`.`text/plain`.withCharset(CharacterSet.`UTF-8`)

  val connClose = Connection("close".ci)
  val connKeep = Connection("keep-alive".ci)
  val chunked = `Transfer-Encoding`(TransferCoding.chunked)

  def length(i: Int) = `Content-Length`(i)

  def testRequestResults: Seq[(String, (Status,Set[Header], String))] = Seq(
    ("GET /get HTTP/1.0\r\n\r\n", (Status.Ok,
      Set(length(3), textPlain), "get")),
    /////////////////////////////////
    ("GET /get HTTP/1.1\r\n\r\n", (Status.Ok,
      Set(length(3), textPlain),
      "get")),
    /////////////////////////////////
    ("GET /get HTTP/1.0\r\nConnection:keep-alive\r\n\r\n", (Status.Ok,
      Set(length(3), textPlain, connKeep),
      "get")),
    /////////////////////////////////
    ("GET /get HTTP/1.1\r\nConnection:keep-alive\r\n\r\n", (Status.Ok,
      Set(length(3), textPlain),
      "get")),
    /////////////////////////////////
    ("GET /get HTTP/1.1\r\nConnection:close\r\n\r\n", (Status.Ok,
      Set(length(3), textPlain, connClose),
      "get")),
    /////////////////////////////////
    ("GET /get HTTP/1.0\r\nConnection:close\r\n\r\n", (Status.Ok,
      Set(length(3), textPlain, connClose),
      "get")),
    /////////////////////////////////
    ("GET /get HTTP/1.1\r\nConnection:close\r\n\r\n", (Status.Ok,
      Set(length(3), textPlain, connClose),
      "get")),
    //////////////////////////////////////////////////////////////////////
    ("GET /chunked HTTP/1.1\r\n\r\n", (Status.Ok,
      Set(textPlain, chunked),
      "chunk")),
    /////////////////////////////////
    ("GET /chunked HTTP/1.1\r\nConnection:close\r\n\r\n", (Status.Ok,
      Set(textPlain, chunked, connClose),
      "chunk")),
    ///////////////////////////////// Content-Length and Transfer-Encoding free responses for HTTP/1.0
    ("GET /chunked HTTP/1.0\r\n\r\n", (Status.Ok,
      Set(textPlain), "chunk")),
//    /////////////////////////////////
    ("GET /chunked HTTP/1.0\r\nConnection:Close\r\n\r\n", (Status.Ok,
      Set(textPlain, connClose), "chunk")),
    //////////////////////////////// Requests with a body //////////////////////////////////////
    ("POST /post HTTP/1.1\r\nContent-Length:3\r\n\r\nfoo", (Status.Ok,
      Set(textPlain, length(4)),
      "post")),
    /////////////////////////////////
    ("POST /post HTTP/1.1\r\nConnection:close\r\nContent-Length:3\r\n\r\nfoo", (Status.Ok,
      Set(textPlain, length(4), connClose),
      "post")),
    /////////////////////////////////
    ("POST /post HTTP/1.0\r\nConnection:close\r\nContent-Length:3\r\n\r\nfoo", (Status.Ok,
      Set(textPlain, length(4), connClose),
      "post")),
    /////////////////////////////////
    ("POST /post HTTP/1.0\r\nContent-Length:3\r\n\r\nfoo", (Status.Ok,
      Set(textPlain, length(4)),
      "post")),
    //////////////////////////////////////////////////////////////////////
    ("POST /post HTTP/1.1\r\nTransfer-Encoding:chunked\r\n\r\n3\r\nfoo\r\n0\r\n\r\n", (Status.Ok,
      Set(textPlain, length(4)),
      "post")),
    /////////////////////////////////
    ("POST /post HTTP/1.1\r\nConnection:close\r\nTransfer-Encoding:chunked\r\n\r\n3\r\nfoo\r\n0\r\n\r\n", (Status.Ok,
      Set(textPlain, length(4), connClose),
      "post")),
    ("POST /post HTTP/1.1\r\nTransfer-Encoding:chunked\r\n\r\n3\r\nfoo\r\n3\r\nbar\r\n0\r\n\r\n", (Status.Ok,
      Set(textPlain, length(4)),
      "post")),
    /////////////////////////////////
    ("POST /post HTTP/1.1\r\nConnection:Close\r\nTransfer-Encoding:chunked\r\n\r\n3\r\nfoo\r\n0\r\n\r\n", (Status.Ok,
      Set(textPlain, length(4), connClose),
      "post")),
    ///////////////////////////////// Check corner cases //////////////////
    ("GET /twocodings HTTP/1.0\r\nConnection:Close\r\n\r\n",
      (Status.Ok, Set(textPlain, length(3), connClose), "Foo"))
  )

  def apply(): HttpService = {
    case req if req.requestMethod == Method.Get && req.pathInfo == "/get" => Ok("get")
    case req if req.requestMethod == Method.Get && req.pathInfo == "/chunked" => Ok(await(Task("chunk"))(emit))
    case req if req.requestMethod == Method.Post && req.pathInfo == "/post" => Ok("post")

    case req if req.requestMethod == Method.Get && req.pathInfo == "/twocodings" =>
      Ok("Foo").addHeaders(`Transfer-Encoding`(TransferCoding.chunked))

    case req if req.requestMethod == Method.Post && req.pathInfo == "/echo" =>
      Ok(emit("post") ++ req.body.map(bs => new String(bs.toArray, req.charset.charset)))
  }

}
