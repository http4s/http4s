package org.http4s.zipkin.interpreters.collector

import argonaut.Argonaut._
import org.http4s.Uri.RegName
import org.http4s.client.Client
import org.http4s.zipkin.algebras.{CollectorInterpreter, CollectorOp}
import org.http4s.zipkin.interpreters._
import org.http4s.{MediaType, Method, Request, Uri}

import scalaz.concurrent.Task

final case class Http(client: Client) extends CollectorInterpreter {
  override def apply[A](fa: CollectorOp[A]): Task[A] = fa match {
    case CollectorOp.Send(zipkinInfo) =>
      val uri = Uri(
        authority = Option(Uri.Authority(
          host = RegName("localhost"),
          port = Option(9411)
        )),
        path = "/api/v1/spans"
      )
      val payload = List(zipkinInfo).asJson.spaces2
      val request = Request(Method.POST, uri)
        .withContentType(Option(MediaType.`application/json`))
        .withBody(payload)

      client
        .fetch(request)(response => Task.now(()))
        .runAsync(_ => ())

      Task.now(())

  }
}
