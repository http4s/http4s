package org.http4s
package attributes

import reflect.ClassTag
import scala.collection.concurrent.TrieMap
import scala.collection._
import collection.immutable.Map


abstract class Key[T](val description: Option[String] = None)(implicit val classTag: ClassTag[T]) {
  val name: String = getClass.getSimpleName.replaceAll("\\$$", "")
  def in(scope: Scope) = ScopedKey(this, scope)
  final override def hashCode(): Int = super.hashCode()
  mutable.HashMap.empty[String, Int].put("", 4)
}

case class ScopedKey[T](key: Key[T], scope: Scope, override val description: Option[String] = None)(implicit classTag: ClassTag[T])
  extends Key[T](description) {
  def value: T = scope(key)
  def := (value: T) { scope.put(key, value) }
  def get() = scope.get(key)
  def remove { scope.remove(key) }
  def exists = scope.contains(key)
}

object ScopedAttributes {
  private[attributes] def newMutable(s: Scope, m: mutable.Map[Key[_ >: Nothing <: Any], _]) = new ScopedAttributes {

    def underlying = m.asInstanceOf[mutable.Map[Key[_], Any]]

    def scope: Scope = s
  }

  def apply(s: Scope, m: Map[Key[_ >: Nothing <: Any], _]) = new ScopedAttributes {

    def underlying = mutable.Map(m.toSeq:_*)

    def scope: Scope = s
  }
}

trait ScopedAttributes {

  protected def scope: Scope

  protected def underlying: mutable.Map[Key[_], Any]

  def contains[T](key: Key[T]) = underlying.contains(key)

  def getOrElse[T](key: Key[T], default: => T) = underlying.getOrElse(key, default)

  def getOrElseUpdate[T](key: Key[T], op: => T) = {
    get(key) match {
      case Some(v) => v
      case None => val d = op; update(key, d); d
    }
  }

  def get[T](key: Key[T]): Option[T] = underlying.get(key).asInstanceOf[Option[T]]

  def apply[T](key: Key[T]): T = get(key) getOrElse (throw new KeyNotFoundException(key.name, scope))

  def update[T](key:Key[T], value: T): T = {
    underlying.update(key, value)
    value
  }

  def put[T](key: Key[T], value: T) = update[T](key, value)

  def updated[T](key: Key[T], value: T): this.type = {
    underlying.updated(key, value)
    this
  }

  def remove[T](key: Key[T]) = underlying.remove(key).asInstanceOf[Option[T]]

  def size =  underlying.size

  def empty = ScopedAttributes.newMutable(scope, underlying.empty)

  def +=[T](kv: (Key[T], T)): this.type = {
    underlying += kv
    this
  }

  def +=[T](kv1: (Key[T], T), kv2: (Key[T], T), kvs: (Key[T], T)*): this.type = {
    underlying += kv1 += kv2 ++= kvs
    this
  }


  def ++=[T](kv: TraversableOnce[(Key[T], T)]): this.type = {
    underlying ++= kv
    this
  }

  def -=[T](key: Key[T]): this.type = { underlying -= key; this }
  def -=[T](key1: Key[T], key2: Key[T], keys: Key[T]*): this.type =
          {  underlying -= key1 -= key2 --= keys; this }

  def --=[T](key: TraversableOnce[Key[T]]): this.type = { underlying --= key; this }

  def clear() { underlying.clear() }

  def foreach[U](iterator: ((Key[_], Any)) => U) { underlying.foreach(iterator) }

  def map[B](endo: ((Key[_], Any)) => B) = underlying.map(endo)
  def flatMap[B](endo: ((Key[_], Any)) => GenTraversable[B]) = underlying.flatMap(endo)

  def collect[B](collector: PartialFunction[(Key[_], Any), B]) = underlying.collect(collector)

  def collectFirst[B](collector: PartialFunction[(Key[_], Any), B]): Option[B] =
    underlying.collectFirst(collector)

  def toMap: Map[Key[_], Any] = underlying.toMap

  /** Collects all keys of this map in an iterable collection.
   *
   *  @return the keys of this map as an iterable.
   */
  def keys: Iterable[Key[_]] = underlying.keys

  /** Collects all values of this map in an iterable collection.
   *
   *  @return the values of this map as an iterable.
   */
  def values: Iterable[Any] = underlying.values

  /** Creates an iterator for all keys.
   *
   *  @return an iterator over all keys.
   */
  def keysIterator: Iterator[Key[_]] = underlying.keysIterator

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
  def filterKeys(p: Key[_] => Boolean): ScopedAttributes =
    ScopedAttributes.newMutable(scope, TrieMap(underlying.filterKeys(p).toSeq:_*))

  /** Transforms this map by applying a function to every retrieved value.
   *  @param  f   the function used to transform values of this map.
   *  @return a map view which maps every key of this map
   *          to `f(this(key))`. The resulting map wraps the original map without copying any elements.
   */
  def mapValues[C](f: Any => C): ScopedAttributes = {
    ScopedAttributes.newMutable(scope, TrieMap(underlying.mapValues(f).toSeq:_*))
  }
}

