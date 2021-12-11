/*
rule = v0_22
*/

package fix

import org.http4s.Uri

object UriPathTests {
  Uri(path = "foo/bar")
  Uri(None, None, "foo/bar")
}
