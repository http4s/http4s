package org.http4s
package dsl

import cats._
import cats.effect.IO
import org.http4s.headers.{Accept, `Content-Length`, `Content-Type`}

class ResponseGeneratorSpec extends Http4sSpec {

  "Add the EntityEncoder headers along with a content-length header" in {
    val body          = "foo"
    val resultheaders = Ok(body)(Applicative[IO], FlatMap[IO], EntityEncoder.stringEncoder[IO]).unsafeRunSync.headers
    EntityEncoder.stringEncoder[IO].headers.foldLeft(ok) { (old, h) =>
      old and (resultheaders.exists(_ == h) must_=== true)
    }

    resultheaders.get(`Content-Length`) must_=== Some(`Content-Length`(body.getBytes.length.toLong))
  }

  "Not duplicate headers when not provided" in {
    val w =
      EntityEncoder.encodeBy[IO, String](
        EntityEncoder.stringEncoder[IO].headers.put(Accept(MediaRange.`audio/*`)))(
        EntityEncoder.stringEncoder[IO].toEntity(_)
      )

    Ok("foo")(Applicative[IO], FlatMap[IO], w).map(_.headers.get(Accept)) must returnValue(
      beSome(Accept(MediaRange.`audio/*`)))
  }

  "Explicitly added headers have priority" in {
    val w: EntityEncoder[IO, String] = EntityEncoder.encodeBy[IO, String](
      EntityEncoder.stringEncoder[IO].headers.put(`Content-Type`(MediaType.`text/html`)))(
      EntityEncoder.stringEncoder[IO].toEntity(_)
    )

    val resp: IO[Response[IO]] =
      Ok("foo", Headers(`Content-Type`(MediaType.`application/json`)))(Applicative[IO], FlatMap[IO], w)
    resp must returnValue(haveMediaType(MediaType.`application/json`))
  }

}
