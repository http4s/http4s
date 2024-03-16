package org.http4s
package multipart

import cats.effect.IO
import cats.effect.std.Random
import cats.syntax.apply.*
import fs2.io.file.{Files, Path}
import fs2.{Chunk, Stream}
import org.http4s.syntax.literals.*
import scala.concurrent.duration.DurationInt

class MultipartReceiverSuite extends Http4sSuite {

  private val url = uri"https://example.com/path/to/some/where"

  private val rand =
    Random
      .scalaUtilRandom[IO]
      .syncStep(Int.MaxValue)
      .unsafeRunSync()
      .toOption
      .get

  private val multiparts = Multiparts.fromRandom(rand)

  private def multipartRequest(parts: Part[IO]*): IO[Request[IO]] = multiparts
    .multipart(Vector(parts: _*))
    .map { multipart =>
      val entity = EntityEncoder[IO, Multipart[IO]].toEntity(multipart)
      Request(method = Method.POST, uri = url, body = entity.body, headers = multipart.headers)
    }

  private val infiniteBytes = Stream.repeatEval(rand.nextBytes(512).map(Chunk.array(_))).unchunks
  private val infiniteText = Stream.repeatEval(rand.nextString(32))

  test("`MultipartReceiver.at(...).once` should extract the value of that field") {
    val onlyAllowFoo = MultipartDecoder.fromReceiver(
      MultipartReceiver.at("foo", PartReceiver.bodyText[IO]).once
    )

    val requestWithOnlyFoo = multipartRequest(
      Part.formData[IO]("foo", "bar")
    )
    onlyAllowFoo.use { decoder =>
      requestWithOnlyFoo.flatMap { req =>
        assertIO(
          decoder.decode(req, strict = true).value,
          Right("bar"),
        )
      }
    }.assert
  }

  test("`MultipartReceiver.at(...).once` should fail if the given field is missing") {
    val onlyAllowFoo = MultipartDecoder.fromReceiver(
      MultipartReceiver.at("foo", PartReceiver.bodyText[IO]).once
    )
    val requestWithoutFoo = multipartRequest(Part.formData[IO]("not-foo", "bar"))

    onlyAllowFoo.use { decoder =>
      requestWithoutFoo.flatMap { req =>
        assertIOBoolean(
          decoder.decode(req, strict = true).isLeft
        )
      }
    }.assert
  }

  test(
    "`MultipartReceiver.at(...).once` should raise an error if an unexpected field is encountered"
  ) {
    val onlyAllowFoo = MultipartDecoder.fromReceiver(
      MultipartReceiver.at("foo", PartReceiver.bodyText[IO]).once
    )
    // `EntityDecoder.mixedMultipartResource[IO]` would never terminate if decoding this request,
    // and would fill up the host's hard drive eventually if not cancelled
    val maliciousRequest = multipartRequest(Part.fileData("not-foo", "infinite.bin", infiniteBytes))

    onlyAllowFoo.use { decoder =>
      maliciousRequest.flatMap { req =>
        assertIOBoolean(
          decoder.decode(req, strict = true).isLeft
        )
      }
    }.assert
  }

  test(
    "`MultipartReceiver.at(..., receiver.withSizeLimit(n)).once` should raise an error when the request exceeds the limit"
  ) {
    val onlyAllowFooWithLimit = MultipartDecoder.fromReceiver(
      MultipartReceiver.at("foo", PartReceiver.bodyText[IO].withSizeLimit(8192)).once
    )

    val maliciousRequest = multipartRequest(
      Part.fileData("foo", "infinite.txt", infiniteText.through(fs2.text.utf8.encode))
    )

    onlyAllowFooWithLimit.use { decoder =>
      maliciousRequest.flatMap { req =>
        assertIOBoolean(
          decoder.decode(req, strict = true).isLeft
        )
      }
    }.assert
  }

  test(
    "`MultipartReceiver.at(...).once` should fail when its expected field appears multiple times"
  ) {
    val onlyAllowFoo = MultipartDecoder.fromReceiver(
      MultipartReceiver.at("foo", PartReceiver.bodyText[IO]).once
    )
    val requestWithMultipleFoos = multipartRequest(
      Part.formData[IO]("foo", "bar1"),
      Part.formData[IO]("foo", "bar2"),
    )

    onlyAllowFoo.use { decoder =>
      requestWithMultipleFoos.flatMap { req =>
        assertIOBoolean(
          decoder.decode(req, strict = true).isLeft
        )
      }
    }.assert
  }

