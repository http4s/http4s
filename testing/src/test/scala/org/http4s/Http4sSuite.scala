/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s

import cats.effect.Blocker
import cats.effect.IO
import cats.syntax.all._
import fs2._
import fs2.text.utf8Decode
import org.http4s.internal.threads.newBlockingPool
import munit._

/** Common stack for http4s' munit based tests
  */
trait Http4sSuite extends CatsEffectSuite with DisciplineSuite with munit.ScalaCheckEffectSuite {

  val testBlocker: Blocker = Http4sSpec.TestBlocker

  implicit class ParseResultSyntax[A](self: ParseResult[A]) {
    def yolo: A = self.valueOr(e => sys.error(e.toString))
  }

  def writeToString[A](a: A)(implicit W: EntityEncoder[IO, A]): IO[String] =
    Stream
      .emit(W.toEntity(a))
      .covary[IO]
      .flatMap(_.body)
      .through(utf8Decode)
      .foldMonoid
      .compile
      .last
      .map(_.getOrElse(""))

}

object Http4sSuite {
  val TestBlocker: Blocker =
    Blocker.liftExecutorService(newBlockingPool("http4s-spec-blocking"))
}
