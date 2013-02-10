package org.http4s
package attributes

import reflect.ClassTag
import scalaz._
import scalaz.syntax.state._

trait AttributeKey[T] {
  def classTag: ClassTag[T]
  def name: String
  def description: Option[String]
}

case class ScopedKey[T](scope: Scope, key: AttributeKey[T])

//object State {
//  def constant[S, A](a: A) = new State((s: S) => (s, a))
//}
//case class State[S, A](s: S => (S, A)) {
//  def map[B](f: A => B): State[S, B] = flatMap((a: A) => State.constant(f(a)))
//  def flatMap[B](f: A => State[S, B]): State[S, B] = State((x: S) => {
//    val (a, y) = s(x)
//    f(y).s(a)
//  })
//}
case class Setting[T](key: ScopedKey[T], value: State)

