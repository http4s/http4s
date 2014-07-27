package org.http4s.json4s

import org.http4s.Header.`Content-Type`
import org.http4s.{CharacterSet, Writable}
import org.http4s.json.JsonWritableInstances
import org.json4s.JsonAST.JValue

trait Json4sWritableInstances extends JsonWritableInstances[JValue] {
  override implicit def jsonWritable: Writable[JValue] = Writable.stringWritable(CharacterSet.`UTF-8`)
    .contramap(jValueToString)
    .withContentType(`Content-Type`.`application/json`)

  protected def jValueToString(jValue: JValue): String
}
