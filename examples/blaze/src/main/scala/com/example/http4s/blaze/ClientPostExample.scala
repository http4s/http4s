package com.example.http4s.blaze

import org.http4s._
import org.http4s.dsl._
import org.http4s.client._
import org.http4s.client.blaze.{defaultClient => client}

object ClientPostExample extends App {
  val req = POST(uri("https://duckduckgo.com/"), UrlForm("q" -> "http4s"))
  val responseBody = client.fetchAs[String](req)
  println(responseBody.run)
}
