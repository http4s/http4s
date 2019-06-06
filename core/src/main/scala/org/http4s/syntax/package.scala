package org.http4s

package object syntax {
  object all extends AllSyntaxBinCompat
  @deprecated("Has nothing to do with HTTP.", "0.19.1")
  object async extends AsyncSyntax
  object kleisli extends KleisliSyntax with KleisliSyntaxBinCompat0 with KleisliSyntaxBinCompat1
  object literals extends LiteralsSyntax
  @deprecated("Use cats.foldable._", "0.18.5")
  object nonEmptyList extends NonEmptyListSyntax
  object string extends StringSyntax
}
