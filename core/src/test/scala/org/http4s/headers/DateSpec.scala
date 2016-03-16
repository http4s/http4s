package org.http4s
package headers

class DateSpec extends HeaderLaws {
  checkAll("Date", headerLaws(Date))
}
