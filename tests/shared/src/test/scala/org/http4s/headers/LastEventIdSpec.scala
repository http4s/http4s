package org.http4s
package headers

import org.http4s.ServerSentEvent._
import org.scalacheck._
import org.scalacheck.Arbitrary._

class LastEventIdSpec extends HeaderLaws {
  implicit val arbLastEventId: Arbitrary[`Last-Event-Id`] =
    Arbitrary(for {
      id <- arbitrary[String]
      if !id.contains("\uffff")
    } yield {
      `Last-Event-Id`(EventId(id))
    })

  checkAll("Last-Event-Id", headerLaws(`Last-Event-Id`))
}
