package org.http4s
package attributes

import reflect.ClassTag
import scala.concurrent.stm._
import shapeless.TypeOperators._
import collection._
import generic.{Shrinkable, Growable, CanBuildFrom}
import collection.immutable.Map
import parallel.immutable.ParMap
import JavaConverters._
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID


trait AttributeKey[@specialized T] {
  def classTag: ClassTag[T]
  def name: String
  def description: Option[String]
}

abstract class Key[@specialized T](val description: Option[String])(implicit val classTag: ClassTag[T]) extends AttributeKey[T] {
  def this()(implicit classTag: ClassTag[T]) = this(None)(classTag)

  val name: String = getClass.getSimpleName.replaceAll("\\$$", "")
}

case class ScopedKey[T, S <: Scope](key: AttributeKey[T] @@ S, scope: S)

object Attributes {
  /** An empty $Coll */
  def empty = new Attributes(immutable.Map.empty)

  /** A collection of type Attributes that contains given key/value bindings.
   *  @param elems   the key/value pairs that make up the $coll
   *  @return        a new Attributes consisting key/value pairs given by `elems`.
   */
  def apply(elems: (AttributeKey[_], Any)*): Attributes = (newBuilder ++= elems).result

  /** The default builder for Attributes objects.
   */
  def newBuilder: mutable.Builder[(AttributeKey[_], Any), Attributes] = new AttributesBuilder(mutable.Map.empty)

  class AttributesBuilder(empty: mutable.Map[AttributeKey[_], Any]) extends mutable.Builder[(AttributeKey[_], Any), Attributes] {
    private[this] val coll = empty
    def +=(elem: (AttributeKey[_], Any)): this.type = {
      coll += elem
      this
    }

    def clear() { coll.clear() }

    def result(): Attributes = new Attributes(coll.toMap)
  }

  type AttributePair[T] = (AttributeKey[T], T)

