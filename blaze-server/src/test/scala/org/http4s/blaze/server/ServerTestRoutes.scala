/*
 * Copyright 2014 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s
package blaze
package server

import cats.data.Kleisli
import cats.effect._
import fs2.Stream._
import org.http4s.Charset._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers._
import org.http4s.implicits._
import org.typelevel.ci._

object ServerTestRoutes extends Http4sDsl[IO] {
  private val textPlain = `Content-Type`(MediaType.text.plain, `UTF-8`).toRaw1
  private val connClose = Connection(ci"close").toRaw1
  private val connKeep = Connection(ci"keep-alive").toRaw1
  private val chunked = `Transfer-Encoding`(TransferCoding.chunked).toRaw1

  def length(l: Long): Header.Raw = `Content-Length`.unsafeFromLong(l).toRaw1
  def testRequestResults: Seq[(String, (Status, Set[Header.Raw], String))] =
    Seq(
      ("GET /get HTTP/1.0\r\n\r\n", (Status.Ok, Set(length(3), textPlain), "get")),
      // ///////////////////////////////
      ("GET /get HTTP/1.1\r\n\r\n", (Status.Ok, Set(length(3), textPlain), "get")),
      // ///////////////////////////////
      (
        "GET /get HTTP/1.0\r\nConnection:keep-alive\r\n\r\n",
        (Status.Ok, Set(length(3), textPlain, connKeep), "get"),
      ),
      // ///////////////////////////////
      (
        "GET /get HTTP/1.1\r\nConnection:keep-alive\r\n\r\n",
        (Status.Ok, Set(length(3), textPlain), "get"),
      ),
      // ///////////////////////////////
      (
        "GET /get HTTP/1.1\r\nConnection:close\r\n\r\n",
        (Status.Ok, Set(length(3), textPlain, connClose), "get"),
      ),
      // ///////////////////////////////
      (
        "GET /get HTTP/1.0\r\nConnection:close\r\n\r\n",
        (Status.Ok, Set(length(3), textPlain, connClose), "get"),
      ),
      // ///////////////////////////////
      (
        "GET /get HTTP/1.1\r\nConnection:close\r\n\r\n",
        (Status.Ok, Set(length(3), textPlain, connClose), "get"),
      ),
      ("GET /chunked HTTP/1.1\r\n\r\n", (Status.Ok, Set(textPlain, chunked), "chunk")),
      // ///////////////////////////////
      (
        "GET /chunked HTTP/1.1\r\nConnection:close\r\n\r\n",
        (Status.Ok, Set(textPlain, chunked, connClose), "chunk"),
      ),
      // /////////////////////////////// Content-Length and Transfer-Encoding free responses for HTTP/1.0
      ("GET /chunked HTTP/1.0\r\n\r\n", (Status.Ok, Set(textPlain), "chunk")),
      // ///////////////////////////////
      (
        "GET /chunked HTTP/1.0\r\nConnection:Close\r\n\r\n",
        (Status.Ok, Set(textPlain, connClose), "chunk"),
      ),
      // ////////////////////////////// Requests with a body //////////////////////////////////////
      (
        "POST /post HTTP/1.1\r\nContent-Length:3\r\n\r\nfoo",
        (Status.Ok, Set(textPlain, length(4)), "post"),
      ),
      // ///////////////////////////////
      (
        "POST /post HTTP/1.1\r\nConnection:close\r\nContent-Length:3\r\n\r\nfoo",
        (Status.Ok, Set(textPlain, length(4), connClose), "post"),
      ),
      // ///////////////////////////////
      (
        "POST /post HTTP/1.0\r\nConnection:close\r\nContent-Length:3\r\n\r\nfoo",
        (Status.Ok, Set(textPlain, length(4), connClose), "post"),
      ),
      // ///////////////////////////////
      (
        "POST /post HTTP/1.0\r\nContent-Length:3\r\n\r\nfoo",
        (Status.Ok, Set(textPlain, length(4)), "post"),
      ),
      // ////////////////////////////////////////////////////////////////////
      (
        "POST /post HTTP/1.1\r\nTransfer-Encoding:chunked\r\n\r\n3\r\nfoo\r\n0\r\n\r\n",
        (Status.Ok, Set(textPlain, length(4)), "post"),
      ),
      // ///////////////////////////////
      (
        "POST /post HTTP/1.1\r\nConnection:close\r\nTransfer-Encoding:chunked\r\n\r\n3\r\nfoo\r\n0\r\n\r\n",
        (Status.Ok, Set(textPlain, length(4), connClose), "post"),
      ),
      (
        "POST /post HTTP/1.1\r\nTransfer-Encoding:chunked\r\n\r\n3\r\nfoo\r\n3\r\nbar\r\n0\r\n\r\n",
        (Status.Ok, Set(textPlain, length(4)), "post"),
      ),
      // ///////////////////////////////
      (
        "POST /post HTTP/1.1\r\nConnection:Close\r\nTransfer-Encoding:chunked\r\n\r\n3\r\nfoo\r\n0\r\n\r\n",
        (Status.Ok, Set(textPlain, length(4), connClose), "post"),
      ),
      // /////////////////////////////// Check corner cases //////////////////
      (
        "GET /twocodings HTTP/1.0\r\nConnection:Close\r\n\r\n",
        (Status.Ok, Set(textPlain, length(3), connClose), "Foo"),
      ),
      // /////////////// Work with examples that don't have a body //////////////////////
      ("GET /notmodified HTTP/1.1\r\n\r\n", (Status.NotModified, Set(), "")),
      (
        "GET /notmodified HTTP/1.0\r\nConnection: Keep-Alive\r\n\r\n",
        (Status.NotModified, Set(connKeep), ""),
      ),
    )

  def apply()(implicit cs: ContextShift[IO]): Kleisli[IO, Request[IO], Response[IO]] =
    HttpRoutes
      .of[IO] {
        case req if req.method == Method.GET && req.pathInfo == path"/get" =>
          Ok("get")

        case req if req.method == Method.GET && req.pathInfo == path"/chunked" =>
          Ok(eval(IO.shift *> IO("chu")) ++ eval(IO.shift *> IO("nk")))

        case req if req.method == Method.POST && req.pathInfo == path"/post" =>
          Ok("post")

        case req if req.method == Method.GET && req.pathInfo == path"/twocodings" =>
          Ok("Foo", `Transfer-Encoding`(TransferCoding.chunked))

        case req if req.method == Method.POST && req.pathInfo == path"/echo" =>
          Ok(emit("post") ++ req.bodyText)

        // Kind of cheating, as the real NotModified response should have a Date header representing the current? time?
        case req if req.method == Method.GET && req.pathInfo == path"/notmodified" =>
          NotModified()
      }
      .orNotFound
}
