package org.http4s
package headers

class ContentEncodingSpec extends HeaderLaws {
  checkAll("Content-Encoding", headerLaws(`Content-Encoding`))
}
