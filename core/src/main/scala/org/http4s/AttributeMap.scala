/*
 * Derived from https://github.com/sbt/sbt/blob/0.13/util/collection/src/main/scala/sbt/Attributes.scala
 *
 * sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package org.http4s

// T must be invariant to work properly.

/** A key in an [[AttributeMap]] that constrains its associated value to be of type `T`.
  * The key is uniquely defined by its reference: there are no duplicate keys, even
  * those with the same name and type. */
final class AttributeKey[T] private {
  def apply(value: T): AttributeEntry[T] = AttributeEntry(this, value)
}

object AttributeKey {

  /** Construct a new [[AttributeKey]] */
  def apply[T]: AttributeKey[T] = new AttributeKey()

  @deprecated("Removed because `name` suggests equality between keys with the same name", "0.17")
  def apply[T](name: String): AttributeKey[T] = apply
}

/** An immutable map where an [[AttributeKey]]  for a fixed type `T` can only be associated with values of type `T`.
  * Because the equality of keys is based on reference, it is therefore possible for this map to contain mappings
  * for keys with the same label and same types. */
final class AttributeMap private(private val backing: Map[AttributeKey[_], Any]) {

  /** Gets the value of type `T` associated with the key `k`. */
  def apply[T](k: AttributeKey[T]): T = backing(k).asInstanceOf[T]

  /** Gets the value of type `T` associated with the key `k` or `None` if no value is associated. */
  def get[T](k: AttributeKey[T]): Option[T] = backing.get(k).asInstanceOf[Option[T]]

  /** Returns this map without the mapping for `k`. */
  def remove[T](k: AttributeKey[T]): AttributeMap = new AttributeMap(backing - k)

  /** Returns true if this map contains a mapping for `k`. */
  def contains[T](k: AttributeKey[T]): Boolean = backing.contains(k)

  /** Adds the mapping `k -> value` to this map, replacing any existing mapping for `k`. */
  def put[T](k: AttributeKey[T], value: T): AttributeMap = new AttributeMap(backing.updated(k, value))

  /** All keys with defined mappings. */
  def keys: Iterable[AttributeKey[_]] = backing.keys

  /** Adds the mappings in `o` to this map, with mappings in `o` taking precedence over existing mappings.*/
  def ++(o: Iterable[AttributeEntry[_]]): AttributeMap =
    new AttributeMap(backing ++ o.iterator.map { e => (e.key, e.value) })

  /** Combines the mappings in `o` with the mappings in this map, with mappings in `o` taking precedence over existing mappings.*/
  def ++(o: AttributeMap): AttributeMap = new AttributeMap(backing ++ o.backing)

  /** Removes an attribute key from the map*/
  def --(k: AttributeKey[_]): AttributeMap = new AttributeMap(backing - k)

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
  override def toString: String = s"$key: $value"
}

