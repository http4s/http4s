package org.http4s.util.encoding

import java.nio.charset.Charset
import java.nio.{ByteBuffer, CharBuffer}

import org.http4s.Uri
import org.http4s.parser.RequestUriParser
import org.http4s.util.encoding.tags._
import org.parboiled2.CharPredicate

import scala.annotation.tailrec
import scala.collection.SeqLike
import scala.util.Try


/** Goodies for URI encoding */
object UriCodingUtils {
  // https://www.w3.org/TR/html5/forms.html#application/x-www-form-urlencoded-encoding-algorithm
  def FormQueryParam = CharPredicate.Alpha ++ CharPredicate.Digit ++ "*-._"

  private val Utf8 = Charset.forName("UTF-8")
  private val HexUpperCaseChars = (0 until 16) map { i ⇒ Character.toUpperCase(Character.forDigit(i, 16)) }

  def verifyAuthority(authority: String): Try[Uri.Authority] = new RequestUriParser(authority).Authority.run()
  def verifyHost(          host: String): Try[Uri.Host]      = new RequestUriParser(host).Host.run()
  def verifyUserInfo(  userInfo: String): Try[Uri.UserInfo]  = new RequestUriParser(userInfo).UserInfo.run()
  def verifyRegName(    regName: String): Try[Unit]          = new RequestUriParser(regName).RegName.run()
  def verifyPath(          path: String): Try[Uri.Path]      = new RequestUriParser(path).Path.run()
  def verifyQuery(        query: String): Try[String]        = new RequestUriParser(query).Query.run()
  def verifyFragment(  fragment: String): Try[String]        = new RequestUriParser(fragment).Fragment.run()

  def encodeUserInfo(userInfo: String): EncodedString[UserInfo] = percentEncode(userInfo)
  def encodeRegName(regName: String): EncodedString[RegName] = percentEncode(regName)
  def encodeFragment(fragment: String): EncodedString[Fragment] = percentEncode(fragment)

  def encodePath(pathSegments: Seq[String]): EncodedString[Path] =
    pathSegments.toList match {
      case Nil => EncodedString("")
      case first :: rest => EncodedString {
        (encodePathSegmentNzNc(first) :: rest.map(encodePathSegment)).map(_.encoded).mkString("/")
      }
    }
  def encodePathSegment(s: String): EncodedString[PathSegment] = percentEncode(s)
  private def encodePathSegmentNzNc(pathSegmentNzNc: String): EncodedString[PathSegmentNzNc] = percentEncode(pathSegmentNzNc)

  private def encodeQueryValue(encodedKey: EncodedFormQueryKey)(value: String): EncodedString[FormQueryKV] = {
    def encodedValue = encodeQueryParam(value)
    EncodedString(s"$encodedKey=$encodedValue")
  }

  def encodeQueryMap(query: Map[String, Seq[String]]): EncodedString[FormQuery] =
    EncodedString {
      query.map {
        case (k, vs) =>
          def kEncoded = encodeQueryParam(k).asKey
          if (vs.isEmpty) kEncoded
          else vs.map(encodeQueryValue(kEncoded)).mkString("&")
      }.mkString("&")
    }

  def decodeQueryMap(e: EncodedString[FormQuery]): Map[String, Seq[String]] = {
    def vecToMap(v: Vector[(String, Option[String])]): Map[String, Vector[String]] =
      v.foldLeft[Map[String, Vector[String]]](Map()) {
        case (m, (key, None))         => m.updated(key, m.getOrElse(key, Vector()))
        case (m, (key, Some(value)))  => m.updated(key, m.getOrElse(key, Vector()) :+ value)
      }

    vecToMap(w3cHtml5FormUrlDecode(e.encoded))
  }

  def encodePlainQueryString(query: String): PctEncoded[UriQuery] =
    percentEncode[UriQuery](query)

  def decodePlainQueryString(encoded: PctEncoded[UriQuery]): String = percentDecode(encoded)

  def encodeQueryVector(query: Vector[(String, Option[String])]): EncodedString[FormQuery] =
    EncodedString {
      query.map {
        case (k, ov) =>
          val kEncoded = encodeQueryParam(k).asKey
          ov.fold[EncodedString[FormQueryKV]](kEncoded.asKV)(encodeQueryValue(kEncoded)).encoded
      }.mkString("&")
    }

  def encodeQueryParam(queryParam: String): EncodedFormQueryParam =
    EncodedFormQueryParam(percentEncode(queryParam))

  private[encoding] def decodeQueryParam(encoded: EncodedString[FormQueryParam]): String =
    percentDecode(PctEncoded(encoded.encoded.replace('+',' ')))


  def percentEncode(s: String, isLegal: Char => Boolean): String = {
    def percentEncodeByte(b: Byte): String = {
      val hex1 = HexUpperCaseChars((b & 0xFF) >> 4)
      val hex2 = HexUpperCaseChars(b & 0xF)
      s"%$hex1$hex2"
    }

    val sb = new StringBuilder(s.length)
    val bb = Utf8.encode(s)
    val bytes = new Array[Byte](bb.remaining())
    bb.get(bytes, 0, bytes.length)

    bytes.foreach {
      b =>
        val c = (b & 0xFF).toChar
        if (isLegal(c))
          sb.append(c)
        else sb.append(percentEncodeByte(b))
    }

    sb.result
  }

