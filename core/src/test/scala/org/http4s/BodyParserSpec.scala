package org.http4s

import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import play.api.libs.iteratee.Enumerator
import StatusLine._

import scala.concurrent.duration._

import java.io.{FileOutputStream,FileInputStream,File,InputStreamReader}
import concurrent.Await

/**
 * @author Bryce Anderson
 * Created on 2/14/13 at 8:44 PM
 */

class BodyParserSpec extends Specification with NoTimeConversions {
  "A File BodyParser" should {
    val binData: Array[Byte] = "Bytes 10111".getBytes

    def readFile(in: File): Array[Byte] = {
      val os = new FileInputStream(in)
      val data = new Array[Byte](in.length.asInstanceOf[Int])
      os.read(data)
      data
    }

    def readTextFile(in: File): String = {
      val os = new InputStreamReader(new FileInputStream(in))
      val data = new Array[Char](in.length.asInstanceOf[Int])
      os.read(data,0,in.length.asInstanceOf[Int])
      data.foldLeft("")(_ + _)
    }

    def mocServe(req: RequestPrelude, route: Route) = {
      val server = new MockServer(route)
      Await.result(server(req,Enumerator(binData)), 2 seconds)
    }

    "Write a text file from raw" in {
      val tmpFile = File.createTempFile("foo","bar")
      val response = mocServe(RequestPrelude(), {
        case req =>
          BodyParser.textFile(req, tmpFile){
            Ok("Hello")
          }
      })

      readTextFile(tmpFile) must_== new String(binData)
      response.statusLine must_== StatusLine.Ok
      response.body must_== "Hello".getBytes
    }

    "Write a binary file from raw" in {
      val tmpFile = File.createTempFile("foo","bar")
      val response = mocServe(RequestPrelude(), {
        case req =>
          BodyParser.binFile(tmpFile)(Ok("Hello"))
      })

      response.statusLine must_== StatusLine.Ok
      response.body must_== "Hello".getBytes
      readFile(tmpFile) must_== binData
    }


  }

}
