package org.http4s

import org.http4s.Header.`Content-Type`
import org.specs2.mutable.Specification

class ResponseBuilderSpec extends Specification {

  "Explicitly added headers have priority" in {
    val w = Writable[String](toEntity = Writable.stringWritable.toEntity,
      headers = Writable.stringWritable.headers.put(`Content-Type`(MediaType.`text/html`)))
    ResponseBuilder(Status.Ok, "some content", Headers(`Content-Type`(MediaType.`application/json`)))(w)
      .run.headers.get(`Content-Type`).get must_== `Content-Type`(MediaType.`application/json`)
  }

}
