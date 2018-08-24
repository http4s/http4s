package org.http4s

import cats.implicits._
import cats.kernel.laws.discipline.OrderTests
import org.http4s.Status._
import org.scalacheck.Gen
import org.scalacheck.Prop.forAll

class StatusSpec extends Http4sSpec {

  "code is valid iff between 100 and 599" in {
    forAll(Gen.choose(Int.MinValue, 99)) { i =>
      fromInt(i).isLeft
    }
    foreach(100 to 599) { i =>
      fromInt(i).isRight
    }
    forAll(Gen.choose(600, Int.MaxValue)) { i =>
      fromInt(i).isLeft
    }
  }

  private def getStatus(code: Int) =
    fromInt(code).right.get

  private def getStatus(code: Int, reason: String) =
    fromIntAndReason(code, reason).right.get

  private def responseClassTest(range: Range, responseClass: ResponseClass) =
    foreach(range) { i =>
      fromInt(i) must beRight.like {
        case s => s.responseClass must_== responseClass
      }
    }

  "codes from 100 to 199 are informational" in responseClassTest(100 to 199, Informational)
  "codes from 200 to 299 are successful" in responseClassTest(200 to 299, Successful)
  "codes from 300 to 399 are redirectional" in responseClassTest(300 to 399, Redirection)
  "codes from 400 to 499 are client errors" in responseClassTest(400 to 499, ClientError)
  "codes from 500 to 599 are server errors" in responseClassTest(500 to 599, ServerError)

  checkAll("Status", OrderTests[Status].order)

  "status is not equal if code is not equal" in {
    prop { (s1: Status, s2: Status) =>
      (s1.code != s2.code) ==> (s1 != s2)
    }
  }

  "status is equal if reason is equal" in {
    prop { (s1: Status, reason: String) =>
      (s1.reason != reason) ==> (s1 == s1.withReason(reason))
    }
  }

  "statuses are equal if their codes are" in {
    val s1: Status = getStatus(200, "a reason")
    val s2: Status = getStatus(200, "another reason")
    s1 must_== s2
  }

  "finding existing statuses by code, and by code and reason" in {
    getStatus(NotFound.code).reason must_== "Not Found"

    getStatus(NotFound.code, "Not Found").reason must_== "Not Found"
  }

  "finding a status by a standard code, but nonstandard reason" in {
    getStatus(NotFound.code, "My dog ate my homework").reason must_== "My dog ate my homework"

    getStatus(NotFound.code).reason must_== "Not Found"
  }

}
