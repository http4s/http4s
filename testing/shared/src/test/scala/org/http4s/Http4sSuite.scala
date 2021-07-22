/*
 * Copyright 2016 http4s.org
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
import cats.syntax.all._
import fs2._
import fs2.text.utf8
import munit._
import cats.effect.unsafe.IORuntime
import cats.effect.Resource
import cats.effect.kernel.Deferred

/** Common stack for http4s' munit based tests
  */
trait Http4sSuite
    extends CatsEffectSuite
    with Http4sSuitePlatform
    with DisciplineSuite
    with munit.ScalaCheckEffectSuite {

  override implicit val ioRuntime: IORuntime = Http4sSuite.TestIORuntime

  private[this] val suiteFixtures = List.newBuilder[Fixture[_]]

  override def munitFixtures: Seq[Fixture[_]] = suiteFixtures.result()

  def registerSuiteFixture[A](fixture: Fixture[A]) = {
    suiteFixtures += fixture
    fixture
  }

  // allow flaky tests on ci
  override def munitFlakyOK = sys.env.get("CI").isDefined

  implicit class ParseResultSyntax[A](self: ParseResult[A]) {
    def yolo: A = self.valueOr(e => sys.error(e.toString))
  }

  def writeToString[A](a: A)(implicit W: EntityEncoder[IO, A]): IO[String] =
    Stream
      .emit(W.toEntity(a))
      .covary[IO]
      .flatMap(_.body)
      .through(utf8.decode)
      .foldMonoid
      .compile
      .last
      .map(_.getOrElse(""))

  def resourceSuiteDeferredFixture[A](name: String, resource: Resource[IO, A]) =
    registerSuiteFixture(ResourceSuiteLocalDeferredFixture(name, resource))

  // TODO Pending https://github.com/typelevel/munit-cats-effect/pull/104
  object ResourceSuiteLocalDeferredFixture {

    def apply[T](name: String, resource: Resource[IO, T]): Fixture[IO[T]] =
      new Fixture[IO[T]](name) {
        val value: Deferred[IO, (T, IO[Unit])] = Deferred.unsafe

        def apply(): IO[T] = value.get.map(_._1)

        override def beforeAll(): Unit = {
          val resourceEffect = resource.allocated.flatMap(value.complete)
          resourceEffect.unsafeRunAndForget()
        }

        override def afterAll(): Unit =
          value.get.flatMap(_._2).unsafeRunAndForget()
      }
  }

}

object Http4sSuite extends Http4sSuiteSingletonPlatform
