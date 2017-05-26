package org.http4s

package object syntax {
  object all extends AllSyntax
  object async extends AsyncSyntax
  object service extends ServiceSyntax
  object streamCats extends StreamCatsSyntax
  object string extends StringSyntax
  object taskRequest extends TaskRequestSyntax
  object taskResponse extends TaskResponseSyntax
}
