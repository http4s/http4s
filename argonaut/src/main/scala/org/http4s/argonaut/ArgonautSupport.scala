package org.http4s
package argonaut

import _root_.argonaut.{Argonaut, Json}
import jawn.Facade
import jawn.support.argonaut.Parser
import jawnstreamz._
import org.http4s.json.jawn.JawnDecodeSupport
import org.http4s.json.JsonSupport
import scala.util.control.NonFatal
import scalaz.EitherT
import scalaz.stream.Process.emit
import scalaz.stream.text.utf8Encode

trait ArgonautSupport extends JsonSupport[Json] with JawnDecodeSupport[Json] {
  override protected implicit def jawnFacade: Facade[Json] = Parser.facade

  override def encodeJson(json: Json): EntityBody = {
    // TODO naive implementation materializes to a String.  Consider muster.
    val str = Argonaut.nospace.pretty(json)
    emit(str).pipe(utf8Encode)
  }

  override def decodeJson(body: EntityBody): DecodeResult[Json] = {
    EitherT(body.runJson.attempt).leftMap {
      // TODO: What happens when JSON parsing fails on the Argo?
      case NonFatal(t) => throw t
    }
  }
}

object ArgonautSupport extends ArgonautSupport
