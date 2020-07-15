package com.foreignlanguagereader.api.client.elasticsearch.searchstates

import com.foreignlanguagereader.api.client.elasticsearch.LookupAttempt
import com.foreignlanguagereader.api.domain.Language
import com.foreignlanguagereader.api.domain.definition.{
  ChineseDefinition,
  Definition,
  DefinitionSource,
  GenericDefinition
}
import com.foreignlanguagereader.api.domain.word.PartOfSpeech
import com.sksamuel.elastic4s.ElasticDsl.indexInto
import com.sksamuel.elastic4s.playjson._
import org.scalatest.funspec.AnyFunSpec

class ElasticsearchResultTest extends AnyFunSpec {
  val index: String = "definition"
  val fields: Map[String, String] =
    Map("field1" -> "value1", "field2" -> "value2", "field3" -> "value3")
  describe("an elasticsearch result") {
    describe("where nothing was refetched") {
      it("does not persist anything to elasticsearch") {
        val result = ElasticsearchResult[Definition](
          index = index,
          fields = fields,
          result = None,
          fetchCount = 5,
          refetched = false
        )

        assert(result.cacheQueries.isEmpty)
        assert(result.attemptsQuery.isEmpty)
        assert(result.toIndex.isEmpty)
      }
    }
    describe("where refetching gave no result") {
      val result = ElasticsearchResult[Definition](
        index = index,
        fields = fields,
        result = None,
        fetchCount = 5,
        refetched = true
      )
      val attemptsQuery =
        indexInto("attempts").doc(LookupAttempt(index, fields, 5))
      it("correctly saves fetch attempts to elasticsearch") {
        assert(result.attemptsQuery.contains(attemptsQuery))
      }
      it("does not cache anything") {
        assert(result.cacheQueries.isEmpty)
        assert(result.toIndex.contains(List(attemptsQuery)))
      }
    }
    describe("where refetching produced results") {
      val dummyChineseDefinition = ChineseDefinition(
        List("definition 1", "definition 2"),
        Some(PartOfSpeech.NOUN),
        List("example 1", "example 2"),
        "ni3 hao3",
        "你好",
        "你好",
        Language.ENGLISH,
        DefinitionSource.MULTIPLE,
        token = "你好"
      )
      val dummyGenericDefinition = GenericDefinition(
        List("definition 1", "definition 2"),
        Some(PartOfSpeech.NOUN),
        List("example 1", "example 2"),
        Language.ENGLISH,
        Language.ENGLISH,
        DefinitionSource.MULTIPLE,
        "anything"
      )
      val result = ElasticsearchResult[Definition](
        index = index,
        fields = fields,
        result = Some(List(dummyChineseDefinition, dummyGenericDefinition)),
        fetchCount = 5,
        refetched = true
      )
      val attemptsQuery =
        indexInto("attempts").doc(LookupAttempt(index, fields, 5))
      val indexQuery = List(
        indexInto(index).doc(dummyChineseDefinition),
        indexInto(index).doc(dummyGenericDefinition)
      )
      it("correctly saves fetch attempts to elasticsearch") {
        assert(result.attemptsQuery.contains(attemptsQuery))
      }
      it("caches search results to elasticsearch") {
        assert(result.cacheQueries.contains(indexQuery))
        assert(result.toIndex.contains(attemptsQuery :: indexQuery))
      }
    }
  }
}
