package org.http4s.client.blaze
package Main

import org.http4s.Uri
import org.http4s.EntityDecoder._

import scalaz._

object Main {

  def main(args: Array[String]): Unit = {

    val \/-(uri) = Uri.fromString("http://www.cnn.com/")

    val resp = defaultClient(uri).flatMap{ resp =>
      resp.as[String]
    }

    println(resp.run)
  }

}
