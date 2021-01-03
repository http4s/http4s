/*
 * Copyright 2019 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.chrisdavenport.keypool.internal

import cats._
import cats.implicits._
import scala.concurrent.duration._

private[keypool] sealed trait PoolList[A] extends Product with Serializable {
  def toList: List[(FiniteDuration, A)] = this match {
    case One(a, created) => List((created, a))
    case Cons(a, _, created, tail) => (created, a) :: tail.toList
  }
}
private[keypool] object PoolList {
  def fromList[A](l: List[(FiniteDuration, A)]): Option[PoolList[A]] = l match {
    case Nil => None
    case (t, a) :: Nil => Some(One(a, t))
    case list =>
      def go(l: List[(FiniteDuration, A)]): (Int, PoolList[A]) = l match {
        case Nil => throw new Throwable("PoolList.fromList Nil")
        case (t, a) :: Nil => (2, One(a, t))
        case (t, a) :: rest =>
          val (i, rest_) = go(rest)
          val i_ = i + 1
          (i_, Cons(a, i, t, rest_))
      }
      Some(go(list)._2)
  }

  implicit val poolListFoldable: Foldable[PoolList] = new Foldable[PoolList] {
    def foldLeft[A, B](fa: PoolList[A], b: B)(f: (B, A) => B): B =
      Foldable[List].foldLeft(fa.toList.map(_._2), b)(f)
    def foldRight[A, B](fa: PoolList[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] =
      Foldable[List].foldRight(fa.toList.map(_._2), lb)(f)
  }
}

private[keypool] final case class One[A](a: A, created: FiniteDuration) extends PoolList[A]
private[keypool] final case class Cons[A](
    a: A,
    length: Int,
    created: FiniteDuration,
    xs: PoolList[A])
    extends PoolList[A]
