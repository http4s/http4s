package org.http4s
package headers

/** A Header that is already parsed from its String representation. */
trait ParsedHeader extends Header {
  def key: HeaderKey
  def name = key.name
  final def parsed: this.type = this
}
