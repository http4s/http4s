package org.http4s
package attributes

import reflect.ClassTag
import scala.collection.concurrent.TrieMap
import scala.collection._
import collection.generic.{Shrinkable, Growable, CanBuildFrom}
import collection.immutable.Map
import collection.parallel.immutable.ParMap
import JavaConverters._
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import scala.collection
import scala.collection


trait AttributeKey[@specialized T] { self =>
  def classTag: ClassTag[T]
  def name: String
  def description: Option[String]
  def in(scope: Scope) = ScopedKey(self, scope)
}

abstract class Key[@specialized T](val description: Option[String] = None)(implicit val classTag: ClassTag[T]) extends AttributeKey[T] {
  def this()(implicit classTag: ClassTag[T]) = this(None)(classTag)

  val name: String = getClass.getSimpleName.replaceAll("\\$$", "")
}

case class ScopedKey[T](key: AttributeKey[T], scope: Scope) {
  def value: T = scope(key)
  def := (value: T) { scope.put(key, value) }
  def get() = scope.get(key)
  def remove { scope.remove(key) }
  def exists = scope.contains(key)
}

//object Attributes {
//  /** An empty $Coll */
//  def empty = new Attributes(immutable.Map.empty)
//
//  /** A collection of type Attributes that contains given key/value bindings.
//   *  @param elems   the key/value pairs that make up the $coll
//   *  @return        a new Attributes consisting key/value pairs given by `elems`.
//   */
//  def apply(elems: (AttributeKey[_], Any)*): Attributes = (newBuilder ++= elems).result
//
//  /** The default builder for Attributes objects.
//   */
//  def newBuilder: mutable.Builder[(AttributeKey[_], Any), Attributes] = new AttributesBuilder(mutable.Map.empty)
//
//  class AttributesBuilder(empty: mutable.Map[AttributeKey[_], Any]) extends mutable.Builder[(AttributeKey[_], Any), Attributes] {
//    private[this] val coll = empty
//    def +=(elem: (AttributeKey[_], Any)): this.type = {
//      coll += elem
//      this
//    }
//
//    def clear() { coll.clear() }
//
//    def result(): Attributes = new Attributes(coll.toMap)
//  }
//
//  type AttributePair[T] = (AttributeKey[T], T)
//
//  implicit def canBuildFrom: CanBuildFrom[TraversableOnce[(AttributeKey[_], Any)], (AttributeKey[_], Any), Attributes] =
//    new CanBuildFrom[TraversableOnce[(AttributeKey[_], Any)], (AttributeKey[_], Any), Attributes] {
//      def apply(from: TraversableOnce[(AttributeKey[_], Any)]): mutable.Builder[(AttributeKey[_], Any), Attributes] =
//        new AttributesBuilder(mutable.Map(from.toSeq:_*))
//
//      def apply(): mutable.Builder[(AttributeKey[_], Any), Attributes] = new AttributesBuilder(mutable.Map.empty)
//    }
//}
//class Attributes(store: Map[AttributeKey[_], Any] = Map.empty)
//  extends Iterable[(AttributeKey[_], Any)] with IterableLike[(AttributeKey[_], Any), Attributes] {
//
//  protected[this] override def parCombiner = ParMap.newCombiner[AttributeKey[_], Any]
//
//  def iterator: Iterator[(AttributeKey[_], Any)] = store.iterator
//
//  def toMap: Map[AttributeKey[_], Any] = store
//
//  override protected[this] def newBuilder: mutable.Builder[(AttributeKey[_], Any), Attributes] = Attributes.newBuilder
//  def empty: Attributes = Attributes.empty
//  def get[T](key: AttributeKey[T]): Option[T] = (store get key) map (_.asInstanceOf[T])
//  def apply[T](key: AttributeKey[T]): T = get(key) getOrElse default(key)
//  def contains[T](key: AttributeKey[T]): Boolean = store contains key
//  def getOrElse[T](key: AttributeKey[T], default: => T): T = get(key) getOrElse default
//  override def seq: Attributes = this
//  def +[T](kv: Attributes.AttributePair[T]): Attributes = new Attributes(store + kv)
//  def -[T](key: AttributeKey[T]): Attributes = new Attributes(store - key)
//  def default[T](key: AttributeKey[T]): T = throw new NoSuchElementException("Can't find " + key)
//
//  /** A new immutable map containing updating this map with a given key/value mapping.
//   *  @param    key the key
//   *  @param    value the value
//   *  @return   A new map with the new key/value mapping
//   */
//  def updated[T](key: AttributeKey[T], value: T): Attributes = this + ((key, value))
//
//  def keySet: Set[AttributeKey[_]] = store.keySet
//
//  /** Collects all keys of this map in an iterable collection.
//   *
//   *  @return the keys of this map as an iterable.
//   */
//  def keys: Iterable[AttributeKey[_]] = store.keys
//
//  /** Collects all values of this map in an iterable collection.
//   *
//   *  @return the values of this map as an iterable.
//   */
//  def values: Iterable[Any] = store.values
//
//  /** Creates an iterator for all keys.
//   *
//   *  @return an iterator over all keys.
//   */
//  def keysIterator: Iterator[AttributeKey[_]] = store.keysIterator
//
//  /** Creates an iterator for all values in this map.
//   *
//   *  @return an iterator over all values that are associated with some key in this map.
//   */
//  def valuesIterator: Iterator[Any] = store.valuesIterator
//
//  /** Filters this map by retaining only keys satisfying a predicate.
//   *  @param  p   the predicate used to test keys
//   *  @return an immutable map consisting only of those key value pairs of this map where the key satisfies
//   *          the predicate `p`. The resulting map wraps the original map without copying any elements.
//   */
//  def filterKeys(p: AttributeKey[_] => Boolean): Attributes = new Attributes(store.filterKeys(p))
//
//  /** Transforms this map by applying a function to every retrieved value.
//   *  @param  f   the function used to transform values of this map.
//   *  @return a map view which maps every key of this map
//   *          to `f(this(key))`. The resulting map wraps the original map without copying any elements.
//   */
//  def mapValues[C](f: Any => C): Attributes = new Attributes(store.mapValues(f))
//
//  /** Adds a number of elements provided by a traversable object
//   *  and returns a new collection with the added elements.
//   *
//   *  @param xs      the traversable object consisting of key-value pairs.
//   *  @return        a new immutable map with the bindings of this map and those from `xs`.
//   */
//  def ++[T](xs: GenTraversableOnce[(AttributeKey[_], Any)]): Attributes =
//    new Attributes((store /: xs.seq) (_ + _))
//
//  /* Overridden for efficiency. */
//  override def toSeq: Seq[(AttributeKey[_], Any)] = toBuffer[(AttributeKey[_], Any)]
//  override def toBuffer[C >: (AttributeKey[_], Any)]: mutable.Buffer[C] = {
//    val result = new mutable.ArrayBuffer[C](size)
//    copyToBuffer(result)
//    result
//  }
//
//  override def toString(): String = {
//    s"Attributes(${map(kv => kv._1.name -> kv._2.toString)})"
//  }
//}