  test(
    "`MultipartReceiver.at(..., PartReceiver.reject(...)).once` should fail when its expected field appears"
  ) {
    val rejectFoo = MultipartDecoder.fromReceiver(
      MultipartReceiver
        .at("foo", PartReceiver.reject[IO, String](InvalidMessageBodyFailure("foo not allowed")))
        .once
    )
    val requestWithFoo = multipartRequest(Part.formData[IO]("foo", "bar"))

    rejectFoo.use { decoder =>
      requestWithFoo.flatMap { req =>
        assertIOBoolean(
          decoder.decode(req, strict = true).isLeft
        )
      }
    }.assert
  }

  test(
    "`MultipartReceiver.at(...).once.ignoreUnexpectedParts` should extract the expected value and ignore other parts"
  ) {
    val extractFoo = MultipartDecoder.fromReceiver(
      MultipartReceiver.at("foo", PartReceiver.bodyText[IO]).once.ignoreUnexpectedParts
    )
    val requestWithFooAndOthers = multipartRequest(
      Part.formData[IO]("bing", "bong"),
      Part.formData[IO]("ding", "dong"),
      Part.fileData("file", "file.txt", infiniteText.take(512).through(fs2.text.utf8.encode)),
      Part.formData[IO]("foo", "bar"), // <- here it is!
      Part.formData[IO]("baz", "biff"),
    )
    extractFoo.use { decoder =>
      requestWithFooAndOthers.flatMap { req =>
        assertIO(
          decoder.decode(req, strict = true).value,
          Right("bar"),
        )
      }
    }.assert
  }

  test("`MultipartReceiver.at(...).asOption` should extract the expected value when present") {
    val maybeExtractFoo = MultipartDecoder.fromReceiver(
      MultipartReceiver.at("foo", PartReceiver.bodyText[IO]).asOption
    )
    val requestWithFoo = multipartRequest(
      Part.formData[IO]("foo", "bar")
    )

    maybeExtractFoo.use { decoder =>
      requestWithFoo.flatMap { req =>
        assertIO(
          decoder.decode(req, strict = true).value,
          Right(Some("bar")),
        )
      }
    }.assert
  }

  test(
    "`MultipartReceiver.at(...).asOption` should result in None when the expected field is missing"
  ) {
    val maybeExtractFoo = MultipartDecoder.fromReceiver(
      MultipartReceiver.at("foo", PartReceiver.bodyText[IO]).asOption.ignoreUnexpectedParts
    )
    val requestWithFoo = multipartRequest(
      Part.formData[IO]("not-foo", "bar")
    )

    maybeExtractFoo.use { decoder =>
      requestWithFoo.flatMap { req =>
        assertIO(
          decoder.decode(req, strict = true).value,
          Right(None),
        )
      }
    }.assert
  }

  test(
    "`MultipartReceiver.at(...).asList` should extract a value for each part with the expected name"
  ) {
    val extractAllFoos = MultipartDecoder.fromReceiver(
      MultipartReceiver.at("foo", PartReceiver.bodyText[IO]).asList.ignoreUnexpectedParts
    )
    val requestWithManyFoos = multipartRequest(
      Part.formData[IO]("foo", "bar1"),
      Part.formData[IO]("foo", "bar2"),
      Part.formData[IO]("not-foo", "bar3"),
      Part.formData[IO]("foo", "bar4"),
    )

    extractAllFoos.use { decoder =>
      requestWithManyFoos.flatMap { req =>
        assertIO(
          decoder.decode(req, strict = true).value,
          Right(List("bar1", "bar2", "bar4")),
        )
      }
    }.assert
  }

  test(
    "`MultipartReceiver.at(..., PartReceiver.ignore)` will not terminate when the expected part contains infinite data"
  ) {
    val ignoreFoo = MultipartDecoder.fromReceiver(
      MultipartReceiver.at("foo", PartReceiver.ignore[IO]).once
    )
    val requestWithInfiniteFoo = multipartRequest(
      Part.fileData("foo", "foo.bin", infiniteBytes)
    )

    ignoreFoo.use { decoder =>
      requestWithInfiniteFoo.flatMap { req =>
        assertIOBoolean(
          // Since the decoder won't terminate, we'll run it for 1 second before cancelling.
          IO.race(
            IO.sleep(1.second).as(true),
            decoder.decode(req, strict = true).value.as(false),
          ).map(_.merge)
        )
      }
    }.assert
  }

