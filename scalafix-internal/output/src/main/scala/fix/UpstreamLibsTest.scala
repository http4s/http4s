package fix

import cats.effect._
import fs2._

object UpstreamLibsTest {
  Stream.eval(IO("hi")).as(3)
}
