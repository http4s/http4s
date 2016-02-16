package org.http4s
package headers

class DateSpec extends Http4sSpec with HeaderLaws[Date] {
  checkHeaderLaws
}