//object AttributesView {
//  /** An empty $Coll */
//  //def empty = new Attributes(immutable.Map.empty)
//
//  /** A collection of type Attributes that contains given key/value bindings.
//  *  @param elems   the key/value pairs that make up the $coll
//  *  @return        a new Attributes consisting key/value pairs given by `elems`.
//  */
//  def apply[S <: Scope](elems: (AttributeKey[_], Any)*)(implicit scope: S): AttributesView = (newBuilder[S] ++= elems).result
//
//  def apply[S <: Scope](attributes: ScopedAttributes)(implicit inscope: S) = new AttributesView {
//
//    def underlying: ScopedAttributes = attributes
//
//    implicit def scope = inscope
//  }
//
//  /** The default builder for Attributes objects.
//  */
//  def newBuilder[S <: Scope](implicit scope: S): mutable.Builder[(AttributeKey[_], Any), AttributesView] = new AttributesViewBuilder(mutable.Map.empty)
//
//  class AttributesViewBuilder(empty: mutable.Map[AttributeKey[_], Any])(implicit scope: Scope) extends mutable.Builder[(AttributeKey[_], Any), AttributesView] {
//    private[this] val coll = empty
//
//    def +=(elem: (AttributeKey[_], Any)): this.type = {
//     coll += k2sk(elem._1) -> elem._2
//     this
//    }
//
//    def clear() { coll.clear() }
//
//    def result(): AttributesView = AttributesView(new ScopedAttributes(scope, TrieMap(coll.toSeq:_*)))
//  }
//
//  private def k2sk[T, S <: Scope](k: AttributeKey[T])(implicit scope: S) = (k in scope).key
//
//  implicit def canBuildFrom[S <: Scope](implicit scope: S): CanBuildFrom[TraversableOnce[(AttributeKey[_], Any)], (AttributeKey[_], Any), AttributesView] =
//   new CanBuildFrom[TraversableOnce[(AttributeKey[_], Any)], (AttributeKey[_], Any), AttributesView] {
//     def apply(from: TraversableOnce[(AttributeKey[_], Any)]): mutable.Builder[(AttributeKey[_], Any), AttributesView] = {
//       val f = from.toSeq
//       val m: Seq[(AttributeKey[_], Any)] = f.map(kv => k2sk(kv._1).asInstanceOf[AttributeKey[_]] -> kv._2)
//       new AttributesViewBuilder(mutable.Map.empty[AttributeKey[_], Any] ++= m)
//     }
//
//     def apply(): mutable.Builder[(AttributeKey[_], Any), AttributesView] = new AttributesViewBuilder(mutable.Map.empty)
//   }
//}
//
//trait AttributesView extends Iterable[(AttributeKey[_], Any)] with IterableLike[(AttributeKey[_], Any), AttributesView] {
//
//  import AttributesView.k2sk
//
//  private[attributes] def underlying: ScopedAttributes
//
//  implicit def scope: Scope
//
//  def iterator: Iterator[(AttributeKey[_], Any)] = underlying.toMap.iterator
//
//  override protected[this] def newBuilder: mutable.Builder[(AttributeKey[_], Any), AttributesView] = AttributesView.newBuilder
//  def contains[T](key: AttributeKey[T]) = underlying contains key
//
//  def getOrElse[T](key: AttributeKey[T], default: => T) = underlying.getOrElse(key, default)
//
//  def getOrElseUpdate[T](key: AttributeKey[T], default: => T) = underlying.getOrElseUpdate(key, default)
//
//  def get[T](key: AttributeKey[T]): Option[T] = underlying.get(key)
//
//  def apply[T](key: AttributeKey[T]): T = get(key) getOrElse (throw new KeyNotFoundException(key.name, scope))
//
//  def update[T](key: AttributeKey[T], value: T): T = {
//    println(s"Putting value: $value, ${key.hashCode()}")
//
//    underlying.put(key, value)
//    Thread.sleep(500)
//    println(s"Underlying now has: ${underlying.get(key)}, ${key.hashCode()}")
//    value
//  }
//
//  def put[T](key: AttributeKey[T], value: T) = update[T](key, value)
//
//  def updated[T](key: AttributeKey[T], value: T) = {
//    underlying.updated(key, value)
//    this
//  }
//
//  def remove[T](key: AttributeKey[T]) = { underlying.remove(key) }
//
//  def +=[T](kv: (AttributeKey[T], T)): this.type = {
//    underlying += k2sk(kv._1) -> kv._2
//    this
//  }
//
//  def +=[T](kv1: (AttributeKey[T], T), kv2: (AttributeKey[T], T), kvs: (AttributeKey[T], T)*): this.type = {
//    underlying += k2sk(kv1._1) -> kv1._2 += k2sk(kv2._1) -> kv2._2 ++= kvs.map(kk => k2sk(kk._1) -> kk._2)
//    this
//  }
//
//
//  def ++=[T](kv: TraversableOnce[(AttributeKey[T], T)]): this.type = {
//    underlying ++= kv.map(kk => k2sk(kk._1) -> kk._2)
//    this
//  }
//
//  def -=[T](key: AttributeKey[T]): this.type = { underlying -= key; this }
//  def -=[T](key1: AttributeKey[T], key2: AttributeKey[T], keys: AttributeKey[T]*): this.type = {
//    underlying -= key1 -= key2 --= keys.map(k2sk(_)); this }
//
//  def --=[T](key: TraversableOnce[AttributeKey[T]]): this.type = { underlying --= key.map(k2sk(_)); this }
//
//
//  def toMap: Map[AttributeKey[_], Any] = underlying.toMap
//
//  /** Collects all keys of this map in an iterable collection.
//   *
//   *  @return the keys of this map as an iterable.
//   */
//  def keys: Iterable[AttributeKey[_]] = underlying.keys
//
//  /** Collects all values of this map in an iterable collection.
//   *
//   *  @return the values of this map as an iterable.
//   */
//  def values: Iterable[Any] = underlying.values
//
//  /** Creates an iterator for all keys.
//   *
//   *  @return an iterator over all keys.
//   */
//  def keysIterator: Iterator[AttributeKey[_]] = underlying.keysIterator
//
//  /** Creates an iterator for all values in this map.
//   *
//   *  @return an iterator over all values that are associated with some key in this map.
//   */
//  def valuesIterator: Iterator[Any] = underlying.valuesIterator
//
//  /** Filters this map by retaining only keys satisfying a predicate.
//   *  @param  p   the predicate used to test keys
//   *  @return an immutable map consisting only of those key value pairs of this map where the key satisfies
//   *          the predicate `p`. The resulting map wraps the original map without copying any elements.
//   */
//  def filterKeys(p: AttributeKey[_] => Boolean): AttributesView = AttributesView(underlying.filterKeys(p))
//
//  /** Transforms this map by applying a function to every retrieved value.
//   *  @param  f   the function used to transform values of this map.
//   *  @return a map view which maps every key of this map
//   *          to `f(this(key))`. The resulting map wraps the original map without copying any elements.
//   */
//  def mapValues[C](f: Any => C): AttributesView = AttributesView(underlying.mapValues(f))
//
//}

