/*
rule = Http4sUseLiteralsSyntax
*/

package fix

import org.http4s.Uri
import org.http4s.syntax.all._

object LiteralsSyntaxWithExistingSyntaxImportTests {
  Uri.unsafeFromString("foo.com")
}
