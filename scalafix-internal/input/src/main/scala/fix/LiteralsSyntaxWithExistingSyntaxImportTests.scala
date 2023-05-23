/*
rule = Http4sUseLiteralsSyntax
*/

package fix

import org.http4s.Uri

object LiteralsSyntaxWithExistingSyntaxImportTests {
  Uri.unsafeFromString("foo.com")
}
