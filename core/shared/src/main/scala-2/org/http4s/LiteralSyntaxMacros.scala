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

package org.http4s

object LiteralSyntaxMacros {

  import org.typelevel.literally.Literally

  object uri extends Literally[Uri] {
    def validate(c: Context)(s: String): Either[String, c.Expr[Uri]] = {
      import c.universe._
      Uri.fromString(s) match {
        case Right(_) => Right(c.Expr(q"_root_.org.http4s.Uri.unsafeFromString($s)"))
        case Left(parseError) => Left(s"invalid URI: ${parseError.details}")
      }
    }
    def make(c: Context)(args: c.Expr[Any]*): c.Expr[Uri] = apply(c)(args: _*)
  }

  object path extends Literally[Uri.Path] {
    def validate(c: Context)(s: String): Either[String, c.Expr[Uri.Path]] = {
      import c.universe._
      Right(c.Expr(q"_root_.org.http4s.Uri.Path.unsafeFromString($s)"))
    }
    def make(c: Context)(args: c.Expr[Any]*): c.Expr[Uri.Path] = apply(c)(args: _*)
  }

  object scheme extends Literally[Uri.Scheme] {
    def validate(c: Context)(s: String): Either[String, c.Expr[Uri.Scheme]] = {
      import c.universe._
      Uri.Scheme.fromString(s) match {
        case Right(_) => Right(c.Expr(q"_root_.org.http4s.Uri.Scheme.unsafeFromString($s)"))
        case Left(pf) => Left(s"invalid Scheme: ${pf.details}")
      }
    }
    def make(c: Context)(args: c.Expr[Any]*): c.Expr[Uri.Scheme] = apply(c)(args: _*)
  }

  object mediaType extends Literally[MediaType] {
    def validate(c: Context)(s: String): Either[String, c.Expr[MediaType]] = {
      import c.universe._
      MediaType.parse(s) match {
        case Right(_) => Right(c.Expr(q"_root_.org.http4s.MediaType.unsafeParse($s)"))
        case Left(pf) => Left(s"invalid MediaType: ${pf.details}")
      }
    }
    def make(c: Context)(args: c.Expr[Any]*): c.Expr[MediaType] = apply(c)(args: _*)
  }

  object qValue extends Literally[QValue] {
    def validate(c: Context)(s: String): Either[String, c.Expr[QValue]] = {
      import c.universe._
      QValue.fromString(s) match {
        case Right(_) => Right(c.Expr(q"_root_.org.http4s.QValue.unsafeFromString($s)"))
        case Left(pf) => Left(s"invalid QValue: ${pf.details}")
      }
    }
    def make(c: Context)(args: c.Expr[Any]*): c.Expr[QValue] = apply(c)(args: _*)
  }
}
