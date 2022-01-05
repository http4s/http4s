package fix

import org.http4s.Uri
import org.http4s.syntax.literals._

object LiteralsSyntaxTests {
  val s = "foo.com"

  uri"foo.com"
  uri"""foo.com"""
  Uri.unsafeFromString("foo" + ".com")
  Uri.unsafeFromString(s)
  Uri.unsafeFromString(s"http://$s")

  path"foo/bar"
}
