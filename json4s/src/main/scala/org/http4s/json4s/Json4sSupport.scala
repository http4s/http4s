package org.http4s
package json4s

import jawn.Facade
import jawn.support.json4s.Parser
import jawnstreamz._
import org.http4s.json.JsonSupport
import org.http4s.json.jawn.JawnDecodeSupport
import org.json4s.JsonAST.JValue
import org.json4s.JsonMethods
import org.json4s.ParserUtil.ParseException

import scala.util.control.NonFatal
import scalaz.EitherT
import scalaz.stream.Process.emit
import scalaz.stream.text.utf8Encode

trait Json4sSupport[J] extends JsonSupport[JValue] with JawnDecodeSupport[JValue] {
  protected def jsonMethods: JsonMethods[J]

  override protected implicit def jawnFacade: Facade[JValue] = Parser.facade

  override def encodeJson(json: JValue): EntityBody = {
    // TODO naive implementation materializes to a String.  Consider muster.
    val str = jsonMethods.compact(jsonMethods.render(json))
    emit(str).pipe(utf8Encode)
  }

  override def decodeJson(body: EntityBody): DecodeResult[JValue] = {
    EitherT(body.runJson.attempt).leftMap {
      case pe: ParseException => ParseFailure("Could not decode JSON", pe.getMessage)
      case NonFatal(t) => throw t
    }
  }
}
