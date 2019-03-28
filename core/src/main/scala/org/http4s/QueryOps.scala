package org.http4s

import cats.implicits._
trait QueryOps {

  protected type Self <: QueryOps

  protected val query: Query

  protected def self: Self

  protected def replaceQuery(query: Query): Self

  /** alias for containsQueryParam */
  def ?[K: QueryParamKeyLike](name: K): Boolean =
    _containsQueryParam(QueryParamKeyLike[K].getKey(name))

  /** alias for setQueryParams */
  def =?[T: QueryParamEncoder](q: Map[String, List[T]]): Self =
    setQueryParams(q)

  /** alias for withQueryParam */
  def +?[T: QueryParam]: Self =
    _withQueryParam(QueryParam[T].key, Nil)

  /** alias for withQueryParam */
  def +*?[T: QueryParam: QueryParamEncoder](value: T): Self =
    _withQueryParam(QueryParam[T].key, QueryParamEncoder[T].encode(value) :: Nil)

  /** alias for withQueryParam */
  def +*?[T: QueryParam: QueryParamEncoder](values: List[T]): Self =
    _withQueryParam(QueryParam[T].key, values.map(QueryParamEncoder[T].encode))

  /** alias for withQueryParam */
  def +?[K: QueryParamKeyLike, T: QueryParamEncoder](name: K, value: T): Self =
    +?(name, value :: Nil)

  /** alias for withQueryParam */
  def +?[K: QueryParamKeyLike](name: K): Self =
    _withQueryParam(QueryParamKeyLike[K].getKey(name), Nil)

  /** alias for withQueryParam */
  def +?[K: QueryParamKeyLike, T: QueryParamEncoder](name: K, values: List[T]): Self =
    _withQueryParam(QueryParamKeyLike[K].getKey(name), values.map(QueryParamEncoder[T].encode))

  /*
  /** alias for withMaybeQueryParam */
  def +??[K: QueryParamKeyLike, T: QueryParamEncoder](name: K, value: Maybe[T]): Self =
    _withMaybeQueryParam(QueryParamKeyLike[K].getKey(name), value map QueryParamEncoder[T].encode)

  /** alias for withMaybeQueryParam */
  def +??[T: QueryParam : QueryParamEncoder](value: Maybe[T]): Self =
    _withMaybeQueryParam(QueryParam[T].key, value map QueryParamEncoder[T].encode)
   */

  /** alias for withOptionQueryParam */
  def +??[K: QueryParamKeyLike, T: QueryParamEncoder](name: K, value: Option[T]): Self =
    _withOptionQueryParam(QueryParamKeyLike[K].getKey(name), value.map(QueryParamEncoder[T].encode))

  /** alias for withOptionQueryParam */
  def +??[T: QueryParam: QueryParamEncoder](value: Option[T]): Self =
    _withOptionQueryParam(QueryParam[T].key, value.map(QueryParamEncoder[T].encode))

  /** alias for removeQueryParam */
  def -?[T](implicit key: QueryParam[T]): Self =
    _removeQueryParam(key.key)

  /** alias for removeQueryParam */
  def -?[K: QueryParamKeyLike](key: K): Self =
    _removeQueryParam(QueryParamKeyLike[K].getKey(key))

  /**
    * Checks if a specified parameter exists in the [[Query]]. A parameter
    * without a name can be checked with an empty string.
    */
  def containsQueryParam[T](implicit key: QueryParam[T]): Boolean =
    _containsQueryParam(key.key)

  def containsQueryParam[K: QueryParamKeyLike](key: K): Boolean =
    _containsQueryParam(QueryParamKeyLike[K].getKey(key))

  private def _containsQueryParam(name: QueryParameterKey): Boolean =
    if (query.isEmpty) false
    else query.exists { case (k, _) => k == name.value }

  /**
    * Creates maybe a new `Self` without the specified parameter in query.
    * If no parameter with the given `key` exists then `this` will be
    * returned.
    */
  def removeQueryParam[K: QueryParamKeyLike](key: K): Self =
    _removeQueryParam(QueryParamKeyLike[K].getKey(key))

  private def _removeQueryParam(name: QueryParameterKey): Self =
    if (query.isEmpty) self
    else {
      val newQuery = query.filterNot { case (n, _) => n == name.value }
      replaceQuery(newQuery)
    }

