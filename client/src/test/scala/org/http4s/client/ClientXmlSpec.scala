package org.http4s
package client

import cats.effect._
import cats.implicits._
import fs2.async.parallelTraverse
import org.http4s.Method.GET
import org.http4s.Status.Ok
import scala.xml.Elem

class ClientXmlSpec extends Http4sSpec {
  implicit val decoder = scalaxml.xml[IO]
  val body = <html><h1>h1</h1></html>
  val xml = s"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>$body"""
  val service = HttpService[IO] {
    case _ =>
      Response[IO](Ok).withBody(xml).pure[IO]
  }
  val client = Client.fromHttpService(service)

  "mock client" should {
    "read xml body before dispose" in {
      client.expect[Elem](Request[IO](GET)).unsafeRunSync() must_== body
    }
    "read xml body in parallel" in {
      // https://github.com/http4s/http4s/issues/1209
      val resp = parallelTraverse((0 to 5).toList)(_ => client.expect[Elem](Request[IO](GET)))
        .unsafeRunSync()
      resp.map(_ must_== body)
    }
  }
}
