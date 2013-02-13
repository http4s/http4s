package org.http4s
package attributes

import reflect.ClassTag


trait AttributeKey[T] {
  def classTag: ClassTag[T]
  def name: String
  def description: Option[String]
}

case class ScopedKey[T](scope: Scope, key: AttributeKey[T])



