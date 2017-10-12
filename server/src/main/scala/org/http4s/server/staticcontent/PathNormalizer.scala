package org.http4s.server.staticcontent

import java.lang.StringBuilder

// Adapted from https://github.com/Norconex/commons-lang/blob/c83fdeac7a60ac99c8602e0b47056ad77b08f570/norconex-commons-lang/src/main/java/com/norconex/commons/lang/url/URLNormalizer.java#L429
object PathNormalizer {

  private def startsWith(b: StringBuilder, str: String): Boolean =
    b.indexOf(str) == 0
  private def equalStrings(b: StringBuilder, str: String): Boolean =
    b.length == str.length && startsWith(b, str)
  private def deleteStart(b: StringBuilder, str: String): StringBuilder =
    b.delete(0, str.length)
  private def replaceStart(b: StringBuilder, target: String, replacement: String): StringBuilder = {
    deleteStart(b, target)
    b.insert(0, replacement)
  }
  private def removeLastSegment(b: StringBuilder): Unit =
    b.lastIndexOf("/") match {
      case -1 => b.setLength(0)
      case n => b.setLength(n)
    }

  def removeDotSegments(path: String): String = {
    // (Bulleted comments are from RFC3986, section-5.2.4)

    // 1.  The input buffer is initialized with the now-appended path
    //     components and the output buffer is initialized to the empty
    //     string.
    val in = new StringBuilder(path)
    val out = new StringBuilder

    // 2.  While the input buffer is not empty, loop as follows:
    while (in.length > 0) {

      // A.  If the input buffer begins with a prefix of "../" or "./",
      //     then remove that prefix from the input buffer; otherwise,
      if (startsWith(in, "../"))
        deleteStart(in, "../")
      else if (startsWith(in, "./"))
        deleteStart(in, "./")

      // B.  if the input buffer begins with a prefix of "/./" or "/.",
      //     where "." is a complete path segment, then replace that
      //     prefix with "/" in the input buffer; otherwise,
      else if (startsWith(in, "/./"))
        replaceStart(in, "/./", "/")
      else if (equalStrings(in, "/."))
        replaceStart(in, "/.", "/")

      // C.  if the input buffer begins with a prefix of "/../" or "/..",
      //     where ".." is a complete path segment, then replace that
      //     prefix with "/" in the input buffer and remove the last
      //     segment and its preceding "/" (if any) from the output
      //     buffer; otherwise,
      else if (startsWith(in, "/../")) {
        replaceStart(in, "/../", "/")
        removeLastSegment(out)
      } else if (equalStrings(in, "/..")) {
        replaceStart(in, "/..", "/")
        removeLastSegment(out)
      }

      // D.  if the input buffer consists only of "." or "..", then remove
      //      that from the input buffer; otherwise,
      else if (equalStrings(in, ".."))
        deleteStart(in, "..")
      else if (equalStrings(in, "."))
        deleteStart(in, ".")

      // E.  move the first path segment in the input buffer to the end of
      //     the output buffer, including the initial "/" character (if
      //     any) and any subsequent characters up to, but not including,
      //     the next "/" character or the end of the input buffer.
      else
        in.indexOf("/", 1) match {
          case nextSlashIndex if nextSlashIndex > -1 =>
            out.append(in.substring(0, nextSlashIndex))
            in.delete(0, nextSlashIndex)
          case _ =>
            out.append(in)
            in.setLength(0)
        }
    }

    // 3.  Finally, the output buffer is returned as the result of
    //     remove_dot_segments.
    out.toString
  }

}
