package tech.cryptonomic.conseil.api.routes.platform.data.tezos

import java.sql.Timestamp

import tech.cryptonomic.conseil.api.metadata.MetadataService
import tech.cryptonomic.conseil.common.config.MetadataConfiguration
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.OutputType.OutputType
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes.DataType
import tech.cryptonomic.conseil.common.metadata.EntityPath

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

object TezosDataTypes {

  import cats.implicits._
  import io.scalaland.chimney.dsl._

  /** Replaces timestamp represented as Long in predicates with one understood by the SQL */
  private def replaceTimestampInPredicatesAndSnapshot(path: EntityPath, query: Query, metadataService: MetadataService)(
      implicit executionContext: ExecutionContext
  ): Future[Query] = {
    val fetchedAttributes = metadataService.getTableAttributesWithoutUpdatingCache(path)
    val newSnapshot: Future[Option[Snapshot]] =
      fetchedAttributes.map { maybeAttributes =>
        query.snapshot.flatMap { snapshot =>
          maybeAttributes.flatMap { attributes =>
            attributes.find(_.name == snapshot.field).map {
              case attribute if attribute.dataType == DataType.DateTime =>
                snapshot.copy(value = new Timestamp(snapshot.value.toString.toLong).toString)
              case _ => snapshot
            }
          }
        }
      }

    val newPredicates: Future[List[Predicate]] =
      fetchedAttributes.map { maybeAttributes =>
        query.predicates.flatMap { predicate =>
          maybeAttributes.flatMap { attributes =>
            attributes.find(_.name == dropAggregationPrefixes(predicate.field)).map {
              case attribute if attribute.dataType == DataType.DateTime =>
                predicate.copy(set = predicate.set.map(x => new Timestamp(x.toString.toLong).toString))
              case _ => predicate
            }
          }.toList
        }
      }
    (newPredicates, newSnapshot).mapN {
      case (pred, snap) =>
        query.copy(predicates = pred, snapshot = snap)
    }
  }

