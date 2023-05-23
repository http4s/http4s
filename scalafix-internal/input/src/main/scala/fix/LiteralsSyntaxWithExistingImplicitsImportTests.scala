/*
rule = Http4sUseLiteralsSyntax
*/

package fix

import org.http4s.Uri

object LiteralsSyntaxWithExistingImplicitsImportTests {
  Uri.unsafeFromString("foo.com")
}
