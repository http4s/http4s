package org.http4s.dsl

import org.http4s.headers.{Accept, `Content-Length`, `Content-Type`}
import org.http4s.Headers
import org.http4s.MediaRange
import org.http4s.MediaType
import org.http4s.EntityEncoder
import org.http4s.internal.compatibility._
import org.specs2.mutable.Specification

class ResponseGeneratorSpec extends Specification {

  "Add the EntityEncoder headers along with a content-length header" in {
    val body = "foo"
    val resultheaders = Ok(body)(EntityEncoder.stringEncoder).unsafePerformSync.headers
    EntityEncoder.stringEncoder.headers.foldLeft(ok) { (old, h) =>
      old and (resultheaders.exists(_ == h) must_=== true)
    }

    resultheaders.get(`Content-Length`) must_=== `Content-Length`.fromLong(body.getBytes.length.toLong).toOption
  }

  "Not duplicate headers when not provided" in {
    val w = EntityEncoder.encodeBy[String](EntityEncoder.stringEncoder.headers.put(Accept(MediaRange.`audio/*`)))(
                            EntityEncoder.stringEncoder.toEntity(_))

    Ok("foo")(w).unsafePerformSync.headers.get(Accept).get.values.list must_=== List(MediaRange.`audio/*`)
  }

  "Explicitly added headers have priority" in {
    val w = EntityEncoder.encodeBy[String](EntityEncoder.stringEncoder.headers.put(`Content-Type`(MediaType.`text/html`)))(
      EntityEncoder.stringEncoder.toEntity(_)
    )

    Ok("foo", Headers(`Content-Type`(MediaType.`application/json`)))(w)
      .unsafePerformSync.headers.get(`Content-Type`) must_=== Some(`Content-Type`(MediaType.`application/json`))
  }

}
