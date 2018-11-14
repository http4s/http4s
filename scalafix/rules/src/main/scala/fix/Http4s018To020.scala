package fix

import scalafix.v1._

import scala.meta._

class v0_20 extends SemanticRule("v0_20") {

  override def fix(implicit doc: SemanticDocument): Patch = {
    doc.tree.collect {
      case HttpServiceRules(patch) => patch
      case WithBodyRules(patch) => patch
      case CookiesRules(patch) => patch
      case MimeRules(patch) => patch
      case ClientRules(patch) => patch
    }
  }.asPatch

  object CookiesRules {
    def unapply(t: Tree): Option[Patch] = t match {
      case Importer(Term.Select(Term.Name("org"), Term.Name("http4s")), is) =>
        Some(is.collect {
          case c @ Importee.Name(Name("Cookie")) =>
            Patch.addGlobalImport(
              Importer(
                Term.Select(Term.Name("org"), Term.Name("http4s")),
                List(Importee.Rename(Name("ResponseCookie"), Name("Cookie"))))) +
              Patch.removeImportee(c)
          case c @ Importee.Rename(Name("Cookie"), rename) =>
            Patch.addGlobalImport(
              Importer(
                Term.Select(Term.Name("org"), Term.Name("http4s")),
                List(Importee.Rename(Name("ResponseCookie"), rename)))) +
              Patch.removeImportee(c)
        }.asPatch)
      case _ => None
    }
  }
}


sealed trait ClientType
object ClientType {

  case object Stream extends ClientType

  case object Apply extends ClientType
}

object ClientRules {
  def unapply(t: Tree)(implicit doc: SemanticDocument): Option[Patch] = t match {
    // Client builders
    case Importee.Name(c @ Name("Http1Client")) =>
      Some(Patch.replaceTree(c, "BlazeClientBuilder"))
    case d @ Defn.Def(
    _,
    _,
    _,
    _,
    _,
    c @ Term.Apply(Term.ApplyType(Term.Name("Http1Client"), tpes), configParam)) =>
      Some(patchClient(d, c, configParam, tpes, ClientType.Apply))
    case d @ Defn.Val(
    _,
    _,
    _,
    c @ Term.Apply(Term.ApplyType(Term.Name("Http1Client"), tpes), configParam)) =>
      Some(patchClient(d, c, configParam, tpes, ClientType.Apply))
    case d @ Defn.Var(
    _,
    _,
    _,
    c @ Term.Apply(Term.ApplyType(Term.Name("Http1Client"), tpes), configParam)) =>
      Some(patchClient(d, c, configParam, tpes, ClientType.Apply))
    case d @ Defn.Def(
    _,
    _,
    _,
    _,
    _,
    c @ Term.Apply(
    Term.ApplyType(Term.Select(Term.Name("Http1Client"), Term.Name("stream")), tpes),
    configParam)) =>
      Some(patchClient(d, c, configParam, tpes, ClientType.Stream))
    case d @ Defn.Val(
    _,
    _,
    _,
    c @ Term.Apply(
    Term.ApplyType(Term.Select(Term.Name("Http1Client"), Term.Name("stream")), tpes),
    configParam)) =>
      Some(patchClient(d, c, configParam, tpes, ClientType.Stream))
    case d @ Defn.Var(
    _,
    _,
    _,
    c @ Term.Apply(
    Term.ApplyType(Term.Select(Term.Name("Http1Client"), Term.Name("stream")), tpes),
    configParam)) =>
      Some(patchClient(d, c, configParam, tpes, ClientType.Stream))
    case _ => None

  }

  private def patchClient(
                           defn: Defn,
                           client: Term,
                           configParam: List[Term],
                           tpes: List[Type],
                           clientType: ClientType)(implicit doc: SemanticDocument) = {
    val configParams = getClientConfigParams(configParam)
    val ec = configParams.getOrElse("executionContext", Term.Name("global"))
    val sslc = configParams.get("sslContext")
    val newClientBuilder = Term.Apply(
      Term.ApplyType(Term.Name("BlazeClientBuilder"), tpes),
      List(Some(ec), sslc).flatten)
    val newClientBuilderPatch = Patch.replaceTree(client, newClientBuilder.toString())
    val withParamsPatches = withConfigParams(configParams).map(p => Patch.addRight(client, p))
    val clientTypePatch = applyClientType(defn, client, clientType)
    val ecPatch = ec match {
      case Term.Name("global") =>
        Patch.addGlobalImport(importer"scala.concurrent.ExecutionContext.Implicits.global")
      case _ =>
        Patch.empty
    }
    newClientBuilderPatch ++ withParamsPatches + clientTypePatch + ecPatch
  }

  private[this] def applyClientType(defn: Defn, client: Tree, clientType: ClientType): Patch =
    clientType match {
      case ClientType.Stream =>
        Patch.addRight(client, ".stream")
      case ClientType.Apply =>
        Patch.addRight(client, ".allocate") + replaceType(defn)
    }

