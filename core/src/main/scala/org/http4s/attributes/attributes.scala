package org.http4s
package attributes

import reflect.ClassTag
import scala.collection.concurrent.TrieMap
import scala.collection._
import collection.immutable.Map


trait AttributeKey[@specialized T] { self =>
  def classTag: ClassTag[T]
  def name: String
  def description: Option[String]
  def in(scope: Scope) = ScopedKey(self, scope)
}

abstract class Key[@specialized T](val description: Option[String] = None)(implicit val classTag: ClassTag[T]) extends AttributeKey[T] {
  val name: String = getClass.getSimpleName.replaceAll("\\$$", "")
}

case class ScopedKey[T](key: AttributeKey[T], scope: Scope) {
  def value: T = scope(key)
  def := (value: T) { scope.put(key, value) }
  def get() = scope.get(key)
  def remove { scope.remove(key) }
  def exists = scope.contains(key)
}

object ScopedAttributes {
  def apply(s: Scope, m: mutable.Map[AttributeKey[_], Any]) = new ScopedAttributes {

    def underlying = m

    def scope: Scope = s
  }
}

trait ScopedAttributes {

  protected def scope: Scope

  protected def underlying: mutable.Map[AttributeKey[_], Any]

  def contains[T](key: AttributeKey[T]) = underlying.contains(key)

  def getOrElse[T](key: AttributeKey[T], default: => T) = underlying.getOrElse(key, default)

  def getOrElseUpdate[T](key: AttributeKey[T], op: => T) = {
    get(key) match {
      case Some(v) => v
      case None => val d = op; update(key, d); d
    }
  }

  def get[T](key: AttributeKey[T]): Option[T] = underlying.get(key).asInstanceOf[Option[T]]

  def apply[T](key: AttributeKey[T]): T = get(key) getOrElse (throw new KeyNotFoundException(key.name, scope))

  def update[T](key:AttributeKey[T], value: T): T = {
    underlying.update(key, value)
    value
  }

  def put[T](key: AttributeKey[T], value: T) = update[T](key, value)

  def updated[T](key: AttributeKey[T], value: T): this.type = {
    underlying.updated(key, value)
    this
  }

  def remove[T](key: AttributeKey[T]) = underlying.remove(key).asInstanceOf[Option[T]]

  def size =  underlying.size

  def empty = ScopedAttributes(scope, underlying.empty)

  def +=[T](kv: (AttributeKey[T], T)): this.type = {
    underlying += kv
    this
  }

  def +=[T](kv1: (AttributeKey[T], T), kv2: (AttributeKey[T], T), kvs: (AttributeKey[T], T)*): this.type = {
    underlying += kv1 += kv2 ++= kvs
    this
  }


  def ++=[T](kv: TraversableOnce[(AttributeKey[T], T)]): this.type = {
    underlying ++= kv
    this
  }

  def -=[T](key: AttributeKey[T]): this.type = { underlying -= key; this }
  def -=[T](key1: AttributeKey[T], key2: AttributeKey[T], keys: AttributeKey[T]*): this.type =
          {  underlying -= key1 -= key2 --= keys; this }

  def --=[T](key: TraversableOnce[AttributeKey[T]]): this.type = { underlying --= key; this }

  def clear() { underlying.clear() }

  def foreach[U](iterator: ((AttributeKey[_], Any)) => U) { underlying.foreach(iterator) }

  def map[B](endo: ((AttributeKey[_], Any)) => B) = underlying.map(endo)
  def flatMap[B](endo: ((AttributeKey[_], Any)) => GenTraversable[B]) = underlying.flatMap(endo)

  def collect[B](collector: PartialFunction[(AttributeKey[_], Any), B]) = underlying.collect(collector)

  def collectFirst[B](collector: PartialFunction[(AttributeKey[_], Any), B]): Option[B] =
    underlying.collectFirst(collector)

  def toMap: Map[AttributeKey[_], Any] = underlying.toMap

  /** Collects all keys of this map in an iterable collection.
   *
   *  @return the keys of this map as an iterable.
   */
  def keys: Iterable[AttributeKey[_]] = underlying.keys

  /** Collects all values of this map in an iterable collection.
   *
   *  @return the values of this map as an iterable.
   */
  def values: Iterable[Any] = underlying.values

  /** Creates an iterator for all keys.
   *
   *  @return an iterator over all keys.
   */
  def keysIterator: Iterator[AttributeKey[_]] = underlying.keysIterator

  /** Creates an iterator for all values in this map.
   *
   *  @return an iterator over all values that are associated with some key in this map.
   */
  def valuesIterator: Iterator[Any] = underlying.valuesIterator

  /** Filters this map by retaining only keys satisfying a predicate.
   *  @param  p   the predicate used to test keys
   *  @return an immutable map consisting only of those key value pairs of this map where the key satisfies
   *          the predicate `p`. The resulting map wraps the original map without copying any elements.
   */
  def filterKeys(p: AttributeKey[_] => Boolean): ScopedAttributes =
    ScopedAttributes(scope, TrieMap(underlying.filterKeys(p).toSeq:_*))

  /** Transforms this map by applying a function to every retrieved value.
   *  @param  f   the function used to transform values of this map.
   *  @return a map view which maps every key of this map
   *          to `f(this(key))`. The resulting map wraps the original map without copying any elements.
   */
  def mapValues[C](f: Any => C): ScopedAttributes = {
    ScopedAttributes(scope, TrieMap(underlying.mapValues(f).toSeq:_*))
  }

}

