package org.http4s
package argonaut

import _root_.argonaut.{Argonaut, Json}
import _root_.jawn.support.argonaut.Parser.facade
import org.http4s.Header.`Content-Type`

trait ArgonautInstances {
  implicit val json: EntityDecoder[Json] = jawn.jawnDecoder(facade)

  implicit val jsonEncoder: EntityEncoder[Json] =
    EntityEncoder[String].contramap[Json] { json =>
      // TODO naive implementation materializes to a String.
      // Look into replacing after https://github.com/non/jawn/issues/6#issuecomment-65018736
      Argonaut.nospace.pretty(json)
    }.withContentType(`Content-Type`(MediaType.`application/json`))
}
