package com.foreignlanguagereader.domain.client.languageservice

import akka.actor.ActorSystem
import cats.data.Nested
import cats.implicits._
import com.foreignlanguagereader.content.types.Language.Language
import com.foreignlanguagereader.content.types.internal.word.PartOfSpeech.PartOfSpeech
import com.foreignlanguagereader.content.types.internal.word.{
  PartOfSpeech,
  Word
}
import com.foreignlanguagereader.domain.client.common.{
  CircuitBreakerResult,
  RestClient,
  RestClientBuilder
}
import com.foreignlanguagereader.domain.metrics.MetricsReporter
import com.foreignlanguagereader.dto.v1.health.ReadinessStatus.ReadinessStatus
import play.api.libs.json.{JsObject, Json}
import play.api.{Configuration, Logger}

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LanguageServiceClient @Inject() (
    config: Configuration,
    val system: ActorSystem,
    clientBuilder: RestClientBuilder,
    metrics: MetricsReporter
) {
  val logger: Logger = Logger(this.getClass)
  implicit val ec: ExecutionContext =
    system.dispatchers.lookup("language-service.context")

  val timeout: FiniteDuration =
    Duration(config.get[Int]("language-service.timeout"), TimeUnit.SECONDS)
  val scheme: String = config.get[String]("language-service.scheme")
  val baseUrl: String = config.get[String]("language-service.url")
  val port: Int = config.get[Int]("language-service.port")

  val client: RestClient =
    clientBuilder.buildClient("LanguageServiceClient", timeout = timeout)

  def getWordsForDocument(
      language: Language,
      document: String
  ): Future[CircuitBreakerResult[List[Word]]] = {
    val timer =
      metrics.reportLanguageServiceRequestStarted(language)
    val request =
      Json.obj("text" -> document)
    val result = client
      .post[JsObject, List[LanguageServiceWord]](
        s"$scheme://$baseUrl:$port/v1/tagging/${language.toString}/document",
        request,
        e => {
          logger.error(
            s"Failed to get tokens in $language for request: $document",
            e
          )
          metrics.reportLanguageServiceFailure(timer, language)
        }
      )
    metrics.reportLanguageServiceRequestFinished(timer)
    Nested(result)
      .map(
        _.map(word =>
          Word(
            language = language,
            token = word.token,
            tag = LanguageServiceClient
              .spacyPartOfSpeechToDomainPartOfSpeech(word.tag),
            lemma = word.lemma,
            definitions = List(),
            gender = None,
            number = None,
            tense = None,
            proper = None,
            processedToken = word.token
          )
        )
      )
      .value
  }

  def health(): ReadinessStatus = client.breaker.health()
}

object LanguageServiceClient {
  def spacyPartOfSpeechToDomainPartOfSpeech(tag: String): PartOfSpeech =
    tag match {
      case "ADJ"   => PartOfSpeech.ADJECTIVE
      case "ADP"   => PartOfSpeech.ADPOSITION
      case "ADV"   => PartOfSpeech.ADVERB
      case "AUX"   => PartOfSpeech.AUXILIARY
      case "CONJ"  => PartOfSpeech.CONJUNCTION
      case "CCONJ" => PartOfSpeech.COORDINATING_CONJUNCTION
      case "DET"   => PartOfSpeech.DETERMINER
      case "INTJ"  => PartOfSpeech.INTERJECTION
      case "NOUN"  => PartOfSpeech.NOUN
      case "NUM"   => PartOfSpeech.NUMBER
      case "PART"  => PartOfSpeech.PARTICLE
      case "PRON"  => PartOfSpeech.PRONOUN
      case "PROPN" => PartOfSpeech.PROPER_NOUN
      case "PUNCT" => PartOfSpeech.PUNCTUATION
      case "SCONJ" => PartOfSpeech.SUBORDINATING_CONJUNCTION
      case "SYM"   => PartOfSpeech.SYMBOL
      case "VERB"  => PartOfSpeech.VERB
      case "X"     => PartOfSpeech.OTHER
      case "SPACE" => PartOfSpeech.SPACE
      case _       => PartOfSpeech.UNKNOWN
    }
}