package org.http4s
package headers

import scalaz.NonEmptyList

/**
 * A recurring header that satisfies this clause of the Spec:
 *
 * Multiple message-header fields with the same field-name MAY be present in a message if and only if the entire
 * field-value for that header field is defined as a comma-separated list [i.e., #(values)]. It MUST be possible
 * to combine the multiple header fields into one "field-name: field-value" pair, without changing the semantics
 * of the message, by appending each subsequent field-value to the first, each separated by a comma.
 */
trait RecurringHeader extends ParsedHeader {
  type Value
  def key: HeaderKey.Recurring
  def values: NonEmptyList[Value]
}