  implicit def canBuildFrom: CanBuildFrom[TraversableOnce[(AttributeKey[_], Any)], (AttributeKey[_], Any), Attributes] =
    new CanBuildFrom[TraversableOnce[(AttributeKey[_], Any)], (AttributeKey[_], Any), Attributes] {
      def apply(from: TraversableOnce[(AttributeKey[_], Any)]): mutable.Builder[(AttributeKey[_], Any), Attributes] =
        new AttributesBuilder(mutable.Map(from.toSeq:_*))

      def apply(): mutable.Builder[(AttributeKey[_], Any), Attributes] = new AttributesBuilder(mutable.Map.empty)
    }
}
class Attributes(store: Map[AttributeKey[_], Any] = Map.empty)
  extends Iterable[(AttributeKey[_], Any)] with IterableLike[(AttributeKey[_], Any), Attributes] {

  protected[this] override def parCombiner = ParMap.newCombiner[AttributeKey[_], Any]

  def iterator: Iterator[(AttributeKey[_], Any)] = store.iterator

  def toMap: Map[AttributeKey[_], Any] = store

  override protected[this] def newBuilder: mutable.Builder[(AttributeKey[_], Any), Attributes] = Attributes.newBuilder
  def empty: Attributes = Attributes.empty
  def get[T](key: AttributeKey[T]): Option[T] = (store get key) map (_.asInstanceOf[T])
  def apply[T](key: AttributeKey[T]): T = get(key) getOrElse default(key)
  def contains[T](key: AttributeKey[T]): Boolean = store contains key
  def getOrElse[T](key: AttributeKey[T], default: => T): T = get(key) getOrElse default
  override def seq: Attributes = this
  def +[T](kv: Attributes.AttributePair[T]): Attributes = new Attributes(store + kv)
  def -[T](key: AttributeKey[T]): Attributes = new Attributes(store - key)
  def default[T](key: AttributeKey[T]): T = throw new NoSuchElementException("Can't find " + key)

  /** A new immutable map containing updating this map with a given key/value mapping.
   *  @param    key the key
   *  @param    value the value
   *  @return   A new map with the new key/value mapping
   */
  def updated[T](key: AttributeKey[T], value: T): Attributes = this + ((key, value))

  def keySet: Set[AttributeKey[_]] = store.keySet

  /** Collects all keys of this map in an iterable collection.
   *
   *  @return the keys of this map as an iterable.
   */
  def keys: Iterable[AttributeKey[_]] = store.keys

  /** Collects all values of this map in an iterable collection.
   *
   *  @return the values of this map as an iterable.
   */
  def values: Iterable[Any] = store.values

  /** Creates an iterator for all keys.
   *
   *  @return an iterator over all keys.
   */
  def keysIterator: Iterator[AttributeKey[_]] = store.keysIterator

  /** Creates an iterator for all values in this map.
   *
   *  @return an iterator over all values that are associated with some key in this map.
   */
  def valuesIterator: Iterator[Any] = store.valuesIterator

  /** Filters this map by retaining only keys satisfying a predicate.
   *  @param  p   the predicate used to test keys
   *  @return an immutable map consisting only of those key value pairs of this map where the key satisfies
   *          the predicate `p`. The resulting map wraps the original map without copying any elements.
   */
  def filterKeys(p: AttributeKey[_] => Boolean): Attributes = new Attributes(store.filterKeys(p))

  /** Transforms this map by applying a function to every retrieved value.
   *  @param  f   the function used to transform values of this map.
   *  @return a map view which maps every key of this map
   *          to `f(this(key))`. The resulting map wraps the original map without copying any elements.
   */
  def mapValues[C](f: Any => C): Attributes = new Attributes(store.mapValues(f))

  /** Adds a number of elements provided by a traversable object
   *  and returns a new collection with the added elements.
   *
   *  @param xs      the traversable object consisting of key-value pairs.
   *  @return        a new immutable map with the bindings of this map and those from `xs`.
   */
  def ++[T](xs: GenTraversableOnce[(AttributeKey[_], Any)]): Attributes =
    new Attributes((store /: xs.seq) (_ + _))

  /* Overridden for efficiency. */
  override def toSeq: Seq[(AttributeKey[_], Any)] = toBuffer[(AttributeKey[_], Any)]
  override def toBuffer[C >: (AttributeKey[_], Any)]: mutable.Buffer[C] = {
    val result = new mutable.ArrayBuffer[C](size)
    copyToBuffer(result)
    result
  }

  override def toString(): String = {
    s"Attributes(${map(kv => kv._1.name -> kv._2.toString)})"
  }
}

class AttributesView[S <: Scope](scope: S, underlying: ScopedAttributes[S]) {
  
  private[this] implicit def k2sk[T](k: AttributeKey[T]) = (k in scope).key
  def contains[T](key: AttributeKey[T]) = underlying contains key  
    
  def getOrElse[T](key: AttributeKey[T], default: => T) = underlying.getOrElse(key, default)
  
  def getOrElseUpdate[T](key: AttributeKey[T], default: => T) = underlying.getOrElseUpdate(key, default)

  def get[T](key: AttributeKey[T]): Option[T] = underlying.get(key)

  def apply[T](key: AttributeKey[T]): T = get(key) getOrElse (throw new KeyNotFoundException(key.name, scope))

  def update[T](key:AttributeKey[T], value: T): T = {
    underlying.update(key, value)
    value
  }

  def put[T](key: AttributeKey[T], value: T) = update[T](key, value)

  def updated[T](key: AttributeKey[T], value: T) = {
    underlying.updated(key, value)
    this
  }

  def remove[T](key: AttributeKey[T]) = { underlying.remove(key) }

  def size = underlying.size

  def isEmpty = underlying.isEmpty

  def +=[T](kv: (AttributeKey[T], T)): this.type = {
    underlying += k2sk(kv._1) -> kv._2
    this
  }

  def +=[T](kv1: (AttributeKey[T], T), kv2: (AttributeKey[T]@@S, T), kvs: (AttributeKey[T]@@S, T)*): this.type = {
    underlying += k2sk(kv1._1) -> kv1._2 += k2sk(kv2._1) -> kv2._2 ++= kvs.map(kk => k2sk(kk._1) -> kk._2)
    this
  }


