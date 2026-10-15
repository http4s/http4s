package fix

import org.http4s.Uri
import org.http4s.syntax.all._

object LiteralsSyntaxWithExistingSyntaxImportTests {
  val existing: Uri = uri"existing.com"

  uri"foo.com"
}
