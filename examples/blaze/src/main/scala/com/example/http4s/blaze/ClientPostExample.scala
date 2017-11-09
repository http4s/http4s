package com.example.http4s.blaze

import cats.effect._
import org.http4s._
import org.http4s.client.blaze.Http1Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.io._

object ClientPostExample extends App with Http4sClientDsl[IO] {
  val req = POST(uri("https://duckduckgo.com/"), UrlForm("q" -> "http4s"))
  val responseBody = Http1Client[IO]().flatMap(_.expect[String](req))
  println(responseBody.unsafeRunSync())
}
