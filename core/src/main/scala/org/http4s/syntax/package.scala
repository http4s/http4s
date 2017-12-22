package org.http4s

package object syntax {
  object all extends AllSyntax
  object async extends AsyncSyntax
  object byteChunk extends ByteChunkSyntax
  @deprecated("Use map or flatMap on the request instead", "0.18.0-M2")
  object effectRequest extends EffectRequestSyntax
  @deprecated("Use map or flatMap on the request instead", "0.18.0-M2")
  object effectResponse extends EffectResponseSyntax
  object kleisli extends KleisliSyntax
  object literals extends LiteralsSyntax
  object nonEmptyList extends NonEmptyListSyntax
  object string extends StringSyntax
}
