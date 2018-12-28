package org.http4s.client
package blaze

sealed abstract class ParserMode extends Product with Serializable

object ParserMode {
  final case object Strict extends ParserMode
  final case object Lenient extends ParserMode
}
