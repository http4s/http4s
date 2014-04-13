package org.http4s
package blaze

import org.http4s.Status.Ok
import scalaz.stream.Process._
import scalaz.concurrent.Task
import java.nio.charset.StandardCharsets


/**
 * Created by Bryce Anderson on 3/28/14.
 */
object TestRoutes {

  val empty: Seq[(String,String)] = Nil

  def testRequestResults:Seq[(String, (Status,Set[(String,String)], String))] = Seq(
    ("GET /get HTTP/1.0\r\n\r\n", (Status.Ok,
      Set(("Content-Length","3"), ("Content-Type","text/plain; charset=UTF-8")),
    "get")),
    /////////////////////////////////
    ("GET /get HTTP/1.1\r\n\r\n", (Status.Ok,
      Set(("Content-Length","3"), ("Content-Type","text/plain; charset=UTF-8")),
      "get")),
    /////////////////////////////////
    ("GET /get HTTP/1.0\r\nConnection:Keep-Alive\r\n\r\n", (Status.Ok,
      Set(("Content-Length","3"), ("Content-Type","text/plain; charset=UTF-8"), ("Connection","Keep-Alive")),
      "get")),
    /////////////////////////////////
    ("GET /get HTTP/1.1\r\nConnection:Keep-Alive\r\n\r\n", (Status.Ok,
      Set(("Content-Length","3"), ("Content-Type","text/plain; charset=UTF-8")),
      "get")),
    /////////////////////////////////
    ("GET /get HTTP/1.1\r\nConnection:Close\r\n\r\n", (Status.Ok,
      Set(("Content-Length","3"), ("Content-Type","text/plain; charset=UTF-8"), ("Connection","Close")),
      "get")),
    /////////////////////////////////
    ("GET /get HTTP/1.0\r\nConnection:Close\r\n\r\n", (Status.Ok,
      Set(("Content-Length","3"), ("Content-Type","text/plain; charset=UTF-8"), ("Connection","Close")),
      "get")),
    /////////////////////////////////
    ("GET /get HTTP/1.1\r\nConnection:Close\r\n\r\n", (Status.Ok,
      Set(("Content-Length","3"), ("Content-Type","text/plain; charset=UTF-8"), ("Connection","Close")),
      "get")),
    //////////////////////////////////////////////////////////////////////
    ("GET /chunked HTTP/1.1\r\n\r\n", (Status.Ok,
      Set(("Content-Type","text/plain; charset=UTF-8"), ("Transfer-Encoding","chunked")),
      "chunk")),
    /////////////////////////////////
    ("GET /chunked HTTP/1.1\r\nConnection:Close\r\n\r\n", (Status.Ok,
      Set(("Content-Type","text/plain; charset=UTF-8"), ("Transfer-Encoding","chunked"), ("Connection","Close")),
      "chunk")),
    ///////////////////////////////// TODO: blaze parser doesn't support a response without content-length
//    ("GET /chunked HTTP/1.0\r\n\r\n", (Status.Ok,
//      Set(("Content-Type","text/plain; charset=UTF-8"), ("Content-Length","5")),
//      "chunk")),
//    /////////////////////////////////
//    ("GET /chunked HTTP/1.0\r\nConnection:Close\r\n\r\n", (Status.Ok,
//      Set(("Content-Type","text/plain; charset=UTF-8"), ("Connection","Close")),
//      "chunk")),
    //////////////////////////////// Requests with a body //////////////////////////////////////
    ("POST /post HTTP/1.1\r\nContent-Length:3\r\n\r\nfoo", (Status.Ok,
      Set(("Content-Type","text/plain; charset=UTF-8"), ("Content-Length","4")),
      "post")),
    /////////////////////////////////
    ("POST /post HTTP/1.1\r\nConnection:Close\r\nContent-Length:3\r\n\r\nfoo", (Status.Ok,
      Set(("Content-Type","text/plain; charset=UTF-8"), ("Content-Length","4"), ("Connection","Close")),
      "post")),
    /////////////////////////////////
    ("POST /post HTTP/1.0\r\nConnection:Close\r\nContent-Length:3\r\n\r\nfoo", (Status.Ok,
      Set(("Content-Type","text/plain; charset=UTF-8"), ("Content-Length","4"), ("Connection","Close")),
      "post")),
    /////////////////////////////////
    ("POST /post HTTP/1.0\r\nContent-Length:3\r\n\r\nfoo", (Status.Ok,
      Set(("Content-Type","text/plain; charset=UTF-8"), ("Content-Length","4")),
      "post")),
    //////////////////////////////////////////////////////////////////////
    ("POST /post HTTP/1.1\r\nTransfer-Encoding:chunked\r\n\r\n3\r\nfoo\r\n0\r\n\r\n", (Status.Ok,
      Set(("Content-Type","text/plain; charset=UTF-8"), ("Content-Length","4")),
      "post")),
    /////////////////////////////////
    ("POST /post HTTP/1.1\r\nConnection:Close\r\nTransfer-Encoding:chunked\r\n\r\n3\r\nfoo\r\n0\r\n\r\n", (Status.Ok,
      Set(("Content-Type","text/plain; charset=UTF-8"), ("Content-Length","4"), ("Connection","Close")),
      "post")),
    ("POST /post HTTP/1.1\r\nTransfer-Encoding:chunked\r\n\r\n3\r\nfoo\r\n3\r\nbar\r\n0\r\n\r\n", (Status.Ok,
      Set(("Content-Type","text/plain; charset=UTF-8"), ("Content-Length","4")),
      "post")),
    /////////////////////////////////
    ("POST /post HTTP/1.1\r\nConnection:Close\r\nTransfer-Encoding:chunked\r\n\r\n3\r\nfoo\r\n0\r\n\r\n", (Status.Ok,
      Set(("Content-Type","text/plain; charset=UTF-8"), ("Content-Length","4"), ("Connection","Close")),
      "post"))
  )

  def apply(): HttpService = {
    case req if req.requestMethod == Method.Get && req.pathInfo == "/get" => Ok("get")
    case req if req.requestMethod == Method.Get && req.pathInfo == "/chunked" => Ok(await(Task("chunk"))(emit))
    case req if req.requestMethod == Method.Post && req.pathInfo == "/post" => Ok("post")

    case req if req.requestMethod == Method.Post && req.pathInfo == "/echo" =>
      Ok(emit("post") ++ req.body.map(_.decodeString(StandardCharsets.UTF_8)))
  }

}
