package org.http4s.dsl

import org.http4s.Header
import org.http4s.Header.`Content-Type`
import org.http4s.Headers
import org.http4s.MediaRange
import org.http4s.MediaType
import org.http4s.EntityEncoder
import org.specs2.mutable.Specification

class ResponseGeneratorSpec extends Specification {

  "Add the EntityEncoder headers along with a content-length header" in {
    val body = "foo"
    val resultheaders = Ok(body)(EntityEncoder.stringEncoder).run.headers
    EntityEncoder.stringEncoder.headers.foldLeft(true must_== true) { (old, h) =>
      old and (resultheaders.exists(_ == h) must_== true)
    }

    resultheaders.get(Header.`Content-Length`) must_== Some(Header.`Content-Length`(body.getBytes().length))
  }

  "Not duplicate headers when not provided" in {
    val w = EntityEncoder.encodeBy[String](EntityEncoder.stringEncoder.toEntity(_),
      EntityEncoder.stringEncoder.headers.put(Header.Accept(MediaRange.`audio/*`)))

    Ok("foo")(w).run.headers.get(Header.Accept).get.values.list must_== List(MediaRange.`audio/*`)
  }

  "Explicitly added headers have priority" in {
    val w = EntityEncoder.encodeBy[String](EntityEncoder.stringEncoder.toEntity(_),
      EntityEncoder.stringEncoder.headers.put(`Content-Type`(MediaType.`text/html`)))
    Ok("foo", Headers(`Content-Type`(MediaType.`application/json`)))(w)
      .run.headers.get(`Content-Type`).get must_== `Content-Type`(MediaType.`application/json`)
  }

}
