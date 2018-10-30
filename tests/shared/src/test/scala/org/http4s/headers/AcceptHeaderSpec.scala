package org.http4s
package headers

class AcceptHeaderSpec extends HeaderLaws {
  checkAll("Accept", headerLaws(Accept))
}
