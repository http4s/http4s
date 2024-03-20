/*
 * Copyright 2013 http4s.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.http4s.multipart

import fs2.Stream
import fs2.io.file.Files
import fs2.io.file.Flags
import fs2.io.file.Path
import org.http4s.EntityBody
import org.http4s.Header

/** Generic representation of typical Multipart Part bodies, as either a string or a file path.
  * Produced by [[PartReceiver.toMixedBuffer]].
  *
  * Similar to [[Part]], but with less Header-oriented detail, and a clearer distinction
  * between in-memory data vs on-disk data.
  */
sealed trait PartValue {

  /** Returns a byte-stream representation of the part's body.
    *
    * For `OfString` parts, the underlying string is piped through a UTF-8 encoder.
    * For `OfFile` parts, the underlying file's content will be read from disk.
    *
    * @return The part's body as a stream of bytes
    */
  def bytes[F[_]: Files]: EntityBody[F]

  /** Fold over the different PartValue subtypes to compute a value
    *
    * @param onString Called with the underlying String if this part is an `OfString`
    * @param onFile Called with the underlying filename and file path if this part is an `OfFile`
    * @return The value returned by either `onString` or `onFile`
    */
  def fold[A](onString: String => A, onFile: (String, Path) => A): A

  /** Builds a new [[Part]] from this `PartValue`.
    *
    * @param name The name of the part
    * @param headers Any extra headers to add to the part. `Content-Disposition` is added
    *                automatically based on the `name`, and in the case of `OfFile` parts,
    *                the filename
    * @return a new [[Part]] based on the given name, applicable filename, and body of this `PartValue`.
    */
  def toPart[F[_]: Files](name: String, headers: Header.ToRaw*): Part[F] = fold(
    s => Part.formData(name, s, headers: _*),
    (filename, _) => Part.fileData(name, filename, bytes[F], headers: _*),
  )
}
object PartValue {

  final case class OfString(value: String) extends PartValue {
    def bytes[F[_]: Files]: EntityBody[F] = fs2.text.utf8.encode[F](Stream.emit(value))
    def fold[A](onString: String => A, onFile: (String, Path) => A): A = onString(value)
  }

  final case class OfFile(filename: String, path: Path) extends PartValue {
    def bytes[F[_]: Files]: EntityBody[F] = Files[F].readAll(path, 8192, Flags.Read)
    def fold[A](onString: String => A, onFile: (String, Path) => A): A =
      onFile(filename, path)
  }

}
