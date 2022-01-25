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
import cats.effect.Resource
import cats.syntax.all._
import fs2._
import fs2.text.utf8
import munit._
import org.scalacheck.Prop

/** Common stack for http4s' munit based tests
  */
trait Http4sSuite
    extends CatsEffectSuite
    with DisciplineSuite
    with munit.ScalaCheckEffectSuite
    with Http4sSuitePlatform {

  private[this] val suiteFixtures = List.newBuilder[Fixture[_]]

  override def munitFixtures: Seq[Fixture[_]] = suiteFixtures.result()

  // Override to remove implicit modifier
  override def unitToProp = super.unitToProp

  // Scala 3 likes this better
  implicit def saneUnitToProp(unit: Unit): Prop = unitToProp(unit)

  def registerSuiteFixture[A](fixture: Fixture[A]) = {
    suiteFixtures += fixture
    fixture
  }

  def resourceSuiteDeferredFixture[A](name: String, resource: Resource[IO, A]) =
    registerSuiteFixture(UnsafeResourceSuiteLocalDeferredFixture(name, resource))

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
object Http4sSuite extends Http4sSuiteCompanionPlatform