  def ++=[T](kv: TraversableOnce[(AttributeKey[T], T)]): this.type = {
    underlying ++= kv.map(kk => k2sk(kk._1) -> kk._2)
    this
  }

  def -=[T](key: AttributeKey[T]): this.type = { underlying -= key; this }
  def -=[T](key1: AttributeKey[T], key2: AttributeKey[T], keys: AttributeKey[T]*): this.type = {
    underlying -= key1 -= key2 --= keys.map(k2sk); this }

  def --=[T](key: TraversableOnce[AttributeKey[T]]): this.type = { underlying --= key.map(k2sk); this }

  def foreach[U](iterator: ((AttributeKey[_], Any)) => U) { underlying.foreach(iterator) }

  def map[B](endo: ((AttributeKey[_], Any)) => B) = underlying.map(endo)
  def flatMap[B](endo: ((AttributeKey[_], Any)) => GenTraversable[B]) = underlying.flatMap(endo)

  def collect[B](collector: PartialFunction[(AttributeKey[_], Any), B]) = underlying.collect(collector)
  def collectFirst[B](collector: PartialFunction[(AttributeKey[_], Any), B]): Option[B] =
    underlying.collectFirst(collector)

  def clear() { underlying.clear() }

  def toMap: Map[AttributeKey[_], Any] = underlying.asInstanceOf[Map[AttributeKey[_], Any]]
}

class ScopedAttributes[S <: Scope](val scope: S, underlying: TMap[AttributeKey[_] @@ S, Any] = TMap.empty[AttributeKey[_] @@ S, Any]) {

  def contains[T](key: AttributeKey[T] @@ S) = atomic { implicit txn => underlying.contains(key) }
  
  def getOrElse[T](key: AttributeKey[T] @@ S, default: => T) = atomic { implicit txn =>
    underlying.getOrElse(key, default)
  }
  
  def getOrElseUpdate[T](key: AttributeKey[T] @@ S, default: => T) = atomic { implicit txn =>
    underlying.getOrElseUpdate(key, default)
  }

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

  def clear() { atomic { implicit txn => underlying.clear() } }

  def toMap: Map[AttributeKey[_], Any] = underlying.snapshot.asInstanceOf[Map[AttributeKey[_], Any]]

}

class ServerContext {

  private[this] val serverState = new ScopedAttributes(ThisServer)
  private[this] val applicationState = new ConcurrentHashMap[UUID, ScopedAttributes[AppScope]]().asScala
  private[this] val requestState = new ConcurrentHashMap[UUID, ScopedAttributes[RequestScope]]().asScala

