package fix

import org.http4s._

object Backticks {
  val httpVersions = Set(
    HttpVersion.HTTP_1_0,
    HttpVersion.HTTP_1_1,
    HttpVersion.HTTP_2_0,
  )
}
