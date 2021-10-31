/*
rule = Http4sUpstreamLibs
*/

package fix

import cats.effect._
import fs2._

object UpstreamLibsTest {
  Stream.eval(IO("hi")).map(_ => 3)
}