  /**
    * Creates maybe a new `Self` with the specified parameters. The entire
    * [[Query]] will be replaced with the given one.
    */
  def setQueryParams[K: QueryParamKeyLike, T: QueryParamEncoder](params: Map[K, collection.Seq[T]]): Self = {
    val penc = QueryParamKeyLike[K]
    val venc = QueryParamEncoder[T]
    val vec = params.foldLeft(query.toVector) {
      case (m, (k, Seq())) => m :+ (penc.getKey(k).value -> None)
      case (m, (k, vs)) =>
        vs.foldLeft(m) { case (m, v) => m :+ (penc.getKey(k).value -> venc.encode(v).value.some) }
    }
    replaceQuery(Query.fromVector(vec))
  }

  /**
    * Creates a new `Self` with the specified parameter in the [[Query]].
    * If a parameter with the given `QueryParam.key` already exists the values will be
    * replaced with an empty list.
    */
  def withQueryParam[T: QueryParam]: Self =
    _withQueryParam(QueryParam[T].key, Nil)

  /**
    * Creates a new `Self` with the specified parameter in the [[Query]].
    * If a parameter with the given `key` already exists the values will be
    * replaced with an empty list.
    */
  def withQueryParam[K: QueryParamKeyLike](key: K): Self =
    _withQueryParam(QueryParamKeyLike[K].getKey(key), Nil)

  /**
    * Creates maybe a new `Self` with the specified parameter in the [[Query]].
    * If a parameter with the given `key` already exists the values will be
    * replaced. If the parameter to be added equal the existing entry the same
    * instance of `Self` will be returned.
    */
  def withQueryParam[T: QueryParamEncoder, K: QueryParamKeyLike](key: K, value: T): Self =
    _withQueryParam(QueryParamKeyLike[K].getKey(key), QueryParamEncoder[T].encode(value) :: Nil)

  /**
    * Creates maybe a new `Self` with the specified parameters in the [[Query]].
    * If a parameter with the given `key` already exists the values will be
    * replaced.
    */
  def withQueryParam[T: QueryParamEncoder, K: QueryParamKeyLike](key: K, values: List[T]): Self =
    _withQueryParam(QueryParamKeyLike[K].getKey(key), values.map(QueryParamEncoder[T].encode))

  private def _withQueryParam(name: QueryParameterKey, values: List[QueryParameterValue]): Self = {
    val baseQuery = query.toVector.filter(_._1 != name.value)
    val vec =
      if (values.isEmpty) baseQuery :+ (name.value -> None)
      else {
        values.toList.foldLeft(baseQuery) {
          case (vec, v) => vec :+ (name.value -> v.value.some)
        }
      }

    replaceQuery(Query.fromVector(vec))
  }

  /*
  /**
   * Creates maybe a new `Self` with the specified parameter in the [[Query]].
   * If the value is empty the same instance of `Self` will be returned.
   * If a parameter with the given `key` already exists the values will be
   * replaced.
   */
  def withMaybeQueryParam[T: QueryParamEncoder, K: QueryParamKeyLike](key: K, value: Maybe[T]): Self =
    _withMaybeQueryParam(QueryParamKeyLike[K].getKey(key), value map QueryParamEncoder[T].encode)

  /**
   * Creates maybe a new `Self` with the specified parameter in the [[Query]].
   * If the value is empty or if the parameter to be added equal the existing
   * entry the same instance of `Self` will be returned.
   * If a parameter with the given `name` already exists the values will be
   * replaced.
   */
  def withMaybeQueryParam[T: QueryParam: QueryParamEncoder](value: Maybe[T]): Self =
    _withMaybeQueryParam(QueryParam[T].key, value map QueryParamEncoder[T].encode)
   */

  /**
    * Creates maybe a new `Self` with the specified parameter in the [[Query]].
    * If the value is empty or if the parameter to be added equal the existing
    * entry the same instance of `Self` will be returned.
    * If a parameter with the given `key` already exists the values will be
    * replaced.
    */
  def withOptionQueryParam[T: QueryParamEncoder, K: QueryParamKeyLike](
      key: K,
      value: Option[T]): Self =
    _withOptionQueryParam(QueryParamKeyLike[K].getKey(key), value.map(QueryParamEncoder[T].encode))

  /**
    * Creates maybe a new `Self` with the specified parameter in the [[Query]].
    * If the value is empty or if the parameter to be added equal the existing
    * entry the same instance of `Self` will be returned.
    * If a parameter with the given `name` already exists the values will be
    * replaced.
    */
  def withOptionQueryParam[T: QueryParam: QueryParamEncoder](value: Option[T]): Self =
    _withOptionQueryParam(QueryParam[T].key, value.map(QueryParamEncoder[T].encode))

  private def _withOptionQueryParam(
      name: QueryParameterKey,
      value: Option[QueryParameterValue]): Self =
    value.fold(self)(v => _withQueryParam(name, v :: Nil))
}
