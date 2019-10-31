package org.http4s
package multipart

import cats.effect._
import org.http4s.headers._

class JVMMultipartParserSpec extends MultipartParserSpec {

  multipartParserTests(
    "Default parser",
    MultipartParser.parseStreamed[IO](_),
    MultipartParser.parseStreamed[IO],
    MultipartParser.parseToPartsStream[IO](_))

  multipartParserTests(
    "mixed file parser",
    MultipartParser.parseStreamedFile[IO](_, Http4sSpec.TestBlockingExecutionContext),
    MultipartParser.parseStreamedFile[IO](_, Http4sSpec.TestBlockingExecutionContext, _),
    MultipartParser.parseToPartsStreamedFile[IO](_, Http4sSpec.TestBlockingExecutionContext)
  )

  "Multipart mixed file parser" should {

    "truncate parts when limit set" in {
      val unprocessedInput =
        """
          |--RU(_9F(PcJK5+JMOPCAF6Aj4iSXvpJkWy):6s)YU0
          |Content-Disposition: form-data; name="field1"
          |Content-Type: text/plain
          |
          |Text_Field_1
          |--RU(_9F(PcJK5+JMOPCAF6Aj4iSXvpJkWy):6s)YU0
          |Content-Disposition: form-data; name="field2"
          |
          |Text_Field_2
          |--RU(_9F(PcJK5+JMOPCAF6Aj4iSXvpJkWy):6s)YU0--""".stripMargin

      val input = ruinDelims(unprocessedInput)

      val boundaryTest = Boundary("RU(_9F(PcJK5+JMOPCAF6Aj4iSXvpJkWy):6s)YU0")
      val results =
        unspool(input).through(
          MultipartParser.parseStreamedFile[IO](
            boundaryTest,
            Http4sSpec.TestBlockingExecutionContext,
            maxParts = 1))

      val multipartMaterialized = results.compile.last.map(_.get).unsafeRunSync()
      val headers =
        multipartMaterialized.parts.foldLeft(List.empty[Headers])((l, r) => l ::: List(r.headers))
      headers mustEqual List(
        Headers(
          `Content-Disposition`("form-data", Map("name" -> "field1")),
          `Content-Type`(MediaType.text.plain)
        )
      )
    }

    "Fail parsing when parts limit exceeded if set fail as option" in {
      val unprocessedInput =
        """
          |--RU(_9F(PcJK5+JMOPCAF6Aj4iSXvpJkWy):6s)YU0
          |Content-Disposition: form-data; name="field1"
          |Content-Type: text/plain
          |
          |Text_Field_1
          |--RU(_9F(PcJK5+JMOPCAF6Aj4iSXvpJkWy):6s)YU0
          |Content-Disposition: form-data; name="field2"
          |
          |Text_Field_2
          |--RU(_9F(PcJK5+JMOPCAF6Aj4iSXvpJkWy):6s)YU0--""".stripMargin

      val input = ruinDelims(unprocessedInput)

      val boundaryTest = Boundary("RU(_9F(PcJK5+JMOPCAF6Aj4iSXvpJkWy):6s)YU0")
      val results = unspool(input).through(
        MultipartParser.parseStreamedFile[IO](
          boundaryTest,
          Http4sSpec.TestBlockingExecutionContext,
          maxParts = 1,
          failOnLimit = true))

      results.compile.last.map(_.get).unsafeRunSync() must throwA[MalformedMessageBodyFailure]
    }
  }
}
