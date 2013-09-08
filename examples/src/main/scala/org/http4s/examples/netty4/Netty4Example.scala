package org.http4s
package netty4

import play.api.libs.iteratee.{Enumeratee, Concurrent, Done}
import org.http4s._
import com.typesafe.scalalogging.slf4j.Logging
import org.http4s.util.middleware.URITranslation

object Netty4Example extends App with Logging {

  import concurrent.ExecutionContext.Implicits.global

  SimpleNettyServer()(URITranslation.TranslateRoot("/http4s")(ExampleRoute()))

}
