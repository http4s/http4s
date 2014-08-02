package org.http4s.server.blaze

import org.http4s.Header._
import org.http4s.Http4s._
import org.http4s.Status.Ok
import org.http4s._
import org.http4s.server.HttpService

import scalaz.concurrent.Task
import scalaz.stream.Process._

object ServerTestRoutes {

  val textPlain: Header = `Content-Type`.`text/plain`.withCharset(Charset.`UTF-8`)

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
    ///////////////////////////////// Paths without an explicit content encoding should cache and give a length header
    ("GET /cachechunked HTTP/1.1\r\n\r\n", (Status.Ok,
      Set(textPlain, length(5)),
      "chunk")),
    /////////////////////////////////
    ("GET /cachechunked HTTP/1.1\r\nConnection:close\r\n\r\n", (Status.Ok,
      Set(textPlain, length(5), connClose),
      "chunk")),
    ///////////////////////////////// Content-Length and Transfer-Encoding free responses for HTTP/1.0
    ("GET /chunked HTTP/1.0\r\n\r\n", (Status.Ok,
      Set(textPlain), "chunk")),
    /////////////////////////////////
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
      (Status.Ok, Set(textPlain, length(3), connClose), "Foo")),
    ///////////////// Work with examples that don't have a body //////////////////////
    ("GET /notmodified HTTP/1.1\r\n\r\n",
      (Status.NotModified, Set[Header](), "")),
    ("GET /notmodified HTTP/1.0\r\nConnection: Keep-Alive\r\n\r\n",
      (Status.NotModified, Set[Header](connKeep), ""))
  )

  def apply(): HttpService = {
    case req if req.requestMethod == Method.Get && req.pathInfo == "/get" => Ok("get")
    case req if req.requestMethod == Method.Get && req.pathInfo == "/chunked" =>
      Ok(eval(Task("chu")) ++ eval(Task("nk"))).addHeader(Header.`Transfer-Encoding`(TransferCoding.chunked))

    case req if req.requestMethod == Method.Get && req.pathInfo == "/cachechunked" =>
      Ok(eval(Task("chu")) ++ eval(Task("nk")))

    case req if req.requestMethod == Method.Post && req.pathInfo == "/post" => Ok("post")

    case req if req.requestMethod == Method.Get && req.pathInfo == "/twocodings" =>
      Ok("Foo").addHeaders(`Transfer-Encoding`(TransferCoding.chunked))

    case req if req.requestMethod == Method.Post && req.pathInfo == "/echo" =>
      Ok(emit("post") ++ req.body.map(bs => new String(bs.toArray, req.charset.nioCharset)))

      // Kind of cheating, as the real NotModified response should have a Date header representing the current? time?
    case req if req.requestMethod == Method.Get && req.pathInfo == "/notmodified" => Task.now(Response(NotModified))
  }

}