  def update[T, S <: Scope](key: ScopedKey[T, S], value: T): T = {
    forScope(key.scope).update(key.key, value)
//    key match {
//      case ScopedKey(k, ThisServer) => serverState.update(k.asInstanceOf[AttributeKey[T] @@ ThisServer.type], value)
//      case ScopedKey(k, s @ AppScope(uuid)) =>
//        if (!applicationState.contains(uuid))
//          applicationState(uuid) = new ScopedAttributes[AppScope](s)
//        applicationState(uuid).update(k.asInstanceOf[AttributeKey[T] @@ AppScope], value)
//      case ScopedKey(k, s @ RequestScope(uuid)) =>
//        if (!requestState.contains(uuid))
//          requestState(uuid) = new ScopedAttributes[RequestScope](s)
//        requestState(uuid).update(k.asInstanceOf[AttributeKey[T] @@ RequestScope], value)
//    }
  }
  def updated[T, S <: Scope](key: ScopedKey[T, S], value: T) = {
    forScope(key.scope).updated(key.key, value)
//    key match {
//      case ScopedKey(k, ThisServer) => serverState.updated(k.asInstanceOf[AttributeKey[T] @@ ThisServer.type], value)
//      case ScopedKey(k, s @ AppScope(uuid)) =>
//        if (!applicationState.contains(uuid))
//          applicationState(uuid) = new ScopedAttributes[AppScope](s)
//        applicationState(uuid).updated(k.asInstanceOf[AttributeKey[T] @@ AppScope], value)
//      case ScopedKey(k, s @ RequestScope(uuid)) =>
//        if (!requestState.contains(uuid))
//          requestState(uuid) = new ScopedAttributes[RequestScope](s)
//        requestState(uuid).updated(k.asInstanceOf[AttributeKey[T] @@ RequestScope], value)
//    }
    this
  }
  def apply[T, S <: Scope](key: ScopedKey[T, S]): T = get(key) getOrElse (throw new KeyNotFoundException(key.key.name, key.scope))
  def get[T, S <: Scope](key: ScopedKey[T, S]): Option[T] = {
    forScope(key.scope).get(key.key)
//    key match {
//      case ScopedKey(k, ThisServer) => serverState.get(k.asInstanceOf[AttributeKey[T] @@ ThisServer.type])
//      case ScopedKey(k, AppScope(uuid)) => applicationState.get(uuid).flatMap(_ get k.asInstanceOf[AttributeKey[T] @@ AppScope])
//      case ScopedKey(k, RequestScope(uuid)) => requestState.get(uuid).flatMap(_ get k.asInstanceOf[AttributeKey[T] @@ RequestScope])
//    }
  }

  def -=[T, S <: Scope](elem: ScopedKey[T, S]): ServerContext = {
    forScope(elem.scope) -= elem.key
//    elem match {
//      case ScopedKey(k, ThisServer) =>
//        serverState -= k.asInstanceOf[AttributeKey[T] @@ ThisServer.type]
//      case ScopedKey(k, AppScope(uuid)) =>
//        if (applicationState contains uuid) applicationState(uuid) -= k.asInstanceOf[AttributeKey[T] @@ AppScope]
//      case ScopedKey(k, RequestScope(uuid)) =>
//        if (requestState contains uuid) requestState(uuid) -= k.asInstanceOf[AttributeKey[T] @@ RequestScope]
//    }
    this
  }

  def +=[T, S <:Scope](elem: (ScopedKey[T, S], T)): ServerContext = {
    forScope(elem._1.scope)(elem._1.key) = elem._2
//    elem match {
//      case (ScopedKey(k, ThisServer), v) =>
//        serverState(k.asInstanceOf[AttributeKey[T] @@ ThisServer.type]) = v
//      case (ScopedKey(k, s @ AppScope(uuid)), v) =>
//        if (!applicationState.contains(uuid)) applicationState(uuid) = new ScopedAttributes[AppScope](s)
//        applicationState(uuid)(k.asInstanceOf[AttributeKey[T] @@ AppScope]) = v
//      case (ScopedKey(k, s @ RequestScope(uuid)), v) =>
//        if (!requestState.contains(uuid)) requestState(uuid) = new ScopedAttributes[RequestScope](s)
//        requestState(uuid)(k.asInstanceOf[AttributeKey[T] @@ RequestScope]) = v
//    }
    this
  }

  def clear[S <: Scope](scope: S) {
    scope match {
      case ThisServer => serverState.clear()
      case AppScope(uuid) => if (applicationState contains uuid) applicationState -= uuid
      case RequestScope(uuid) => if (requestState contains uuid) requestState -= uuid
    }
  }

  def forScope[S <: Scope](scope: S): ScopedAttributes[S] = scope match {
    case ThisServer => serverState.asInstanceOf[ScopedAttributes[S]]
    case s @ AppScope(uuid) =>
      if (!applicationState.contains(uuid)) applicationState(uuid) = new ScopedAttributes[AppScope](s)
      applicationState(uuid).asInstanceOf[ScopedAttributes[S]]
    case s @ RequestScope(uuid) =>
      if (!requestState.contains(uuid))
        requestState(uuid) = new ScopedAttributes[RequestScope](s)
      requestState(uuid).asInstanceOf[ScopedAttributes[S]]
  }
}


