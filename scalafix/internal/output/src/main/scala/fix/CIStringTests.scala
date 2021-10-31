

import cats.effect.IO
import fs2.Stream

class CIStringTests {
  Stream.eval(IO("hi")).as(3)
}

case object Bar