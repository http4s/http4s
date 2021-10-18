package fix

import org.http4s.Uri
import org.http4s.syntax.string._

object UriPathTests {
  Uri(path = path"foo/bar")
  Uri(None, None, path"foo/bar")
}
