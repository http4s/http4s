package org.http4s
package jawn

import org.http4s.json.JsonSupport
import org.specs2.execute.PendingUntilFixed

trait JawnDecodeSupportSpec[J] extends Http4sSpec with JsonSupport[J] with PendingUntilFixed {
  "json decoder" should {
    "return right when the entity is valid" in {
      val resp = ResponseBuilder(Status.Ok, body = """{"valid": true}""").run
      json.decode(resp).run.run must beRightDisjunction
    }

    "return a ParseFailure when the entity is invalid" in {
      val resp = ResponseBuilder(Status.Ok, body = """garbage""").run
      json.decode(resp).run.run must beLeftDisjunction.like {
        case ParseFailure("Invalid JSON", _) => ok
      }
    }
  }
}
