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

  implicit val mpe: EntityEncoder[Multipart] = MultipartEntityEncoder

  val textFormData:String => String => FormData = name => value =>
    FormData(Name(name),Entity(Process.emit(ByteVector(value.getBytes))))

  val fileFormData:String => InputStream => FormData = {name => stream => 
    
    val bitVector = BitVector.fromInputStream(stream)
    FormData(Name(name),
             Entity(body = Process.emit(ByteVector(bitVector.toBase64.getBytes))),
             Some(`Content-Type`(`image/png`)))
  }  
                                                
  val bottle = getClass().getResourceAsStream("/beerbottle.png")
  
  def go:String = {
    val url    = Uri(
      scheme    = Some("http".ci),
      authority = Some(Authority(host = RegName("www.posttestserver.com"))),
      path      = "/post.php?dir=http4s")
      
    val multipart = Multipart(textFormData("text")("This is text.") ::
                              fileFormData("BALL")(bottle) ::
                           Nil)
   val request = Method.POST(url,multipart).map(_.withHeaders(multipart.headers))
   val responseBody = client(request).as[String]
    
   client(request).as[String].run
  }

  def main(args: Array[String]): Unit = println(go)
  

}