  test(
    "`MultipartReceiver.at(..., PartReceiver.toTempFile)` will write data to a temp file, which will be released automatically"
  ) {
    val decodeFooToFile = MultipartDecoder.fromReceiver(
      MultipartReceiver.at("foo", PartReceiver.toTempFile[IO]).once
    )
    val requestWithFoo = multipartRequest(
      Part.fileData("foo", "foo.bin", infiniteBytes.take(512))
    )

    decodeFooToFile
      .use { decoder =>
        requestWithFoo.flatMap { req =>
          decoder
            .decode(req, strict = true)
            .value
            .flatTap {
              case Left(_) => IO.unit
              case Right(tempFile) =>
                assertIO(Files[IO].size(tempFile), 512L, "wrong size of temp file")
            }
            .rethrow // return the temp file from the `use` block, escaping its lifetime
        }
      }
      .flatMap { tempFile =>
        // since `decodeFooToFile` is a resource, any underlying resources allocated by the decoder
        // will be released when it is released, i.e. deleting the temp file
        assertIO(Files[IO].exists(tempFile), false, "temp file wasn't deleted")
      }
      .assert
  }

  test(
    "`MultipartReceiver.auto` should decode all parts to a PartValue map, and the decoder should delete temp files after use"
  ) {
    val autoDecoder = MultipartDecoder.fromReceiver(MultipartReceiver.auto[IO])
    val arbitraryRequest = multipartRequest(
      Part.formData("a", "hello"),
      Part.formData("b", "world"),
      Part.fileData("c", "c.bin", infiniteBytes.take(512)),
      Part.fileData("d", "d.bin", infiniteBytes.take(256)),
    )

    autoDecoder
      .use { decoder =>
        arbitraryRequest.flatMap { req =>
          decoder
            .decode(req, strict = true)
            .semiflatMap { parts =>
              for {
                _ <- assertIO(IO(parts.get("a")), Some(PartValue.OfString("hello")))
                _ <- assertIO(IO(parts.get("b")), Some(PartValue.OfString("world")))
                cFileOpt <- parts.get("c") match {
                  case Some(PartValue.OfFile("c.bin", f)) =>
                    assertIO(Files[IO].size(f), 512L, "part 'c' not properly received").as(Some(f))
                  case _ => assertIOBoolean(IO.pure(false), "part 'c' missing").as(None)
                }
                dFileOpt <- parts.get("d") match {
                  case Some(PartValue.OfFile("d.bin", f)) =>
                    assertIO(Files[IO].size(f), 256L, "part 'd' not properly received").as(Some(f))
                  case _ => assertIOBoolean(IO.pure(false), "part 'c' missing").as(None)
                }
              } yield (cFileOpt, dFileOpt)
            }
            .getOrElse(None -> None)
        }
      }
      .flatMap {
        case (Some(fileC), Some(fileD)) =>
          assertIO(
            Files[IO].exists(fileC),
            false,
            "file 'c' must be deleted after decoder resource release",
          ) *>
            assertIO(
              Files[IO].exists(fileD),
              false,
              "file 'd' must be deleted after decoder resource release",
            )
        case _ =>
          assertIOBoolean(IO.pure(false), "file parts were not received")
      }
      .assert
  }

  test("`MultipartReceiver.uniform` receives all parts the same way") {
    val uniformDecoder = MultipartDecoder.fromReceiver(
      MultipartReceiver.uniform(PartReceiver.bodyText[IO])
    )
    val arbitraryRequest = multipartRequest(
      Part.formData("a", "hello"),
      Part.formData("b", "world"),
      Part.formData("c", "how"),
      Part.formData("d", "are"),
      Part.formData("e", "you"),
    )

    uniformDecoder.use { decoder =>
      arbitraryRequest.flatMap { req =>
        assertIOBoolean(
          decoder
            .decode(req, strict = true)
            .map(_ == List("hello", "world", "how", "are", "you"))
            .getOrElse(false)
        )
      }
    }.assert
  }

