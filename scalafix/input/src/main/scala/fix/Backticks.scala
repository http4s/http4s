/*
rule = v0_22
*/
package fix

import org.http4s._

object Backticks {
  val httpVersions = Set(
    HttpVersion.`HTTP/1.0`,
    HttpVersion.`HTTP/1.1`,
    HttpVersion.`HTTP/2.0`,
  )
}
