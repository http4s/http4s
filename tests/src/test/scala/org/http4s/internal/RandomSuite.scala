package org.http4s.internal

import cats.effect.IO
import org.http4s.Http4sSuite

import java.security.SecureRandom
import java.util.{Random => JRandom}
import scala.util.Try

class RandomSuite extends Http4sSuite {
  test("javaUtilRandomBlocking works") {
    val rnd = new JRandom()
    Random
      .javaUtilRandomBlocking[IO](testBlocker)(rnd)
      .nextBytes(128)
      .map(_.size)
      .assertEquals(128)
  }

  test("javaUtilRandomNonBlocking works") {
    val rnd = Try(SecureRandom.getInstance("NativePRNGNonBlocking"))
    // Actually, this test would run with anything, but let's try to
    // give it a proper instance.
    assume(rnd.isSuccess, "this test requires a NativePRNGNonBlocking SecureRandom")
    Random
      .javaUtilRandomNonBlocking[IO](rnd.get)
      .nextBytes(128)
      .map(_.size)
      .assertEquals(128)
  }

  test("javaSecurityRandom works") {
    Random
      .javaSecurityRandom[IO](testBlocker)
      .flatMap(_.nextBytes(128))
      .map(_.size)
      .assertEquals(128)
  }
}
