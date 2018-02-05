package org.http4s
package headers

class ContentTypeHeaderSpec extends HeaderLaws {
  checkAll("Content-Type", headerLaws(`Content-Type`))
}
