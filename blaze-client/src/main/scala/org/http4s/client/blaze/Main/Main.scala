package org.http4s.client.blaze
package Main

import org.http4s.Uri
import org.http4s.EntityDecoder._

import scalaz._
import scalaz.concurrent.Task

object Main {

  def main(args: Array[String]): Unit = {

    val \/-(uri) = Uri.fromString("http://www.cnn.com/")

    val reqs = List.fill(2)(defaultClient(uri))

    val resp = Task.gatherUnordered(reqs).run

    Thread.sleep(10000)

    println(resp.head.as[String].run)
  }

}
