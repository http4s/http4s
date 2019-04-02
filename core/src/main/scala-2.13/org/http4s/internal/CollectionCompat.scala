package org.http4s.internal

import scala.collection.immutable
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

private[http4s] object CollectionCompat {
  type LazyList[A] = scala.collection.immutable.LazyList[A]
  val LazyList = scala.collection.immutable.LazyList

  def pairsToMultiParams[K, V](map: collection.Seq[(K, Option[V])]): Map[K, immutable.Seq[V]] = {
    if (map.isEmpty) Map.empty
    else {
      val m = mutable.Map.empty[K, ListBuffer[V]]
      map.foreach {
        case (k, None) => m.getOrElseUpdate(k, new ListBuffer)
        case (k, Some(v)) => m.getOrElseUpdate(k, new ListBuffer) += v
      }
      m.view.mapValues(_.toList).toMap
    }
  }

  def mapValues[K, A, B](map: Map[K, A])(f: A => B): Map[K, B] =
    map.view.mapValues(f).toMap
}
