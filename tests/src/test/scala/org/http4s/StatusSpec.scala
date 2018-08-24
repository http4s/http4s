package org.http4s

import cats.implicits._
import cats.kernel.laws.discipline.OrderTests
import org.http4s.Status._
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

class StatusSpec extends Http4sSpec {

  checkAll("Status", OrderTests[Status].order)

  "Statuses" should {
    "not be equal if their codes are not" in {
      prop { (s1: Status, s2: Status) =>
        (s1.code != s2.code) ==> (s1 != s2)
      }
    }

    "be equal if their codes are" in {
      forAll(genValidStatusCode) { i =>
        val s1: Status = getStatus(i, "a reason")
        val s2: Status = getStatus(i, "another reason")
        s1 must_== s2
      }
    }

    "be ordered by their codes" in {
      prop { (s1: Status, s2: Status) =>
        (s1.code < s2.code) ==> (s1 < s2)
      }
    }

    "have the appropriate response classes" in {
      forAll(genValidStatusCode) { code =>
        val expected = code / 100 match {
          case 1 => Informational
          case 2 => Successful
          case 3 => Redirection
          case 4 => ClientError
          case 5 => ServerError
        }
        fromInt(code) must beRight.like { case s => s.responseClass must_== expected }
      }
    }

  }

  "Finding a status by code" should {

    "fail if the code is not in the range of valid codes" in {
      forAll(Gen.choose(Int.MinValue, 99)) { i =>
        fromInt(i).isLeft
      }
    }

    "fail if the code is not in the range of valid codes" in {
      forAll(Gen.choose(599, Int.MaxValue)) { i =>
        fromInt(i).isLeft
      }
    }

    "fail if the code is in the valid range, but not a standard code" in {
      fromInt(371) must beLeft
      fromInt(482) must beLeft
    }

    "yield a status with the standard reason for a standard code" in {
      getStatus(NotFound.code).reason must_== "Not Found"
    }
  }

  "Finding a status by code and reason" should {
    "fail if the code is not in the valid range" in {
      fromIntAndReason(17, "a reason") must beLeft
    }

    "fail if the code is in the valid range, but not a standard code" in {
      fromIntAndReason(371, "some reason") must beLeft
      fromIntAndReason(152, "another reason") must beLeft
    }

    "succeed for a standard code and nonstandard reason, without replacing the default reason" in {
      getStatus(NotFound.code, "My dog ate my homework").reason must_== "My dog ate my homework"

      getStatus(NotFound.code).reason must_== "Not Found"
    }

    "succeed for a standard code and reason" in {
      getStatus(NotFound.code, "Not Found").reason must_== "Not Found"
    }
  }

  private def getStatus(code: Int) =
    fromInt(code).right.get

  private def getStatus(code: Int, reason: String) =
    fromIntAndReason(code, reason).right.get

}