  def percentEncode[T](s: String)(implicit encoding: PercentEncoding[T]): PctEncoded[T] =
    PctEncoded(percentEncode(s, encoding.isLegal))


  object NotPercentEncoded extends Exception("The must be properly percent-encoded, with no characters > 8 bits.")

  @inline private def isByte(c: Char) = (c & 0xff) == c

  /**
    * Replaces all UTF-8 percent-encoded sequences in the input string
    * with corresponding characters.
    *
    * There is no Charset argument here because a percent-encoded string
    * should have been encoded as UTF-8 before percent-encoding.
    */
  def unsafePercentDecode(toDecode: String): String = {
    if (!toDecode.forall(isByte)) throw NotPercentEncoded

    val in = CharBuffer.wrap(toDecode)
    // because the percent-encoded strings are already utf-8 encoded, the output
    // will have anywhere between 1x and 1/3x the number of input characters
    val out = ByteBuffer.allocate(in.remaining())
    while (in.hasRemaining) {
      val mark = in.position()
      val c = in.get()
      if (c == '%') {
        if (in.remaining() >= 2) {
          val xc = in.get()
          val yc = in.get()
          val x = Character.digit(xc, 0x10)
          val y = Character.digit(yc, 0x10)
          if (x != -1 && y != -1) {
            val oo = (x << 4) + y
              out.put(oo.toByte)
          }
          else throw NotPercentEncoded
        }
        else throw NotPercentEncoded
      }
      else out.put(c.toByte)
    }
    out.flip()
    Utf8.decode(out).toString
  }

  def percentDecode[T](toDecode: PctEncoded[T]): String = unsafePercentDecode(toDecode.encoded)

  /**
    * The spec asks us to use a charset that may be embedded in the form data,
    * but the UTF-16 conversion has already happened before we received the String.
    * So we skip that part of the spec.
    *
    * The spec also says to treat missing values (e.g. `key` as ("key", ""),
    * but I assume it's because they've never heard of None.  So this implementation
    * returns ("key", None) and you can .getOrElse("") if you like.
    *
    * `key=` still yields ("key", Some("")).
    */
  def w3cHtml5FormUrlDecode(encoded: String): Vector[(String, Option[String])] = {
    // We are decoding, but the encoding procedure is as follows:
    // replace ' ' with '+'
    // do not encode * - . 0-9 A-Z _ a-z
    // do percent-encode everything else

    def decode(s: String) = percentDecode(PctEncoded(s.replace("+", " ")))

    if (encoded.isEmpty)
      Vector("" -> None)
    else
      encoded.split("&", Integer.MAX_VALUE).toVector.map {
        string =>
          val (name, value) =
            string.split("=", 2) match {
              case Array(name)        => (name, None)
              case Array(name, value) => (name, Some(value))
            }
          (decode(name), value.map(decode))
      }

  }

  def split[C[_]<:SeqLike[_, C[A]],A](payload: C[A], delimiter: A): Vector[C[A]] = {
    @tailrec def loop(mark: Int, pos: Int, acc: Vector[C[A]]): Vector[C[A]] = {
      if (pos < payload.size)
        if (payload(pos) == delimiter)
          loop(pos+1, pos+1, acc :+ payload.slice(mark, pos))
        else
          loop(mark, pos+1, acc)
      else acc :+ payload.slice(mark, pos)
    }
    loop(0, 0, Vector())
  }

  def split[@specialized(Byte, Char, Int) A](payload: Array[A], delimiter: A): Vector[Array[A]] = {
    @tailrec def loop(mark: Int, pos: Int, acc: Vector[Array[A]]): Vector[Array[A]] = {
      if (pos < payload.length)
        if (payload(pos) == delimiter)
          loop(pos+1, pos+1, acc :+ payload.slice(mark, pos))
        else
          loop(mark, pos+1, acc)
      else acc :+ payload.slice(mark, pos)
    }
    loop(0, 0, Vector())
  }

//  // doesn't support "isindex"
//  // does support charset!
//  def w3cHtml5FormUrlDecode(payload: Array[Byte], defaultEncoding: Charset = Utf8): Vector[(String, Option[String])] = {
//    def decode(s: String) = percentDecode(PercentEncodedString(s.replace("+", " ")))
//
//    if (payload.isEmpty) Vector()
//    else {
//      val kvPairs: Vector[(Array[Byte], Option[Array[Byte]])] =
//        split(payload, '&'.toByte).map {
//          kv =>
//            val delimIndex = kv.indexWhere(_ == '='.toByte)
//            if (delimIndex < 0) kv -> None
//            else kv.slice(0, delimIndex) -> Some(kv.slice(delimIndex+1, kv.length))
//        }
//      val charset =
//        kvPairs
//          .find(kv => kv._1 == "_charset_".getBytes(Utf8))
//          .flatMap {
//            kv =>
//              kv._2
//                .map(new String(_, Utf8))
//                .flatMap {
//                  c => if (Charset.isSupported(c)) Some(Charset.forName(c)) else None
//                }
//          }.getOrElse(defaultEncoding)
//
//      kvPairs.map {
//        case (key, value) => decode(new String(key, charset)) -> value.map(new String(_, charset)).map(decode)
//      }
//    }
//  }
  // commented out because I still don't understand how charset works here, in conjunction with assuming proper input
  // to PercentEncodedString
}
