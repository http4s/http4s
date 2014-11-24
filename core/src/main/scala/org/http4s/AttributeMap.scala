/*
 * Derived from https://github.com/sbt/sbt/blob/0.13/util/collection/src/main/scala/sbt/Attributes.scala
 *
 * sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package org.http4s

// T must be invariant to work properly.
//  Because it is sealed and the only instances go through AttributeKey.apply,
//  a single AttributeKey instance cannot conform to AttributeKey[T] for different Ts

/** A key in an [[AttributeMap]] that constrains its associated value to be of type `T`.
  * The key is uniquely defined by its [[name]] and type `T`, represented at runtime by [[manifest]]. */
final class AttributeKey[T] private (val name: String) {
  def apply(value: T): AttributeEntry[T] = AttributeEntry(this, value)

  override final def toString = name
}

object AttributeKey {
  def apply[T](name: String): AttributeKey[T] = new AttributeKey(name)

  /**
   * Encourage greater consistency in internal keys by imposing a universal prefix.
   */
  private[http4s] def http4s[T](name: String): AttributeKey[T] =
    apply("org.http4s."+name)
}

/** An immutable map where a key is the tuple `(String,T)` for a fixed type `T` and can only be associated with values of type `T`.
  * It is therefore possible for this map to contain mappings for keys with the same label but different types.
  * Excluding this possibility is the responsibility of the client if desired. */
class AttributeMap private(private val backing: Map[AttributeKey[_], Any]) {

  /** Gets the value of type `T` associated with the key `k`.
    * If a key with the same label but different type is defined, this method will fail. */
  def apply[T](k: AttributeKey[T]): T = backing(k).asInstanceOf[T]

  /** Gets the value of type `T` associated with the key `k` or `None` if no value is associated.
    * If a key with the same label but a different type is defined, this method will return `None`. */
  def get[T](k: AttributeKey[T]): Option[T] = backing.get(k).asInstanceOf[Option[T]]

  /** Returns this map without the mapping for `k`.
    * This method will not remove a mapping for a key with the same label but a different type. */
  def remove[T](k: AttributeKey[T]): AttributeMap = new AttributeMap(backing - k)

  /** Returns true if this map contains a mapping for `k`.
    * If a key with the same label but a different type is defined in this map, this method will return `false`. */
  def contains[T](k: AttributeKey[T]): Boolean = backing.contains(k)

  /** Adds the mapping `k -> value` to this map, replacing any existing mapping for `k`.
    * Any mappings for keys with the same label but different types are unaffected. */
  def put[T](k: AttributeKey[T], value: T): AttributeMap = new AttributeMap(backing.updated(k, value))

  /** All keys with defined mappings.  There may be multiple keys with the same `label`, but different types. */
  def keys: Iterable[AttributeKey[_]] = backing.keys

  /** Adds the mappings in `o` to this map, with mappings in `o` taking precedence over existing mappings.*/
  def ++(o: Iterable[AttributeEntry[_]]): AttributeMap = {
    val newBacking = o.foldLeft(backing) { case (b, AttributeEntry(key, value)) => b.updated(key, value) }
    new AttributeMap(newBacking)
  }

  /** Combines the mappings in `o` with the mappings in this map, with mappings in `o` taking precedence over existing mappings.*/
  def ++(o: AttributeMap): AttributeMap = new AttributeMap(backing ++ o.backing)

  /** All mappings in this map.  The [[AttributeEntry]] type preserves the typesafety of mappings, although the specific types are unknown.*/
  def entries: Iterable[AttributeEntry[_]] =
    for( (k: AttributeKey[kt], v) <- backing) yield AttributeEntry(k, v.asInstanceOf[kt])

  /** `true` if there are no mappings in this map, `false` if there are. */
  def isEmpty: Boolean = backing.isEmpty
}

object AttributeMap
{
  /** An [[AttributeMap]] without any mappings. */
  val empty: AttributeMap = new AttributeMap(Map.empty)

  /** Constructs an [[AttributeMap]] containing the given `entries`. */
  def apply(entries: Iterable[AttributeEntry[_]]): AttributeMap = empty ++ entries

  /** Constructs an [[AttributeMap]] containing the given `entries`.*/
  def apply(entries: AttributeEntry[_]*): AttributeMap = empty ++ entries
}

// type inference required less generality
/** A map entry where `key` is constrained to only be associated with a fixed value of type `T`. */
final case class AttributeEntry[T](key: AttributeKey[T], value: T) {
  override def toString = key.name + ": " + value
}

