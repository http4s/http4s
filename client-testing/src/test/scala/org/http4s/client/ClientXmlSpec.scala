package org.http4s
package client

import cats.effect._
import cats.implicits._
import org.http4s.Method.GET
import org.http4s.Status.Ok
import scala.xml.Elem

class ClientXmlSpec extends Http4sSpec {
  implicit val decoder = scalaxml.xml[IO]
  val body = <html><h1>h1</h1></html>
  val xml = s"""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>$body"""
  val app = HttpApp.pure(Response[IO](Ok).withEntity(xml))
  val client = Client.fromHttpApp(app)

  "mock client" should {
    "read xml body before dispose" in {
      client.expect[Elem](Request[IO](GET)).unsafeRunSync() must_== body
    }
    "read xml body in parallel" in {
      // https://github.com/http4s/http4s/issues/1209
      val resp = (0 to 5).toList
        .parTraverse(_ => client.expect[Elem](Request[IO](GET)))
        .unsafeRunSync()
      resp.map(_ must_== body)
    }
  }
}
