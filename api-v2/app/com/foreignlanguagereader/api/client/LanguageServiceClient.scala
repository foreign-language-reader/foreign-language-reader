package com.foreignlanguagereader.api.client

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import com.foreignlanguagereader.api.client.common.{
  CircuitBreakerAttempt,
  CircuitBreakerResult,
  Circuitbreaker,
  WsClient
}
import com.foreignlanguagereader.api.contentsource.definition.DefinitionEntry
import com.foreignlanguagereader.api.domain.Language.Language
import com.foreignlanguagereader.api.domain.definition.Definition
import javax.inject.Inject
import play.api.libs.json.Reads
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}

class LanguageServiceClient @Inject()(config: Configuration,
                                      val ws: WSClient,
                                      val system: ActorSystem)
    extends WsClient
    with Circuitbreaker {
  override val logger: Logger = Logger(this.getClass)
  implicit val ec: ExecutionContext =
    system.dispatchers.lookup("language-service-context")
  override val timeout =
    Duration(config.get[Int]("language-service.timeout"), TimeUnit.SECONDS)
  override val resetTimeout: FiniteDuration =
    FiniteDuration(60, TimeUnit.SECONDS)

  // This token only works for localhost
  // Will need to replace this with config properties when I learn how secrets work in play
  val languageServiceAuthToken = "simpletoken"
  override val headers: List[(String, String)] = List(
    ("Authorization,", languageServiceAuthToken)
  )

  val languageServiceBaseUrl: String =
    config.get[String]("language-service.url")

  implicit val readsDefinitionEntry: Reads[Seq[DefinitionEntry]] =
    DefinitionEntry.readsSeq

  def getDefinition(
    wordLanguage: Language,
    word: String
  ): Future[CircuitBreakerResult[Option[Seq[Definition]]]] =
    get(s"$languageServiceBaseUrl/v1/definition/$wordLanguage/$word")
      .map(
        results =>
          results.transform {
            case Some(r) => Some(r.map(_.toDefinition))
            case None    => None
        }
      )
}
