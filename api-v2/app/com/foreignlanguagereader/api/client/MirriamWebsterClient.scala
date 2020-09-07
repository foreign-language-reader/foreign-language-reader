package com.foreignlanguagereader.api.client

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import cats.implicits._
import com.foreignlanguagereader.api.client.common.{
  CircuitBreakerResult,
  Circuitbreaker,
  WsClient
}
import com.foreignlanguagereader.api.contentsource.definition.webster.{
  WebsterLearnersDefinitionEntry,
  WebsterSpanishDefinitionEntry
}
import com.foreignlanguagereader.api.domain.definition.Definition
import com.foreignlanguagereader.api.domain.word.Word
import javax.inject.Inject
import play.api.libs.json.Reads
import play.api.libs.ws.WSClient
import play.api.{Configuration, Logger}

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

class MirriamWebsterClient @Inject()(config: Configuration,
                                     val ws: WSClient,
                                     val system: ActorSystem)
    extends WsClient
    with Circuitbreaker {
  override val logger: Logger = Logger(this.getClass)
  implicit val ec: ExecutionContext =
    system.dispatchers.lookup("webster-context")
  override val timeout =
    Duration(config.get[Int]("webster.timeout"), TimeUnit.SECONDS)

  val learnersApiKey = ""
  val spanishApiKey = ""

  implicit val readsListLearners: Reads[List[WebsterLearnersDefinitionEntry]] =
    WebsterLearnersDefinitionEntry.helper.readsList
  implicit val readsListSpanish: Reads[List[WebsterSpanishDefinitionEntry]] =
    WebsterSpanishDefinitionEntry.helper.readsList

  // TODO: Make definition not found not be an error that increments the circuit breaker.
  // That means the input is bad, not the connection to the service.

  // TODO filter garbage

  def getLearnersDefinition(
    word: Word
  ): Future[CircuitBreakerResult[Option[List[Definition]]]] =
    get[List[WebsterLearnersDefinitionEntry]](
      s"https://www.dictionaryapi.com/api/v3/references/learners/json/${word.processedToken}?key=$learnersApiKey"
    ).map(
      results =>
        results.map {
          case Some(r) => Some(r.map(_.toDefinition(word.tag)))
          case None    => None
      }
    )

  def getSpanishDefinition(
    word: Word
  ): Future[CircuitBreakerResult[Option[List[Definition]]]] =
    get[List[WebsterSpanishDefinitionEntry]](
      s"https://www.dictionaryapi.com/api/v3/references/spanish/json/${word.processedToken}?key=$spanishApiKey"
    ).map(
      results =>
        results.map {
          case Some(r) => Some(r.map(_.toDefinition(word.tag)))
          case None    => None
      }
    )
}
