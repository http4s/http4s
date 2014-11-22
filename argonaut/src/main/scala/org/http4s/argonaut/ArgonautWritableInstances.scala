package org.http4s.argonaut

import argonaut._
import jawn.Facade
import jawn.support.argonaut.Parser
import org.http4s.EntityBody
import org.http4s.json.jawn.JawnDecodeSupport
import org.http4s.json.JsonSupport
import scalaz.stream.Process.emit
import scalaz.stream.text.utf8Encode

trait ArgonautSupport extends JsonSupport[Json] with JawnDecodeSupport[Json] {
  override protected implicit def jawnFacade: Facade[Json] = Parser.facade

  override def encodeJson(json: Json): EntityBody = {
    // TODO naive implementation materializes to a String.  Consider muster.
    val str = Argonaut.nospace.pretty(json)
    emit(str).pipe(utf8Encode)
  }
}

object ArgonautSupport extends ArgonautSupport
