package org.http4s.headers

import org.http4s.Http4sSpec

class AllowSpec extends HeaderLaws {
  checkAll("Allow", headerLaws(Allow))
}
