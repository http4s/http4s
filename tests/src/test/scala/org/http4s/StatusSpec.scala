package org.http4s

import cats.implicits._
import cats.kernel.laws.discipline.OrderTests
import org.http4s.Status._
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

class StatusSpec extends Http4sSpec {

  "codes from 100 to 199 are informational" in responseClassTest(100 to 199, Informational)
  "codes from 200 to 299 are successful" in responseClassTest(200 to 299, Successful)
  "codes from 300 to 399 are redirectional" in responseClassTest(300 to 399, Redirection)
  "codes from 400 to 499 are client errors" in responseClassTest(400 to 499, ClientError)
  "codes from 500 to 599 are server errors" in responseClassTest(500 to 599, ServerError)

  private def responseClassTest(range: Range, responseClass: ResponseClass) =
    foreach(range) { i =>
      fromInt(i) must beRight.like {
        case s => s.responseClass must_== responseClass
      }
    }

  checkAll("Status", OrderTests[Status].order)

  "Statuses" should {
    "not be equal if their codes are not" in {
      prop { (s1: Status, s2: Status) =>
        (s1.code != s2.code) ==> (s1 != s2)
      }
    }

    "be equal if their codes are" in {
      forAll(Gen.choose(MinCode, MaxCode)) { i =>
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
  }

  "Finding a status by code" should {
    "fail if the code is not in the range of valid codes" in {
      forAll(Gen.choose(Int.MinValue, 99)) { i =>
        fromInt(i).isLeft
      }

      forAll(Gen.choose(600, Int.MaxValue)) { i =>
        fromInt(i).isLeft
      }
    }
    "succeed if the code is in the valid range" in {
      foreach(100 to 599) { i =>
        fromInt(i).isRight
      }
    }

    "yield a status with the standard reason for a standard code" in {
      getStatus(NotFound.code).reason must_== "Not Found"
    }
  }

  "Finding a status by code and reason" should {
    "fail if the code is not in the valid range" in {
      fromIntAndReason(17, "a reason") must beLeft
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
