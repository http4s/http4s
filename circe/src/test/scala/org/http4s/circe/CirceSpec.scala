/*
 * Copyright 2013-2020 http4s.org
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.http4s
package circe.test // Get out of circe package so we can import custom instances

import cats.effect.IO
import cats.effect.laws.util.TestContext
import cats.effect.laws.util.TestInstances._
import cats.instances.boolean._
import io.circe._
import io.circe.testing.instances._
import org.http4s.circe._
import org.http4s.laws.discipline.EntityCodecTests

// Originally based on ArgonautSpec
class CirceSpec extends Http4sSpec {
  implicit val testContext = TestContext()
  checkAll("EntityCodec[IO, Json]", EntityCodecTests[IO, Json].entityCodec)
}
