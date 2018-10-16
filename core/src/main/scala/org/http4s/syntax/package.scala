package org.http4s

package object syntax {
  object all extends AllSyntax
  @deprecated("Has nothing to do with HTTP.", "0.19.1")
  object async extends AsyncSyntax
  @deprecated("Use map or flatMap on the request instead", "0.18.0-M2")
  object effectRequest extends EffectRequestSyntax
  @deprecated("Use map or flatMap on the request instead", "0.18.0-M2")
  object effectResponse extends EffectResponseSyntax
  object kleisli extends KleisliSyntax
  object literals extends LiteralsSyntax
  @deprecated("Use cats.foldable._", "0.18.5")
  object nonEmptyList extends NonEmptyListSyntax
  object string extends StringSyntax
}
