package org.http4s
package netty

import play.api.libs.iteratee.{Enumeratee, Concurrent, Done}
import org.http4s._
import com.typesafe.scalalogging.slf4j.Logging
import concurrent.Future

object Example extends App with Logging {

  import concurrent.ExecutionContext.Implicits.global

  SimpleNettyServer()(ExampleRoute())

}
