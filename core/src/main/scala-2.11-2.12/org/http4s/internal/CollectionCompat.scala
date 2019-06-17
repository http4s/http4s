package org.http4s.internal

import scala.collection.immutable
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

private[http4s] object CollectionCompat {
  type LazyList[A] = Stream[A]
  val LazyList = Stream

  def pairsToMultiParams[K, V](map: collection.Seq[(K, Option[V])]): Map[K, immutable.Seq[V]] =
    if (map.isEmpty) Map.empty
    else {
      val m = mutable.Map.empty[K, ListBuffer[V]]
      map.foreach {
        case (k, None) => m.getOrElseUpdate(k, new ListBuffer)
        case (k, Some(v)) => m.getOrElseUpdate(k, new ListBuffer) += v
      }
      m.toMap.mapValues(_.toList)
    }

  def mapValues[K, A, B](map: collection.Map[K, A])(f: A => B): Map[K, B] =
    map.mapValues(f).toMap

  val CollectionConverters = scala.collection.JavaConverters
}