  locally {
    // several tests will reuse this class and corresponding decoder; using `locally` to enclose them all
    final case class FormBodyLoaded(foo: String, bar: String, fileContent: String)
    final case class FormBody(foo: String, bar: String, file: Path) {
      def load: IO[FormBodyLoaded] = (
        IO.pure(foo),
        IO.pure(bar),
        Files[IO].readUtf8(file).compile.string,
      ).mapN(FormBodyLoaded.apply)
    }

    val formBodyReceiver = (
      MultipartReceiver.at("foo", PartReceiver.bodyText[IO].withSizeLimit(1024L)).once,
      MultipartReceiver.at("bar", PartReceiver.bodyText[IO].withSizeLimit(1024L)).once,
      MultipartReceiver.at("file", PartReceiver.toTempFile[IO].withSizeLimit(8192L)).once,
    ).mapN(FormBody.apply)
    val formBodyDecoder = MultipartDecoder.fromReceiver(formBodyReceiver)

    test(
      "Example `MultipartReceiver` constructed with Applicative will succeed under expected conditions"
    ) {
      val goodRequest = multipartRequest(
        Part.formData("foo", "I AM A MOOSE"),
        Part.formData("bar", "WE ARE MEESE"),
        Part.fileData(
          "file",
          "filename.txt",
          Stream.emit("there is some stuff in this file!").through(fs2.text.utf8.encode[IO]),
        ),
      )

      formBodyDecoder.use { decoder =>
        goodRequest.flatMap { request =>
          assertIO(
            decoder
              .decode(request, strict = true)
              .semiflatMap(_.load)
              .value,
            Right(
              FormBodyLoaded(
                "I AM A MOOSE",
                "WE ARE MEESE",
                "there is some stuff in this file!",
              )
            ),
          )
        }
      }.assert
    }

    test(
      "Example `MultipartReceiver` constructed with Applicative will fail upon encountering an unexpected part"
    ) {
      val badRequest = multipartRequest(
        Part.formData("foo", "I AM A MOOSE"),
        Part.formData("bar", "WE ARE MEESE"),
        Part.formData("baz", "BOOM"), // <- unexpected!
        Part.fileData(
          "file",
          "filename.txt",
          Stream.emit("there is some stuff in this file!").through(fs2.text.utf8.encode[IO]),
        ),
      )
      formBodyDecoder.use { decoder =>
        badRequest.flatMap { request =>
          assertIOBoolean(
            decoder.decode(request, strict = true).isLeft
          )
        }
      }.assert
    }

    test(
      "Example `MultipartReceiver` constructed with Applicative will fail if not all required parts are received"
    ) {
      val badRequest = multipartRequest(
        Part.formData("foo", "I AM A MOOSE"),
        Part.formData("bar", "WE ARE MEESE"),
      )

      formBodyDecoder.use { decoder =>
        badRequest.flatMap { request =>
          assertIOBoolean(
            decoder.decode(request, strict = true).isLeft
          )
        }
      }.assert
    }

    test(
      "Example `MultipartReceiver` constructed with Applicative will fail if any of its part receivers fail"
    ) {
      val badFoo = Part
        .formData[IO]("foo", "")
        .copy(body = infiniteText.take(8192).through(fs2.text.utf8.encode))
      val badBar =
        Part.formData[IO]("bar", "").copy(body = infiniteText.through(fs2.text.utf8.encode))
      val badFile = Part.fileData[IO]("file", "upload.bin", infiniteBytes.take(65535))

      val goodFoo = Part.formData[IO]("foo", "hello")
      val goodBar = Part.formData[IO]("bar", "world")
      val goodFile = Part.fileData[IO]("file", "upload.bin", infiniteBytes.take(1024))

      // Get permutations of a `multipartRequest` with a bad part for at least one of each expected part.
      // If "good" is a 0 and "bad" is a 1, we can get these permutations by iterating numbers 1 through 7
      // and treating each bit in the number as a good/bad choice for that part
      val badRequestPermutations = Stream.emits(1 to 7).evalMap { i =>
        val fooPart = if ((i & 1) == 0) goodFoo else badFoo
        val barPart = if ((i & 2) == 0) goodBar else badBar
        val filePart = if ((i & 4) == 0) goodFile else badFile
        multipartRequest(fooPart, barPart, filePart)
      }

      badRequestPermutations
        .foreach { req =>
          formBodyDecoder.use { decoder =>
            assertIOBoolean(decoder.decode(req, strict = true).isLeft)
          }
        }
        .compile
        .drain
        .assert
    }

    test(
      "Example `MultipartReceiver` constructed with Applicative can be made resilient to unexpected parts via `ignoreUnexpectedParts`"
    ) {
      val lenientDecoder = MultipartDecoder.fromReceiver(formBodyReceiver.ignoreUnexpectedParts)
      val requestIO = multipartRequest(
        Part.formData[IO]("foo", "hello"),
        Part.formData[IO]("bar", "world"),
        Part.fileData[IO](
          "file",
          "upload.bin",
          Stream.emit("file content here").through(fs2.text.utf8.encode[IO]),
        ),
        Part.formData[IO]("a", "Hey!"),
        Part.formData[IO]("b", "I'm here"),
        Part.formData[IO]("c", "to distract"),
        Part.formData[IO]("d", "you!"),
      )

      lenientDecoder.use { decoder =>
        requestIO.flatMap { req =>
          decoder
            .decode(req, strict = true)
            .foldF(
              _ => assertIOBoolean(IO.pure(false), "decoding failed"),
              result => assertIO(result.load, FormBodyLoaded("hello", "world", "file content here")),
            )
        }
      }.assert
    }

    test(
      "Example `MultipartReceiver` constructed with Applicative will still succeed if expected parts are out of order"
    ) {
      val lenientDecoder = MultipartDecoder.fromReceiver(formBodyReceiver.ignoreUnexpectedParts)

      val parts = List(
        Part.formData[IO]("foo", "hello"),
        Part.formData[IO]("bar", "world"),
        Part.fileData[IO](
          "file",
          "upload.bin",
          Stream.emit("file content here").through(fs2.text.utf8.encode[IO]),
        ),
        Part.formData[IO]("a", "Hey!"),
        Part.formData[IO]("b", "I'm here"),
        Part.formData[IO]("c", "to distract"),
        Part.formData[IO]("d", "you!"),
      )
      val expected = FormBodyLoaded("hello", "world", "file content here")

      Stream
        .eval(rand.shuffleList(parts))
        .repeatN(50)
        .evalMap(multipartRequest(_: _*))
        .foreach { req =>
          lenientDecoder.use { decoder =>
            assertIO(
              decoder.decode(req, strict = true).semiflatMap(_.load).value,
              Right(expected),
            )
          }
        }
        .compile
        .drain
        .assert
    }
  }

