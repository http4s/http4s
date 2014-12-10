package org.http4s

import org.http4s.Header.`Content-Type`
import org.specs2.mutable.Specification

class ResponseBuilderSpec extends Specification {

  "Explicitly added headers have priority" in {
    val w = EntityEncoder.encodeBy[String](EntityEncoder.stringEncoder.toEntity(_),
      EntityEncoder.stringEncoder.headers.put(`Content-Type`(MediaType.`text/html`)))

    ResponseBuilder(Status.Ok, "some content", `Content-Type`(MediaType.`application/json`))(w)
      .run.headers.get(`Content-Type`).get must_== `Content-Type`(MediaType.`application/json`)
  }

}
