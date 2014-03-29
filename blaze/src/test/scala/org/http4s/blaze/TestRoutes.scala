package org.http4s
package blaze

import org.http4s.Status.Ok


/**
 * Created by brycea on 3/28/14.
 */
object TestRoutes {

  val empty: Seq[(String,String)] = Nil

  def testRequestResults:Seq[(String, (Status,Set[(String,String)], String))] = Seq {
    ("GET /get HTTP/1.0\r\n\r\n", (Status.Ok,
      Set(("Content-Length","3"), ("Content-Type","text/plain; charset=UTF-8")),
    "get"))
    /////////////////////////////////
  }

  def apply(): HttpService = {
    case req if req.requestMethod == Method.Get && req.pathInfo.startsWith("/get") =>
      Ok("get")
  }

}
