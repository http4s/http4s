package org.http4s.zipkin.core.interpreters.collector

import argonaut.Argonaut._
import org.http4s.Uri.RegName
import org.http4s.client.Client
import org.http4s.zipkin.core.algebras.Collector
import org.http4s.zipkin.core.interpreters._
import org.http4s.zipkin.core.ZipkinInfo
import org.http4s.{MediaType, Method, Request, Uri}

import scalaz.concurrent.Task

final case class Http(client: Client) extends Collector {

  override def send(zipkinInfo: ZipkinInfo): Unit = {
    val uri = Uri(
      authority = Option(Uri.Authority(
        host = RegName("localhost"),
        port = Option(9411)
      )),
      path = "/api/v1/spans"
    )
    val payload = List(zipkinInfo).asJson.nospaces
    val request = Request(Method.POST, uri)
      .withContentType(Option(MediaType.`application/json`))
      .withBody(payload)

    client
      // Fire and forget.
      .fetch(request)(response => Task.now(()))
      // We don't care about failure.
      .runAsync(_ => ())
  }

}
