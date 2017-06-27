package org.http4s

package object syntax {
  object all extends AllSyntax
  object async extends AsyncSyntax
  object effectRequest extends EffectRequestSyntax
  object effectResponse extends EffectResponseSyntax
  object service extends ServiceSyntax
  object string extends StringSyntax
}
