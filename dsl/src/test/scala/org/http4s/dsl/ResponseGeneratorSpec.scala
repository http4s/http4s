package org.http4s.dsl

import org.http4s.Header
import org.http4s.Header.`Content-Type`
import org.http4s.Headers
import org.http4s.MediaRange
import org.http4s.MediaType
import org.http4s.Writable
import org.specs2.mutable.Specification

class ResponseGeneratorSpec extends Specification {

  "Add the Writable headers along with a content-length header" in {
    val body = "foo"
    val resultheaders = Ok(body)(Writable.stringWritable).run.headers
    Writable.stringWritable.headers.foldLeft(true must_== true) { (old, h) =>
      old and (resultheaders.exists(_ == h) must_== true)
    }

    resultheaders.get(Header.`Content-Length`) must_== Some(Header.`Content-Length`(body.getBytes().length))
  }

  "Not duplicate headers when not provided" in {
    val w = Writable[String](toEntity = Writable.stringWritable.toEntity,
      headers = Writable.stringWritable.headers.put(Header.Accept(MediaRange.`audio/*`)))

    Ok("foo")(w).run.headers.get(Header.Accept).get.values.list must_== List(MediaRange.`audio/*`)
  }

  "Explicitly added headers have priority" in {
    val w = Writable[String](toEntity = Writable.stringWritable.toEntity,
      headers = Writable.stringWritable.headers.put(`Content-Type`(MediaType.`text/html`)))
    Ok("foo", Headers(`Content-Type`(MediaType.`application/json`)))(w)
      .run.headers.get(`Content-Type`).get must_== `Content-Type`(MediaType.`application/json`)
  }

}
