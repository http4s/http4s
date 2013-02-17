package org.http4s
package attributes

import reflect.ClassTag
import concurrent.stm._
import shapeless.TypeOperators._
import collection.GenTraversable


trait AttributeKey[@specialized T] {
  type ValueType = T
  def classTag: ClassTag[T]
  def name: String
  def description: Option[String]
}

abstract class Key[@specialized T](val name: String, val description: Option[String] = None)(implicit val classTag: ClassTag[T]) extends AttributeKey[T]

class ScopedAttributes[S <: Scope](val scope: S, underlying: TMap[AttributeKey[_] @@ S, Any]) {

  def contains[T](key: AttributeKey[T] @@ S) = atomic { implicit txn => underlying.contains(key) }

  def get[T](key: AttributeKey[T] @@ S): Option[T] = atomic { implicit txn =>
    underlying.get(key) map (_.asInstanceOf[T])
  }

  def apply[T](key: AttributeKey[T] @@ S): T = get(key) getOrElse (throw new KeyNotFoundException(key.name, scope))

  def update[T](key:AttributeKey[T] @@ S, value: T): T = atomic { implicit txn =>
    underlying.update(key, value)
    value
  }

  def put[T](key: AttributeKey[T] @@ S, value: T) = update[T](key, value)

  def updated[T](key: AttributeKey[T] @@ S, value: T): this.type = atomic { implicit txn =>
    underlying.updated(key, value)
    this
  }

  def remove[T](key: AttributeKey[T] @@ S) = atomic { implicit txn => underlying.remove(key).map(_.asInstanceOf[T]) }

  def size = atomic { implicit txn => underlying.size }

  def isEmpty = atomic { implicit txn => underlying.isEmpty }

  def +=[T](kv: (AttributeKey[T] @@ S, T)): this.type = atomic { implicit txn =>
    underlying += kv
    this
  }

  def +=[T](kv1: (AttributeKey[T] @@ S, T), kv2: (AttributeKey[T]@@S, T), kvs: (AttributeKey[T]@@S, T)*): this.type = atomic { implicit txn =>
    underlying += kv1 += kv2 ++= kvs
    this
  }


  def ++=[T](kv: TraversableOnce[(AttributeKey[T] @@ S, T)]): this.type = atomic { implicit txn =>
    underlying ++= kv
    this
  }

  def -=[T](key: AttributeKey[T] @@ S): this.type = atomic { implicit txn => underlying -= key; this }
  def -=[T](key1: AttributeKey[T] @@ S, key2: AttributeKey[T] @@ S, keys: AttributeKey[T] @@ S*): this.type =
    atomic { implicit txn => underlying -= key1 -= key2 --= keys; this }

  def --=[T](key: TraversableOnce[AttributeKey[T] @@ S]): this.type = atomic { implicit txn => underlying --= key; this }

  def foreach[U](iterator: ((AttributeKey[_] @@ S, Any)) => U) { underlying.single.foreach(iterator) }

  def map[B](endo: ((AttributeKey[_] @@ S, Any)) => B) = underlying.single.map(endo)
  def flatMap[B](endo: ((AttributeKey[_] @@ S, Any)) => GenTraversable[B]) = underlying.single.flatMap(endo)

  def collect[B](collector: PartialFunction[(AttributeKey[_] @@ S, Any), B]) = underlying.single.collect(collector)
  def collectFirst[B](collector: PartialFunction[(AttributeKey[_] @@ S, Any), B]): Option[B] =
    underlying.single.collectFirst(collector)

}
