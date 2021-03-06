package com.foreignlanguagereader.domain.service.definition

import com.foreignlanguagereader.content.types.Language
import com.foreignlanguagereader.content.types.Language.Language
import com.foreignlanguagereader.content.types.internal.definition.DefinitionSource.DefinitionSource
import com.foreignlanguagereader.content.types.internal.definition.{
  DefinitionSource,
  SpanishDefinition
}
import com.foreignlanguagereader.domain.client.elasticsearch.ElasticsearchCacheClient
import com.foreignlanguagereader.domain.fetcher.DefinitionFetcher
import com.foreignlanguagereader.domain.fetcher.spanish.WebsterSpanishToEnglishFetcher
import com.foreignlanguagereader.domain.metrics.MetricsReporter

import javax.inject.Inject
import play.api.Configuration

import scala.concurrent.ExecutionContext

class SpanishDefinitionService @Inject() (
    val elasticsearch: ElasticsearchCacheClient,
    val websterSpanishToEnglishFetcher: WebsterSpanishToEnglishFetcher,
    override val config: Configuration,
    val metrics: MetricsReporter,
    implicit val ec: ExecutionContext
) extends LanguageDefinitionService[SpanishDefinition] {
  override val wordLanguage: Language = Language.SPANISH
  override val sources: List[DefinitionSource] =
    List(DefinitionSource.MIRRIAM_WEBSTER_SPANISH, DefinitionSource.WIKTIONARY)

  override val definitionFetchers: Map[
    (DefinitionSource, Language),
    DefinitionFetcher[_, SpanishDefinition]
  ] = Map(
    (
      DefinitionSource.MIRRIAM_WEBSTER_SPANISH,
      Language.ENGLISH
    ) -> websterSpanishToEnglishFetcher
  )

}