  /** Helper method for finding fields used in query that don't exist in the database */
  private def findNonExistingFields(
      query: Query,
      path: EntityPath,
      metadataService: MetadataService
  ): List[QueryValidationError] = {
    val fields = query.fields.map('query -> _.field) :::
          query.predicates.map(predicate => 'predicate -> dropAggregationPrefixes(predicate.field)) :::
          query.orderBy.map(orderBy => 'orderBy -> dropAggregationPrefixes(orderBy.field)) :::
          query.aggregation.map('aggregation -> _.field)

    fields.map {
      case (source, field) => (metadataService.exists(path.addLevel(field)), source, field)
    }.collect {
      case (false, 'query, field) => InvalidQueryField(field)
      case (false, 'predicate, field) => InvalidPredicateField(field)
      case (false, 'orderBy, field) => InvalidOrderByField(field)
      case (false, 'aggregation, field) => InvalidAggregationField(field)
    }
  }

  /** Drops aggregation prefixes from field name */
  private def dropAggregationPrefixes(fieldName: String): String =
    dropAnyOfPrefixes(fieldName, AggregationType.prefixes)

  /** Helper method for dropping prefixes from given string */
  private def dropAnyOfPrefixes(str: String, prefixes: List[String]): String =
    prefixes.collectFirst {
      case prefixToBeDropped if str.startsWith(prefixToBeDropped) => str.stripPrefix(prefixToBeDropped)
    }.getOrElse(str)

  /** Helper method for finding fields with invalid types in aggregation */
  private def findInvalidAggregationTypeFields(query: Query, path: EntityPath, metadataService: MetadataService)(
      implicit ec: ExecutionContext
  ): Future[List[InvalidAggregationFieldForType]] =
    query.aggregation.traverse { aggregation =>
      metadataService.getTableAttributesWithoutUpdatingCache(path).map { attributesOpt =>
        attributesOpt.flatMap { attributes =>
          attributes
            .find(_.name == aggregation.field)
            .map(attribute => canBeAggregated(attribute.dataType)(aggregation.function) -> aggregation.field)
        }
      }
    }.map(_.flatten.collect { case (false, fieldName) => InvalidAggregationFieldForType(fieldName) }.toList)

  /** Helper method for finding if queries does not contain filters on key fields or datetime fields */
  private def findInvalidPredicateFilteringFields(query: Query, path: EntityPath, metadataService: MetadataService)(
      implicit ec: ExecutionContext
  ): Future[List[InvalidPredicateFiltering]] = {
    val limitedQueryEntity = metadataService
      .getEntities(path.up)
      .toList
      .flatten
      .find(_.name == path.entity)
      .flatMap(_.limitedQuery)
      .getOrElse(false)
    if (limitedQueryEntity) {
      query.predicates.traverse { predicate =>
        metadataService.getTableAttributesWithoutUpdatingCache(path).map { attributesOpt =>
          attributesOpt.flatMap { attributes =>
            attributes.find(_.name == predicate.field)
          }.toList
        }
      }.map(attributes => attributes.flatten.flatMap(_.doesPredicateContainValidAttribute))
    } else {
      successful(List.empty)
    }
  }

  /** Helper method for query fields validation */
  private def findInvalidQueryFieldFormats(
      query: Query,
      path: EntityPath,
      metadataService: MetadataService
  ): List[InvalidQueryFieldFormatting] = {
    val columns = metadataService.getTableAttributes(path).toList.flatten
    query.fields.filterNot {
      case SimpleField(_) => true
      case FormattedField(field, FormatType.datePart, _) =>
        columns.exists(column => column.name == field && column.dataType == DataType.DateTime)
      case _ => false
    }.map(field => InvalidQueryFieldFormatting(s"Field ${field.field} does not have correct type to be formatted."))
  }

  /** Class representing query got through the REST API */
  case class ApiQuery(
      fields: Option[List[Field]],
      predicates: Option[List[ApiPredicate]],
      orderBy: Option[List[QueryOrdering]],
      limit: Option[Int],
      output: Option[OutputType],
      aggregation: Option[List[ApiAggregation]],
      snapshot: Option[Snapshot] = None
  ) {

    /** Method which validates query fields */
    def validate(entity: EntityPath, metadataService: MetadataService, metadataConfiguration: MetadataConfiguration)(
        implicit ec: ExecutionContext
    ): Future[Either[List[QueryValidationError], Query]] = {

      val patchedPredicates = predicates.getOrElse(List.empty).map(_.toPredicate)
      val query = this
        .into[Query]
        .withFieldConst(_.fields, fields.getOrElse(List.empty))
        .withFieldConst(_.predicates, patchedPredicates)
        .withFieldConst(_.orderBy, orderBy.getOrElse(List.empty))
        .withFieldConst(_.limit, limit.getOrElse(defaultLimitValue))
        .withFieldConst(_.output, output.getOrElse(OutputType.json))
        .withFieldConst(_.aggregation, aggregation.toList.flatten.map(_.toAggregation))
        .withFieldConst(_.temporalPartition, metadataConfiguration.entity(entity).flatMap(_.temporalPartition))
        .transform

      (
        successful(findNonExistingFields(query, entity, metadataService)),
        findInvalidAggregationTypeFields(query, entity, metadataService),
        findInvalidPredicateFilteringFields(query, entity, metadataService),
        successful(findInvalidQueryFieldFormats(query, entity, metadataService)),
        successful(findInvalidSnapshotFields(query, entity, metadataConfiguration)),
        replaceTimestampInPredicatesAndSnapshot(entity, query, metadataService)
      ).mapN {
        (
            invalidNonExistingFields,
            invalidAggregationFieldForTypes,
            invalidPredicateFilteringFields,
            invalidQueryFieldFormats,
            invalidSnapshotField,
            updatedQuery
        ) =>
          invalidNonExistingFields :::
              invalidAggregationFieldForTypes :::
              invalidPredicateFilteringFields :::
              invalidQueryFieldFormats :::
              invalidSnapshotField match {
            case Nil => Right(updatedQuery)
            case wrongFields => Left(wrongFields)
          }
      }
    }
  }

}