  test(
    "`MultipartDecoder.fromReceiver(..., headerLimit)` will fail when receiving a part with headers exceeding the size limit"
  ) {
    val badRequest = multipartRequest(
      Part.formData("foo", "bar"),
      Part.formData("baz", "bang"),
      Part.formData(
        "bing",
        "bong",
        "X-My-Long-Header" -> ("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_" * 64),
      ),
    )
    val decoderRes = MultipartDecoder.fromReceiver(
      MultipartReceiver.auto[IO],
      headerLimit = 512,
    )
    decoderRes.use { decoder =>
      badRequest.flatMap { req =>
        assertIOBoolean(decoder.decode(req, strict = true).isLeft)
      }
    }.assert
  }

  test(
    "`MultipartDecoder.fromReceiver(..., maxParts, failOnLimit = true)` will fail if it receives too many parts"
  ) {
    val decoderRes = MultipartDecoder.fromReceiver(
      MultipartReceiver.auto[IO],
      maxParts = Some(5),
      failOnLimit = true,
    )

    val parts =
      Iterator.from(0).take(10).map(i => Part.formData[IO](i.toString, "whatever")).toVector

    Stream
      .emits(1 to 10)
      .foreach { n =>
        decoderRes.use { decoder =>
          multipartRequest(parts.take(n): _*).flatMap { req =>
            val decodeResult = decoder.decode(req, strict = true)
            assertIOBoolean(
              if (n > 5) decodeResult.isLeft // decode failure due to exceeded limit
              else decodeResult.isRight, // decode success due to part count being within limit
              s"$n parts should ${if (n > 5) "fail" else "pass"}",
            )
          }
        }
      }
      .compile
      .drain
      .assert
  }

  test(
    "MultipartDecoder.fromReceiver(..., maxParts, failOnLimit = false)` will ignore parts after the limit"
  ) {
    val decoderRes = MultipartDecoder.fromReceiver(
      MultipartReceiver.auto[IO],
      maxParts = Some(5),
      failOnLimit = false,
    )

    val parts =
      Iterator.from(0).take(10).map(i => Part.formData[IO](i.toString, "whatever")).toVector

    Stream
      .emits(1 to 10)
      .foreach { n =>
        decoderRes.use { decoder =>
          multipartRequest(parts.take(n): _*).flatMap { req =>
            val decodeResult = decoder.decode(req, strict = true).map(_.size).getOrElse(-1)
            assertIO(decodeResult, n.min(5), "part count should be limited")
          }
        }
      }
      .compile
      .drain
      .assert
  }

}
