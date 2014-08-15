package org.http4s.json4s

import org.http4s.Header.`Content-Type`
import org.http4s.{Charset, Writable}
import org.http4s.json.JsonWritableInstances
import org.json4s.JsonAST.JValue
import org.json4s.JsonMethods

trait Json4sWritableInstances[J] extends JsonWritableInstances[JValue] {
  override implicit def jsonWritable: Writable[JValue] = Writable.stringWritable(Charset.`UTF-8`)
    .contramap { jValue: JValue => jsonMethods.compact(jsonMethods.render(jValue)) }
    .withContentType(`Content-Type`.`application/json`)

  protected def jsonMethods: JsonMethods[J]
}
