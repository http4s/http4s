package org.http4s
package client

class UnexpectedStatusSpec extends Http4sSpec {
  "UnexpectedStatus" should {
    "include status and original request in message" in {
      val e = UnexpectedStatus(Status.NotFound, Method.GET, Uri.unsafeFromString("www.google.com"))
      e.getMessage() must_== "unexpected HTTP status: 404 Not Found for request GET www.google.com"
    }

    "not return null" in {
      prop { (status: Status) =>
        val e = UnexpectedStatus(status, Method.GET, Uri.unsafeFromString("www.google.it"))
        e.getMessage() must not beNull
      }
    }
  }
}
