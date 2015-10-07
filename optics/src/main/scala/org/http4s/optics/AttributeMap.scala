package org.http4s.optics

import monocle.Lens
import monocle.function.{At, Index}
import org.http4s.{AttributeKey, AttributeMap}

object attributemap {

  implicit def atAttributeMap[T]: At[AttributeMap, AttributeKey[T], T] = new At[AttributeMap, AttributeKey[T], T] {
    override def at(i: AttributeKey[T]): Lens[AttributeMap, Option[T]] =
      Lens[AttributeMap, Option[T]](_.get(i)){
        case None    => _.remove(i)
        case Some(v) => _.put(i, v)
      }
  }

  implicit def indexAttributeMap[T]: Index[AttributeMap, AttributeKey[T], T] =
    Index.atIndex(atAttributeMap)

}
