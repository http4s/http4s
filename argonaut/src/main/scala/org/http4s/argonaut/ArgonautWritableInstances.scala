package org.http4s.argonaut

import argonaut._
import org.http4s.Header.`Content-Type`
import org.http4s.Writable
import org.http4s.json.JsonWritableInstances

trait ArgonautSupport extends ArgonautWritableInstances with Argonauts

trait ArgonautWritableInstances extends JsonWritableInstances[Json] {
  override implicit def jsonWritable: Writable[Json] = Writable.stringWritable
    .contramap(Argonaut.nospace.pretty)
    .withContentType(`Content-Type`.`application/json`)
}
