package fix

import scalafix.v1._
import scala.meta._

class Http4s018To020 extends SemanticRule("Http4s018To020") {

  override def fix(implicit doc: SemanticDocument): Patch = {
    doc.tree.collect{
      case HttpServiceRules(patch) => patch
      case WithBodyRules(patch) => patch
      case CookiesRules(patch) => patch
      case MimeRules(patch) => patch
      case ClientRules(patch) => patch
    }
  }.asPatch

  object HttpServiceRules {
    def unapply(t: Tree)(implicit doc: SemanticDocument): Option[Patch] = t match {
      case t@Type.Name("HttpService") => Some(Patch.replaceTree(t, "HttpRoutes"))
      case t@Term.Name("HttpService") => Some(Patch.replaceTree(t, "HttpRoutes.of"))
      case t@Importee.Name(Name("HttpService")) => Some(Patch.replaceTree(t, "HttpRoutes"))
      case _ => None
    }
  }

  object WithBodyRules {
    def unapply(t: Tree)(implicit doc: SemanticDocument): Option[Patch] = t match {
      case Defn.Val(_, _, tpe, rhs) if containsWithBody(rhs) =>
        Some(replaceWithBody(rhs) + tpe.map(removeExternalF))
      case Defn.Def(_, _, _, _, tpe, rhs) if containsWithBody(rhs) =>
        Some(replaceWithBody(rhs) + tpe.map(removeExternalF))
      case Defn.Var(_, _, tpe, rhs) if rhs.exists(containsWithBody) =>
        Some(rhs.map(replaceWithBody).asPatch + tpe.map(removeExternalF))
      case _ => None
    }
  }

  object CookiesRules {
    def unapply(t: Tree)(implicit doc: SemanticDocument): Option[Patch] =  t match {
      case Importer(Term.Select(Term.Name("org"), Term.Name("http4s")), is) =>
        Some(is.collect {
          case c@Importee.Name(Name("Cookie")) =>
            Patch.addGlobalImport(Importer(Term.Select(Term.Name("org"), Term.Name("http4s")),
              List(Importee.Rename(Name("ResponseCookie"), Name("Cookie"))))) +
              Patch.removeImportee(c)
          case c@Importee.Rename(Name("Cookie"), rename) =>
            Patch.addGlobalImport(Importer(Term.Select(Term.Name("org"), Term.Name("http4s")),
              List(Importee.Rename(Name("ResponseCookie"), rename)))) +
              Patch.removeImportee(c)
        }.asPatch)
      case _ => None
    }
  }

  object MimeRules {
    val mimeMatcher = SymbolMatcher.normalized("org/http4s/MediaType#")

    def unapply(t: Tree)(implicit doc: SemanticDocument): Option[Patch] = t match {
      case Term.Select(mimeMatcher(_), media) =>
        val mediaParts = media.toString.replace("`", "").split("/").map{
          part =>
            if(!part.forall(c => c.isLetterOrDigit || c == '_'))
              s"`$part`"
            else
              part
        }
        Some(Patch.replaceTree(media,
          mediaParts.mkString(".")
        ))
      case _ => None
    }
  }

  object ClientRules {
    def unapply(t: Tree)(implicit doc: SemanticDocument): Option[Patch] = t match {
      // Client builders
      case Importee.Name(c@Name("Http1Client")) =>
        Some(Patch.replaceTree(c, "BlazeClientBuilder"))
      case c@Term.Apply(Term.ApplyType(Term.Name("Http1Client"), tpes), configParam) =>
        Some(patchClient(c, configParam, tpes, ClientType.Resource))
      case d@Defn.Def(_, _, _, _, _, c@Term.Apply(Term.ApplyType(Term.Select(Term.Name("Http1Client"), Term.Name("stream")), tpes), configParam)) =>
        Some(patchClient(c, configParam, tpes, ClientType.Stream))
      case d@Defn.Val(_, _, _, c@Term.Apply(Term.ApplyType(Term.Select(Term.Name("Http1Client"), Term.Name("stream")), tpes), configParam)) =>
        Some(patchClient(c, configParam, tpes, ClientType.Stream))
      case d@Defn.Var(_, _, _, c@Term.Apply(Term.ApplyType(Term.Select(Term.Name("Http1Client"), Term.Name("stream")), tpes), configParam)) =>
        Some(patchClient(c, configParam, tpes, ClientType.Stream))
      case _ => None

    }

