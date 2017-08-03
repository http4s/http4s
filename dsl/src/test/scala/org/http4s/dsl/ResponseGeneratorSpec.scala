package org.http4s
package dsl

import cats.data._
import org.http4s.headers.{Accept, `Content-Length`, `Content-Type`}

class ResponseGeneratorSpec extends Http4sSpec {

  "Add the EntityEncoder headers along with a content-length header" in {
    val body = "foo"
    val resultheaders = Ok(body)(EntityEncoder.stringEncoder).unsafeRun.headers
    EntityEncoder.stringEncoder.headers.foldLeft(ok) { (old, h) =>
      old and (resultheaders.exists(_ == h) must_=== true)
    }

    resultheaders.get(`Content-Length`) must_=== `Content-Length`.fromLong(body.getBytes.length.toLong).toOption
  }

  "Not duplicate headers when not provided" in {
    val w = EntityEncoder.encodeBy[String](EntityEncoder.stringEncoder.headers.put(Accept(MediaRange.`audio/*`)))(
                            EntityEncoder.stringEncoder.toEntity(_))

    Ok("foo")(w).map(_.headers.get(Accept)) must returnValue(beSome(Accept(MediaRange.`audio/*`)))
  }

  "Explicitly added headers have priority" in {
    val w = EntityEncoder.encodeBy[String](EntityEncoder.stringEncoder.headers.put(`Content-Type`(MediaType.`text/html`)))(
      EntityEncoder.stringEncoder.toEntity(_)
    )

    Ok("foo", Headers(`Content-Type`(MediaType.`application/json`)))(w) must returnValue(haveMediaType(MediaType.`application/json`))
  }

}
