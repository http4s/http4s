package com.example.http4s.blaze

import cats.effect._
import org.http4s._
import org.http4s.client._
import org.http4s.client.blaze.{defaultClient => client}
import org.http4s.dsl._

object ClientPostExample extends App {
  val req = POST(uri("https://duckduckgo.com/"), UrlForm("q" -> "http4s"))
  val responseBody = client[IO].expect[String](req)
  println(responseBody.unsafeRunSync())
}

