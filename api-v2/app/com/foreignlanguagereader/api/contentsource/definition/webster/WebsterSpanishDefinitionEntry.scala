package com.foreignlanguagereader.api.contentsource.definition.webster

import com.foreignlanguagereader.api.contentsource.definition.DefinitionEntry
import com.foreignlanguagereader.api.contentsource.definition.webster.common.{
  WebsterDefinedRunOnPhrase,
  WebsterDefinition,
  WebsterHeadwordInfo,
  WebsterInflection,
  WebsterMeta,
  WebsterPartOfSpeech
}
import com.foreignlanguagereader.api.domain.Language
import com.foreignlanguagereader.api.domain.Language.Language
import com.foreignlanguagereader.api.domain.definition.{
  Definition,
  DefinitionSource
}
import com.foreignlanguagereader.api.domain.definition.DefinitionSource.DefinitionSource
import com.foreignlanguagereader.api.domain.word.PartOfSpeech.PartOfSpeech
import com.foreignlanguagereader.api.util.JsonSequenceHelper
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Reads, Writes}

case class WebsterSpanishDefinitionEntry(
  meta: WebsterMeta,
  headwordInfo: WebsterHeadwordInfo,
  // TODO figure out if we can turn this into an enum. What are the values?
  partOfSpeech: String,
  inflections: Option[Seq[WebsterInflection]],
  definitions: Seq[WebsterDefinition],
  definedRunOns: Option[Seq[WebsterDefinedRunOnPhrase]],
  shortDefinitions: Seq[String]
) extends DefinitionEntry {

  val (wordLanguage: Language, definitionLanguage: Language) =
    meta.language match {
      case Some("en") => (Language.ENGLISH, Language.SPANISH)
      case Some("es") => (Language.SPANISH, Language.ENGLISH)
      case _          => (Language.SPANISH, Language.ENGLISH)
    }
  override val source: DefinitionSource =
    DefinitionSource.MIRRIAM_WEBSTER_SPANISH

  // Here we make some opinionated choices about how webster definitions map to our model

  // Why can't we use an enum to read this in?
  // The Spanish dictionary puts multiple pieces of information within this string.
  // eg: "masculine or feminine noun"
  val tag: Option[PartOfSpeech] =
    WebsterPartOfSpeech.parseFromString(partOfSpeech) match {
      case Some(part) => Some(WebsterPartOfSpeech.toDomain(part))
      case None       => None
    }

  // TODO gender

  val subdefinitions: List[String] = {
    val d = definitions
    // senseSequence: Option[Seq[Seq[WebsterSense]]]
    // remove the nones
      .flatMap(_.senseSequence)
      // Our data model needs them flattened to one list
      .flatten
      .flatten
      // definingText: WebsterDefiningText => examples: Option[Seq[WebsterVerbalIllustration]]
      .flatMap(_.definingText.text)

    if (d.nonEmpty) d.toList else shortDefinitions.toList
  }

  val examples: List[String] = {
    //definitions: Seq[WebsterDefinition]
    val e = definitions
    // senseSequence: Option[Seq[Seq[WebsterSense]]]
    // remove the nones
      .flatMap(_.senseSequence)
      // Our data model needs them flattened to one list
      .flatten
      .flatten
      // definingText: WebsterDefiningText => examples: Option[Seq[WebsterVerbalIllustration]]
      .flatMap(_.definingText.examples)
      .flatten
      // Verbal Illustration means examples, so we can just get the text.
      .map(_.text)
    if (e.isEmpty) List() else e.toList
  }

  // Id is either the token, or token:n where n is the nth definition for the token.
  val token: String = meta.id.split(":")(0)

  lazy override val toDefinition: Definition = Definition(
    subdefinitions,
    tag,
    examples,
    wordLanguage,
    definitionLanguage,
    source,
    token
  )
}
object WebsterSpanishDefinitionEntry {
  implicit val reads: Reads[WebsterSpanishDefinitionEntry] = (
    (JsPath \ "meta").read[WebsterMeta] and
      (JsPath \ "hwi").read[WebsterHeadwordInfo] and
      (JsPath \ "fl").read[String] and
      (JsPath \ "ins")
        .readNullable[Seq[WebsterInflection]](WebsterInflection.helper.readsSeq) and
      (JsPath \ "def")
        .read[Seq[WebsterDefinition]](WebsterDefinition.helper.readsSeq) and
      (JsPath \ "dros").readNullable[Seq[WebsterDefinedRunOnPhrase]](
        WebsterDefinedRunOnPhrase.helper.readsSeq
      ) and
      (JsPath \ "shortdef").read[Seq[String]](Reads.seq[String])
  )(WebsterSpanishDefinitionEntry.apply _)
  implicit val writes: Writes[WebsterSpanishDefinitionEntry] =
    Json.writes[WebsterSpanishDefinitionEntry]
  implicit val helper: JsonSequenceHelper[WebsterSpanishDefinitionEntry] =
    new JsonSequenceHelper[WebsterSpanishDefinitionEntry]
}
