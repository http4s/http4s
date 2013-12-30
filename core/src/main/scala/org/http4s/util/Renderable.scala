package org.http4s.util

/**
 * @author Bryce Anderson
 *         Created on 12/30/13
 */
trait Renderable {
  def render(builder: StringBuilder): StringBuilder

  def value: String = render(new StringBuilder).result()
}