  private def replaceType(defn: Defn): Patch =
    defn match {
      case Defn.Val(_, _, tpe, _) =>
        tpe.map(replaceClientType).asPatch
      case Defn.Var(_, _, tpe, _) =>
        tpe.map(replaceClientType).asPatch
      case Defn.Def(_, _, _, _, tpe, _) =>
        tpe.map(replaceClientType).asPatch
      case d =>
        Patch.lint(Diagnostic("1", s"Your client definition $defn needs to be replaced", d.pos))
    }
  private def replaceClientType(t: Type): Patch = t match {
    case Type.Apply(a, List(Type.Apply(Type.Name("Client"), List(b)))) =>
      Patch.replaceTree(t, s"$a[(Client[$b], $a[Unit])]")
    case _ => Patch.empty
  }

  private[this] def getClientConfigParams(params: List[Term])(implicit doc: SemanticDocument) =
    params.headOption.flatMap {
      case c: Term.Name =>
        doc.tree.collect {
          case Defn.Val(_, pats, _, rhs) if isClientConfig(c, pats) =>
            rhs
        }.headOption
      case c: Term.Apply => Some(c)
      case _ => None
    } match {
      case Some(Term.Apply(_, ps)) =>
        ps.collect {
          case Term.Assign(Term.Name(name: String), p: Term) =>
            name -> p
        }.toMap
      case _ =>
        Map.empty[String, Term]
    }

  private[this] def withConfigParams(params: Map[String, Term]): List[String] =
    params.flatMap {
      case ("lenientParser", Lit(v: Boolean)) =>
        if (v) Some(s".withParserMode(org.http4s.client.blaze.ParserMode.Lenient)")
        else Some(s".withParserMode(org.http4s.client.blaze.ParserMode.Strict)")
      case ("userAgent", Term.Apply(_, agent)) =>
        Some(s".withUserAgent(${agent.mkString})")
      case ("checkEndpointIdentification", Lit(v: Boolean)) =>
        Some(s".withCheckEndpointAuthentication($v)")
      case ("group", Term.Apply(_, group)) =>
        Some(s".withAsynchronousChannelGroup($group)")
      case (s, term) if s != "executionContext" && s != "sslContext" && s != "group" =>
        Some(s".with${s.head.toUpper}${s.tail}($term)")
      case _ => None
    }.toList

  private[this] def isClientConfig(configName: Term.Name, pats: List[Pat]) =
    pats.exists {
      case Pat.Var(Term.Name(name)) => name == configName.value
      case _ => false
    }

}

object HttpServiceRules {
  val httpService = SymbolMatcher.normalized("org/http4s/HttpService.")
  def unapply(t: Tree)(implicit doc: SemanticDocument): Option[Patch] = t match {
    case httpService(Term.Apply(Term.ApplyType(t, _), _)) =>
      Some(Patch.replaceTree(t, "HttpRoutes.of"))
    case httpService(Term.Apply(t: Term.Name, _)) => Some(Patch.replaceTree(t, "HttpRoutes.of"))
    case Type.Apply(t @ Type.Name("HttpService"), _) => Some(Patch.replaceTree(t, "HttpRoutes"))
    case t @ Importee.Name(Name("HttpService")) => Some(Patch.replaceTree(t, "HttpRoutes"))
    case _ => None
  }
}

object MimeRules {
  private[this] val mimeMatcher = SymbolMatcher.normalized("org/http4s/MediaType#")

  def unapply(t: Tree)(implicit doc: SemanticDocument): Option[Patch] = {
    val symbol = t.symbol
    symbol.owner match {
      case mimeMatcher(_) =>
        val mediaParts = symbol.displayName.replace("`", "").split("/").map { part =>
          if (!part.forall(c => c.isLetterOrDigit || c == '_'))
            s"`$part`"
          else
            part
        }
        val newSymbol = Symbol(s"${symbol.owner.value}${mediaParts.init.mkString("/")}#")

        Some(
          Patch.renameSymbol(t.symbol, mediaParts.mkString(".")) + Patch.addGlobalImport(newSymbol))
      case _ => None
    }
  }
}


object WithBodyRules {
  def unapply(t: Tree): Option[Patch] = t match {
    case Defn.Val(_, _, tpe, rhs) if containsWithBody(rhs) =>
      Some(replaceWithBody(rhs) + tpe.map(removeExternalF))
    case Defn.Def(_, _, _, _, tpe, rhs) if containsWithBody(rhs) =>
      Some(replaceWithBody(rhs) + tpe.map(removeExternalF))
    case Defn.Var(_, _, tpe, rhs) if rhs.exists(containsWithBody) =>
      Some(rhs.map(replaceWithBody).asPatch + tpe.map(removeExternalF))
    case _ => None
  }

  private[this] def replaceWithBody(t: Tree) =
    t.collect {
      case Term.Select(_, t @ Term.Name("withBody")) =>
        Patch.replaceTree(t, "withEntity")
      case Term.Apply(
      Term.Select(_, fm @ Term.Name("flatMap")),
      List(Term.Apply(Term.Select(_, Term.Name("withBody")), _))) =>
        Patch.replaceTree(fm, "map")

    }.asPatch

  private[this] def containsWithBody(t: Tree): Boolean =
    t.collect {
      case Term.Select(_, Term.Name("withBody")) =>
        true
    }
      .contains(true)

  private[this] def removeExternalF(t: Type) =
    t match {
      case r @ Type.Apply(_, Type.Apply(Type.Name("Request"), b :: Nil) :: Nil) =>
        // Note: we only change type def in request and not in response as normally the responses created with
        // e.g. Ok() are still F[Response[F]]
        Patch.replaceTree(r, s"Request[$b]")
      case _ =>
        Patch.empty
    }
}
