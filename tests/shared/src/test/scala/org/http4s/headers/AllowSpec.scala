package org.http4s.headers

class AllowSpec extends HeaderLaws {
  checkAll("Allow", headerLaws(Allow))
}
