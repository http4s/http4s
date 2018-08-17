package com.example.http4s.blaze

import cats.effect._
import org.http4s._
import org.http4s.client.blaze.Http1Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.io._

object ClientPostExample extends IOApp with Http4sClientDsl[IO] {
  def run(args: List[String]): IO[ExitCode] = {
    val req = POST(uri("https://duckduckgo.com/"), UrlForm("q" -> "http4s"))
    val responseBody = Http1Client[IO]().flatMap(_.expect[String](req))
    responseBody.flatMap(resp => IO(println(resp))).map(_ => ExitCode.Success)
  }
}
