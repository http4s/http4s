package org.http4s.examples.cooldsl

import org.http4s.cooldsl._
import org.http4s.Method._
import org.http4s.cooldsl.swagger.SwaggerSupport

/**
 * Created by Bryce Anderson on 5/9/14.
 */
class MyService extends CoolService with SwaggerSupport {

  Get / "hello" / parse[String] ^ "Says hello" |>>> { (s: String) => s"Hello world: $s" }

  for {
    i <- 0 until 20
    j <- 0 until 20
    k <- 0 until 20
    z <- 0 until 20 } {
    Get / s"route_$i" / s"route_$j" / s"route_$k" / s"route_$z" / parse[String] |>>> { (s: String) =>
      s"Route ($i, $j, $k, $z), $s"
    }
  }

}
