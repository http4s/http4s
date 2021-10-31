/*
rule = Simplify
*/

import cats.effect.IO
import fs2.Stream

class CIStringTests {
  Stream.eval(IO("hi")).map(_ => 3)
}

final case object Bar
