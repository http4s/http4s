/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s

import cats.effect.IO
import cats.effect.Resource
import cats.effect.SyncIO
import cats.effect.std.Env
import cats.syntax.all._
import fs2._
import fs2.text.utf8
import munit._
import munit.catseffect._

/** Common stack for http4s' munit based tests
  */
trait Http4sSuite extends CatsEffectSuite with DisciplineSuite with munit.ScalaCheckEffectSuite {

  // allow flaky tests on ci
  override def munitFlakyOK: Boolean = Env.make[SyncIO].get("CI").unsafeRunSync().isDefined

  private[this] val suiteFixtures = List.newBuilder[IOFixture[_]]

  override def munitFixtures: Seq[IOFixture[_]] = suiteFixtures.result()

  def registerSuiteFixture[A](fixture: IOFixture[A]): IOFixture[A] = {
    suiteFixtures += fixture
    fixture
  }

  def resourceSuiteFixture[A](name: String, resource: Resource[IO, A]): IOFixture[A] =
    registerSuiteFixture(ResourceSuiteLocalFixture(name, resource))

  implicit class ParseResultSyntax[A](self: ParseResult[A]) {
    def yolo: A = self.valueOr(e => sys.error(e.toString))
  }

  def writeToString[A](a: A)(implicit W: EntityEncoder[IO, A]): IO[String] =
    Stream
      .emit(W.toEntity(a))
      .flatMap(_.body)
      .through(utf8.decode)
      .foldMonoid
      .compile
      .last
      .map(_.getOrElse(""))

}
