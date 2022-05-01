package fix

import org.http4s.Uri
import org.http4s.syntax.literals._

object UriPathTests {
  Uri(path = path"foo/bar")
  Uri(None, None, path"foo/bar")
}
