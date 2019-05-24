package tech.cryptonomic.conseil.routes.openapi

import tech.cryptonomic.conseil.generic.chain.DataTypes.QueryResponse
import tech.cryptonomic.conseil.util.Conversion
import cats.Id

/** Object containing implicit conversions to CSV format */
object CsvConversions {

  /** Type alias representing CSV string */
  type CsvString = String

  /** Implicit instance of the conversion from List[QueryResponse] to CsvString */
  implicit val queryToCsv: Conversion[Id, List[QueryResponse], CsvString] = (from: List[QueryResponse]) => {
    val headers = from.headOption.map { headerList =>
      headerList.keys.mkString(",")
    }.getOrElse("")
    val values = from.map { values =>
      values.values.map {
        case Some(value) => value
        case None => "null"
      }.mkString(",")
    }
    (headers :: values).mkString("\n")
  }
}
