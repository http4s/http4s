// TODO fs2 port
/*
package com.example.http4s.blaze

import java.io._

import org.http4s._
import org.http4s.client._
import org.http4s.client.blaze.{defaultClient => client}
import org.http4s.dsl._
import org.http4s.headers._
import org.http4s.MediaType._
import org.http4s.multipart._
import EntityEncoder._
import org.http4s.Uri._

import scodec.bits._
import scalaz.concurrent.Task
import scalaz.stream._

object ClientMultipartPostExample {

  val bottle = getClass().getResource("/beerbottle.png")
  
  def go:String = {
    // n.b. This service does not appear to gracefully handle chunked requests.
    val url    = Uri(
      scheme    = Some("http".ci),
      authority = Some(Authority(host = RegName("www.posttestserver.com"))),
      path      = "/post.php?dir=http4s")
      
    val multipart = Multipart(Vector(
                                Part.formData("text", "This is text.")
                                ,Part.fileData("BALL", bottle, `Content-Type`(MediaType.`image/png`))
                                ))

    val request = Method.POST(url,multipart).map(_.replaceAllHeaders(multipart.headers))
    client.expect[String](request).run
  }

  def main(args: Array[String]): Unit = println(go)
}
 */
