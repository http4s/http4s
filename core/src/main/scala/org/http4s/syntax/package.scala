package org.http4s

package object syntax {
  object all extends AllSyntax
  object async extends AsyncSyntax
  @deprecated("Use map or flatMap on the request instead", "0.18.0-M2")
  object effectRequest extends EffectRequestSyntax
  @deprecated("Use map or flatMap on the request instead", "0.18.0-M2")
  object effectResponse extends EffectResponseSyntax
  object kleisliResponse extends KleisliResponseSyntax
  object string extends StringSyntax
}
