package org.http4s

import scalaz.concurrent.Task
import org.specs2.matcher._

/** This might be useful in a testkit spinoff.  Let's see what they do for us. */
trait Http4sMatchers extends Matchers {
  def runToStatus(expected: Status) =
    be_===(expected) ^^ { t: Task[Response] =>
      t.map(_.status).run aka "the response status"
    }

  def runToBody[A: EntityDecoder](expected: A) =
    be_===(expected) ^^ { t: Task[Response] =>
      t.flatMap(_.as[A]).run aka "the response body"
    }
}
