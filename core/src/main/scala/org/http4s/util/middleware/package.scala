package org.http4s.util

import org.http4s.{Route, RequestPrelude}

/**
 * @author Bryce Anderson
 *         Created on 3/9/13 at 10:43 AM
 */

package object middleware {

  def TranslateMount(prefix: String)(in: Route): Route = {
    case req: RequestPrelude if(req.pathInfo.startsWith(prefix + "/")) =>
      in(req.copy(pathInfo = req.pathInfo.substring(prefix.length)))
  }

}
