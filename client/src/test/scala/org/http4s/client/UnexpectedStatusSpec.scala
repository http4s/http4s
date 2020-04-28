package org.http4s
package client

class UnexpectedStatusSpec extends Http4sSpec {
  "UnexpectedStatus" should {
    "include status and original request in message" in {
      val e = UnexpectedStatus(Status.NotFound, Uri.unsafeFromString("www.google.com"), Method.GET)
      e.getMessage() must_== "unexpected HTTP status: 404 Not Found for request GET www.google.com"
    }

    "not return null" in {
      prop { (status: Status) =>
        val e = UnexpectedStatus(status, Uri.unsafeFromString("www.google.it"), Method.GET)
        e.getMessage() must not beNull
      }
    }
  }
}