    sealed trait ClientType
    object ClientType {

      case object Stream extends ClientType

      case object Resource extends ClientType
    }

    def patchClient(client: Term, configParam: List[Term], tpes: List[Type], clientType: ClientType)(implicit doc: SemanticDocument) = {
      val configParams = getClientConfigParams(configParam)
      val ec = configParams.getOrElse("executionContext", Term.Name("global"))
      val sslc = configParams.get("sslContext")
      val newClientBuilder = Term.Apply(Term.ApplyType(Term.Name("BlazeClientBuilder"), tpes), List(Some(ec), sslc).flatten)
      val withParams = withConfigParams(configParams).map(p => Patch.addRight(client, s"$p\n"))
      Patch.replaceTree(client, newClientBuilder.toString()) ++ withParams + applyClientType(client, clientType) + (ec match {
        case Term.Name("global") =>
          Patch.addGlobalImport(importer"scala.concurrent.ExecutionContext.Implicits.global")
        case _ =>
          Patch.empty
      })
    }


    def applyClientType(client: Tree, clientType: ClientType): Patch = clientType match {
      case ClientType.Stream => Patch.addRight(client, ".stream")
      case _ => Patch.empty
    }
  }


  def getClientConfigParams(params: List[Term])(implicit doc: SemanticDocument) =
    params.headOption.flatMap {
      case c: Term.Name =>
        doc.tree.collect {
          case Defn.Val(_, pats, _, rhs) if isClientConfig(c, pats) =>
            rhs
        }.headOption
    } match {
      case Some(Term.Apply(_, ps)) =>
        ps.collect{
          case Term.Assign(Term.Name(name: String), p: Term) =>
            name -> p
        }.toMap
      case _ => Map.empty[String, Term]
    }

  def withConfigParams(params: Map[String, Term]): List[String] =
    params.flatMap{
      case ("lenientParser", Lit(v: Boolean)) =>
        if(v) Some(s".withParserMode(org.http4s.client.blaze.ParserMode.Lenient)")
        else Some(s".withParserMode(org.http4s.client.blaze.ParserMode.Strict)")
      case ("userAgent", Term.Apply(_, agent)) =>
        Some(s".withUserAgent(${agent.mkString})")
      case ("checkEndpointIdentification", Lit(v: Boolean)) =>
        Some(s".withCheckEndpointAuthentication($v)")
      case ("group", Term.Apply(_, group)) =>
        Some(s".withAsynchronousChannelGroup($group)")
      case (s, term) if s != "executionContext" && s != "sslContext" && s != "group"=>
        Some(s".with${s.head.toUpper}${s.tail}($term)")
      case _ => None
    }.toList

  def removeExternalF(t: Type) =
    t match {
      case r@t"$a[Request[$b]]" =>
        // Note: we only change type def in request and not in response as normally the responses created with
        // e.g. Ok() are still F[Response[F]]
        Patch.replaceTree(r, s"Request[$b]")
      case _ =>
        Patch.empty
    }

  def replaceWithBody(t: Tree) =
    t.collect{
      case Term.Select(p, t@Term.Name("withBody")) =>
        Patch.replaceTree(t, "withEntity")
    }.asPatch

  def containsWithBody(t: Tree): Boolean =
    t.collect {
      case Term.Select(_, Term.Name("withBody")) =>
        true
    }.contains(true)

  def isClientConfig(configName: Term.Name, pats: List[Pat]) =
    pats.exists{
      case Pat.Var(Term.Name(name)) => name == configName.value
      case _ => false
    }

}
