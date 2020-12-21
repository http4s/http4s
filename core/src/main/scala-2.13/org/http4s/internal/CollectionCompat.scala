/*
 * Copyright 2013 http4s.org
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

package org.http4s.internal

import scala.collection.immutable
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

private[http4s] object CollectionCompat {
  type LazyList[A] = scala.collection.immutable.LazyList[A]
  val LazyList = scala.collection.immutable.LazyList

  def pairsToMultiParams[K, V](map: collection.Seq[(K, Option[V])]): Map[K, immutable.Seq[V]] =
    if (map.isEmpty) Map.empty
    else {
      val m = mutable.Map.empty[K, ListBuffer[V]]
      map.foreach {
        case (k, None) => m.getOrElseUpdate(k, new ListBuffer)
        case (k, Some(v)) => m.getOrElseUpdate(k, new ListBuffer) += v
      }
      m.view.mapValues(_.toList).toMap
    }

  def mapValues[K, A, B](map: Map[K, A])(f: A => B): Map[K, B] =
    map.view.mapValues(f).toMap

  val CollectionConverters = scala.jdk.CollectionConverters
}
