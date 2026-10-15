/*
rule = Http4sUseLiteralsSyntax
*/

package fix

import org.http4s.Uri
import org.http4s.implicits._

object LiteralsSyntaxWithExistingImplicitsImportTests {
  val existing: Uri = uri"existing.com"

  Uri.unsafeFromString("foo.com")
}
