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
package client

import cats.effect._
import cats.syntax.all._
import com.sun.net.httpserver._
import fs2._
import fs2.io._
import java.io.PrintWriter
import java.util.Arrays
import java.util.Locale
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.client.testroutes.GetRoutes
import org.http4s.dsl.io._
import org.http4s.multipart.{Multipart, Part}
import scala.concurrent.duration._

abstract class ClientRouteTestBattery(name: String) extends Http4sSuite with Http4sClientDsl[IO] {
  val timeout = 20.seconds

  def clientResource: Resource[IO, Client[IO]]

  val testHandler: HttpHandler = exchange =>
    (exchange.getRequestMethod match {
      case "GET" =>
        val path = exchange.getRequestURI.getPath
        GetRoutes.getPaths.get(path) match {
          case Some(r) =>
            r.flatMap(renderResponse(exchange, _))
          case None =>
            IO.blocking {
              exchange.sendResponseHeaders(404, -1L)
              exchange.close()
            }
        }
      case "POST" =>
        IO.blocking {
          exchange.sendResponseHeaders(200, 0L)
          val s = scala.io.Source.fromInputStream(exchange.getRequestBody).mkString
          val out = new PrintWriter(exchange.getResponseBody())
          out.print(s)
          out.flush()
          exchange.close()
        }
    }).start.unsafeRunAndForget()

  val server = resourceSuiteFixture("server", ServerScaffold[IO](1, false, testHandler))
  val client = resourceSuiteFixture("client", clientResource)

  test(s"$name Repeat a simple request") {
    val address = server().addresses.head
    val path = GetRoutes.SimplePath

    def fetchBody =
      client().toKleisli(_.as[String]).local { (uri: Uri) =>
        Request(uri = uri)
      }

    val url = Uri.fromString(s"http://${address.getHostName}:${address.getPort}$path").yolo
    (0 until 10).toVector
      .parTraverse(_ => fetchBody.run(url).map(_.length))
      .map(_.forall(_ =!= 0))
      .assert
  }

  test(s"$name POST an empty body") {
    val address = server().addresses.head
    val uri = Uri.fromString(s"http://${address.getHostName}:${address.getPort}/echo").yolo
    val req = POST(uri)
    val body = client().expect[String](req)
    body.assertEquals("")
  }

  test(s"$name POST a normal body") {
    val address = server().addresses.head
    val uri = Uri.fromString(s"http://${address.getHostName}:${address.getPort}/echo").yolo
    val req = POST("This is normal.", uri)
    val body = client().expect[String](req)
    body.assertEquals("This is normal.")
  }

  test(s"$name POST a chunked body".flaky) {
    val address = server().addresses.head
    val uri = Uri.fromString(s"http://${address.getHostName}:${address.getPort}/echo").yolo
    val req = POST(Stream("This is chunked.").covary[IO], uri)
    val body = client().expect[String](req)
    body.assertEquals("This is chunked.")
  }

  test(s"$name POST a multipart body") {
    val address = server().addresses.head
    val uri = Uri.fromString(s"http://${address.getHostName}:${address.getPort}/echo").yolo
    val multipart = Multipart[IO](Vector(Part.formData("text", "This is text.")))
    val req = POST(multipart, uri).withHeaders(multipart.headers)
    val body = client().expect[String](req)
    body.map(_.contains("This is text.")).assert
  }

  GetRoutes.getPaths.toList.foreach { case (path, expected) =>
    test(s"$name Execute GET $path") {
      val address = server().addresses.head
      val name = address.getHostName
      val port = address.getPort
      val req = Request[IO](uri = Uri.fromString(s"http://$name:$port$path").yolo)
      client()
        .run(req)
        .use(resp => expected.flatMap(checkResponse(resp, _)))
        .assert
    }
  }

  private def checkResponse(rec: Response[IO], expected: Response[IO]): IO[Boolean] = {
    // This isn't a generically safe normalization for all header, but
    // it's close enough for our purposes
    def normalizeHeaderValue(h: Header.Raw) =
      Header.Raw(h.name, h.value.replace("; ", ";").toLowerCase(Locale.ROOT))

    for {
      _ <- IO(rec.status).assertEquals(expected.status)
      body <- rec.body.compile.to(Array)
      expBody <- expected.body.compile.to(Array)
      _ <- IO(body).map(Arrays.equals(_, expBody)).assert
      headers = rec.headers.headers.map(normalizeHeaderValue)
      expectedHeaders = expected.headers.headers.map(normalizeHeaderValue)
      _ <- IO(expectedHeaders.diff(headers)).assertEquals(Nil)
      _ <- IO(rec.httpVersion).assertEquals(expected.httpVersion)
    } yield true
  }

  private def renderResponse(exchange: HttpExchange, resp: Response[IO]): IO[Unit] =
    IO(resp.headers.foreach { h =>
      if (h.name =!= headers.`Content-Length`.name)
        exchange.getResponseHeaders.add(h.name.toString, h.value)
    }) *>
      IO.blocking {
        // com.sun.net.httpserver warns on nocontent with a content lengt that is not -1
        val contentLength =
          if (resp.status.code == NoContent.code) -1L
          else resp.contentLength.getOrElse(0L)
        exchange.sendResponseHeaders(resp.status.code, contentLength)
      } *>
      resp.body
        .through(writeOutputStream[IO](IO.pure(exchange.getResponseBody), closeAfterUse = false))
        .compile
        .drain
        .guarantee(IO(exchange.close()))
}
