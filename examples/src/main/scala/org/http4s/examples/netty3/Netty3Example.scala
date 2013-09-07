package org.http4s
package netty3

import play.api.libs.iteratee.{Enumeratee, Concurrent, Done}
import org.http4s._
import com.typesafe.scalalogging.slf4j.Logging
import concurrent.Future

object Netty3Example extends App with Logging {

  import concurrent.ExecutionContext.Implicits.global

  SimpleNettyServer("/http4s")(ExampleRoute())

}
