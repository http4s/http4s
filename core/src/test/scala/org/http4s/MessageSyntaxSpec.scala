package org.http4s

import org.http4s.Header.`Content-Type`
import org.specs2.mutable.Specification
import Http4s._

import scalaz.concurrent.Task

class MessageSyntaxSpec extends Specification {

  "toBody syntax" should {
    "Honor the 'replaceHeaders' flag" in {

      ////// default to not replacing existing headers /////////////
      val req1 = Request().withType(MediaType.`application/octet-stream`)
                          .withBody("foo")

      req1.run.headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`application/octet-stream`))

      val req2 = Task(Response()).withType(MediaType.`application/octet-stream`)
                                 .withBody("foo")

      req2.run.headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`application/octet-stream`))

      ////// replace flag set ///////////////////////////////

      val req3 = Request().withType(MediaType.`application/octet-stream`)
                          .withBody("foo", true)

      req3.run.headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`text/plain`, Charset.`UTF-8`))

      val req4 = Task(Response()).withType(MediaType.`application/octet-stream`)
                                 .withBody("foo", true)

      req4.run.headers.get(`Content-Type`) must_== Some(`Content-Type`(MediaType.`text/plain`, Charset.`UTF-8`))
    }
  }

}
