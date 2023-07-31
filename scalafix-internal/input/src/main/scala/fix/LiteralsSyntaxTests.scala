/*
rule = Http4sUseLiteralsSyntax
*/

package fix

import org.http4s.Uri

object LiteralsSyntaxTests {
  val s = "foo.com"

  Uri.unsafeFromString("foo.com")
  Uri.unsafeFromString("""foo.com""")
  Uri.unsafeFromString("foo" + ".com")
  Uri.unsafeFromString(s)
  Uri.unsafeFromString(s"http://$s")

  Uri.Path.unsafeFromString("foo/bar")
}
