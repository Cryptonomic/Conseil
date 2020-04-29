package tech.cryptonomic.conseil.api.routes.platform.data

import cats.Id
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryResponse
import tech.cryptonomic.conseil.common.util.Conversion

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