object ScopedAttributes {
  def apply(s: Scope, m: mutable.Map[AttributeKey[_], Any]) = new ScopedAttributes {

    def underlying = m

    def scope: Scope = s
  }

  class ScopedAttributesBuilder(s: Scope) extends mutable.Builder[(AttributeKey[_], Any), ScopedAttributes] {

    val u = s.underlying.empty

    def +=(elem: (AttributeKey[_], Any)) = { u += elem; this }

    def clear() { u.clear() }

    def result(): ScopedAttributes = ScopedAttributes(s, u)
  }

//  implicit def canBuildFrom: CanBuildFrom[ScopedAttributes, (AttributeKey[_], Any), ScopedAttributes] =
//     new CanBuildFrom[ScopedAttributes, (AttributeKey[_], Any), ScopedAttributes] {
//       def apply(from: ScopedAttributes): mutable.Builder[(AttributeKey[_], Any), ScopedAttributes] = new ScopedAttributesBuilder(from)
//
//       def apply(): mutable.Builder[(AttributeKey[_], Any), ScopedAttributes] =
//     }
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
//
//class ServerContext {
//
//  private[this] val serverState = new ScopedAttributes(ThisServer)
//  private[this] val applicationState = new ConcurrentHashMap[UUID, ScopedAttributes[AppScope]]().asScala
//  private[this] val requestState = new ConcurrentHashMap[UUID, ScopedAttributes[RequestScope]]().asScala
//
//  def update[T, S <: Scope](key: ScopedKey[T, S], value: T): T = forScope(key.scope).update(key.key, value)
//  def updated[T, S <: Scope](key: ScopedKey[T, S], value: T) = {
//    forScope(key.scope).updated(key.key, value)
//    this
//  }
//  def apply[T, S <: Scope](key: ScopedKey[T, S]): T = get(key) getOrElse (throw new KeyNotFoundException(key.key.name, key.scope))
//  def get[T, S <: Scope](key: ScopedKey[T, S]): Option[T] = forScope(key.scope).get(key.key)
//
//  def -=[T, S <: Scope](elem: ScopedKey[T, S]): ServerContext = {
//    forScope(elem.scope) -= elem.key
//    this
//  }
//
//  def +=[T, S <:Scope](elem: (ScopedKey[T, S], T)): ServerContext = {
//    forScope(elem._1.scope)(elem._1.key) = elem._2
//    this
//  }
//
//  def clear[S <: Scope](scope: S) {
//    scope match {
//      case ThisServer => serverState.clear()
//      case AppScope(uuid) => if (applicationState contains uuid) applicationState -= uuid
//      case r: RequestScope => if (requestState contains r.uuid) requestState -= r.uuid
//    }
//  }
//
//  def forScope[S <: Scope](scope: S): ScopedAttributes[S] = scope match {
//    case ThisServer => serverState.asInstanceOf[ScopedAttributes[S]]
//
//    case s @ AppScope(uuid) =>
//      applicationState.get(uuid).getOrElse {
//        val a = new ScopedAttributes[AppScope](s)
//        applicationState(uuid) = a
//        a
//      }.asInstanceOf[ScopedAttributes[S]]
//
//    case r: RequestScope =>
//      requestState.get(r.uuid).getOrElse {
//        val a = new ScopedAttributes[RequestScope](r)
//        requestState(r.uuid) = a
//        a
//      }.asInstanceOf[ScopedAttributes[S]]
//  }
//}


