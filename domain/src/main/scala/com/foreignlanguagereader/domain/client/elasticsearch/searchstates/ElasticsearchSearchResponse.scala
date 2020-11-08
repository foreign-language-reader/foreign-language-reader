package com.foreignlanguagereader.domain.client.elasticsearch.searchstates

import com.foreignlanguagereader.domain.client.common.{
  CircuitBreakerAttempt,
  CircuitBreakerFailedAttempt,
  CircuitBreakerNonAttempt,
  CircuitBreakerResult
}
import com.foreignlanguagereader.domain.client.elasticsearch.LookupAttempt
import org.elasticsearch.action.search.MultiSearchResponse
import play.api.Logger
import play.api.libs.json.{JsError, JsSuccess, Json, Reads, Writes}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

/**
  *
  * Turns the raw elasticsearch response into the query result.
  * This decides whether we will need to refetch from the original content source.
  *
  * @param index The elasticsearch index to cache the data. Should just be the type
  * @param fields The fields needed to look up the correct item. Think of this as the primary key.
  * @param fetcher A function to be called if results are not in elasticsearch, which can try to get the results again.
  * @param maxFetchAttempts If we don't have any results, how many times should we search for this? Highly source dependent.
  * @param response The elasticsearch response created by using the query in ElasticsearchRequest
  * @param tag The class of T so that sequences can be initialized. Automatically given.
  * @param ec Automatically taken from the implicit val near the caller. This is the thread pool to block on when fetching.
  * @tparam T A case class.
  */
case class ElasticsearchSearchResponse[T](
    index: String,
    fields: Map[String, String],
    fetcher: () => Future[CircuitBreakerResult[List[T]]],
    maxFetchAttempts: Int,
    response: CircuitBreakerResult[MultiSearchResponse]
)(implicit
    tag: ClassTag[T],
    reads: Reads[T],
    writes: Writes[T],
    ec: ExecutionContext
) {
  val logger: Logger = Logger(this.getClass)

  val (
    elasticsearchResult: Option[List[T]],
    fetchCount: Int,
    lookupId: Option[String],
    queried: Boolean
  ) =
    response match {
      case CircuitBreakerAttempt(r) =>
        val responses = r.getResponses
        val result = parseResults(responses.head)
        val (attempts, id) = parseAttempts(responses.tail.head)
        (result, attempts, id, true)
      case _ =>
        (None, 0, None, false)
    }

  // Were we able to connect to elasticsearch?
  // Necessary downstream to prevent us from resaving to elasticsearch.

  lazy val getResultOrFetchFromSource: Future[ElasticsearchSearchResult[T]] =
    elasticsearchResult match {
      case Some(es) =>
        Future.successful(
          ElasticsearchSearchResult(
            index = index,
            fields = fields,
            result = es,
            fetchCount = fetchCount,
            lookupId = lookupId,
            refetched = false,
            queried = queried
          )
        )
      case None if fetchCount < maxFetchAttempts => fetchFromSource
      case None =>
        Future.successful(
          ElasticsearchSearchResult(
            index = index,
            fields = fields,
            result = List(),
            fetchCount = fetchCount,
            lookupId = lookupId,
            refetched = false,
            queried = queried
          )
        )
    }

  lazy val fetchFromSource: Future[ElasticsearchSearchResult[T]] = {
    logger.info(s"Refetching from source for query on $index")
    fetcher()
      .map {
        case CircuitBreakerAttempt(result) =>
          ElasticsearchSearchResult(
            index = index,
            fields = fields,
            result = result,
            fetchCount = fetchCount + 1,
            lookupId = lookupId,
            refetched = true,
            queried = queried
          )
        case CircuitBreakerNonAttempt() =>
          ElasticsearchSearchResult(
            index = index,
            fields = fields,
            result = List(),
            fetchCount = fetchCount,
            lookupId = lookupId,
            refetched = false,
            queried = queried
          )
        case CircuitBreakerFailedAttempt(e) =>
          logger.error(
            s"Failed to call fetcher on index=$index fields=$fields due to error ${e.getMessage}",
            e
          )
          ElasticsearchSearchResult(
            index = index,
            fields = fields,
            result = List(),
            fetchCount = fetchCount + 1,
            lookupId = lookupId,
            refetched = true,
            queried = queried
          )
      }
      .recover {
        case e: Exception =>
          logger.error(
            s"Failed to call fetcher on index=$index fields=$fields due to error ${e.getMessage}",
            e
          )
          ElasticsearchSearchResult(
            index = index,
            fields = fields,
            result = List(),
            fetchCount = fetchCount + 1,
            lookupId = lookupId,
            refetched = true,
            queried = queried
          )

      }
  }

  private[this] def parseResults(
      results: MultiSearchResponse.Item
  ): Option[List[T]] =
    if (results.isFailure) {
      logger.error(
        s"Failed to get result from elasticsearch on index $index due to error ${results.getFailureMessage}",
        results.getFailure
      )
      None
    } else {
      val r: Array[Option[T]] =
        results.getResponse.getHits.getHits
          .map(_.getSourceAsString)
          .map(source =>
            Json.parse(source).validate[T] match {
              case JsSuccess(value, _) => Some(value)
              case JsError(errors) =>
                val errs = errors
                  .map {
                    case (path, e) => s"At path $path: ${e.mkString(", ")}"
                  }
                  .mkString("\n")
                logger.error(
                  s"Failed to parse results from elasticsearch on index $index due to errors: $errs"
                )
                None
            }
          )
      if (r.nonEmpty && r.forall(_.isDefined)) Some(r.flatten.toList)
      else None
    }

  private[this] def parseAttempts(
      attempts: MultiSearchResponse.Item
  ): (Int, Option[String]) =
    if (attempts.isFailure) {
      logger.error(
        s"Failed to get request count from elasticsearch on index $index due to error ${attempts.getFailureMessage}",
        attempts.getFailure
      )
      (0, None)
    } else {
      attempts.getResponse
      val hit = attempts.getResponse.getHits.getHits.head
      Json.parse(hit.getSourceAsString).validate[LookupAttempt] match {
        case JsSuccess(value, _) => (value.count, Some(hit.getId))
        case JsError(errors) =>
          val errs = errors
            .map {
              case (path, e) => s"At path $path: ${e.mkString(", ")}"
            }
            .mkString("\n")
          logger.error(
            s"Failed to parse results from elasticsearch on index $index due to errors: $errs"
          )
          (0, None)
      }
    }
}

object ElasticsearchSearchResponse {
  def fromResult[T](
      request: ElasticsearchSearchRequest[T],
      result: CircuitBreakerResult[MultiSearchResponse]
  )(implicit
      read: Reads[T],
      writes: Writes[T],
      tag: ClassTag[T],
      ec: ExecutionContext
  ): ElasticsearchSearchResponse[T] = {

    ElasticsearchSearchResponse(
      request.index,
      request.fields,
      request.fetcher,
      request.maxFetchAttempts,
      result
    )
  }
}