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

  "The collection of registered statuses" should {
    "contain 61 standard ones" in {
      Status.registered.size must_== 61
    }

    "not contain any custom statuses" in {
      getStatus(371)
      Status.registered.size must_== 61
    }
  }

  "Finding a status by code" should {

    "fail if the code is not in the range of valid codes" in {
      forAll(Gen.choose(Int.MinValue, 99)) { i =>
        fromInt(i).isLeft
      }
    }

    "fail if the code is not in the range of valid codes" in {
      forAll(Gen.choose(600, Int.MaxValue)) { i =>
        fromInt(i).isLeft
      }
    }

    "succeed if the code is in the valid range, but not a standard code" in {
      fromInt(371) must beRight.like { case s => s.reason must_== "" }
      fromInt(482) must beRight
    }

    "yield a status with the standard reason for a standard code" in {
      getStatus(NotFound.code).reason must_== "Not Found"
    }
  }

  "Finding a status by code and reason" should {
    "fail if the code is not in the valid range" in {
      fromIntAndReason(17, "a reason") must beLeft
    }

    "succeed if the code is in the valid range, but not a standard code" in {
      val s1 = getStatus(371, "some reason")
      s1.code must_== 371
      s1.reason must_== "some reason"
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
