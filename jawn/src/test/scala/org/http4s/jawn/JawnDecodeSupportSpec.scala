package org.http4s
package jawn

import _root_.jawn.Facade

trait JawnDecodeSupportSpec[J] extends Http4sSpec with JawnInstances {
  def testJawnDecoder()(implicit facade: Facade[J]) {
    "jawnDecoder" should {
      "return right when the entity is valid" in {
        val resp = ResponseBuilder(Status.Ok, body = """{"valid": true}""").run
        jawnDecoder.decode(resp).run.run must beRightDisjunction
      }

      "return a ParseFailure when the entity is invalid" in {
        val resp = ResponseBuilder(Status.Ok, body = """garbage""").run
        jawnDecoder.decode(resp).run.run must beLeftDisjunction/*.like {
          case ParseFailure("Invalid JSON", _) => ok
        }*/
      }
    }
  }
}
