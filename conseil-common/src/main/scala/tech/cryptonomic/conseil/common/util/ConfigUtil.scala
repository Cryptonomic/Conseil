package tech.cryptonomic.conseil.common.util

import com.typesafe.scalalogging.LazyLogging

object ConfigUtil {

  object Csv extends LazyLogging {
    import java.sql.Timestamp
    import java.time.Instant
    import java.time.format.DateTimeFormatter

    import kantan.codecs.strings.StringDecoder
    import kantan.csv._
    import shapeless._
    import shapeless.ops.hlist._
    import slick.lifted.{AbstractTable, TableQuery}

    /** Trims if passed a String value, otherwise returns the value unchanged */
    object Trimmer extends Poly1 {
      implicit val stringTrim = at[String] { _.trim }
      implicit def noop[T] = at[T] { identity }
    }

    implicit val timestampDecoder: CellDecoder[java.sql.Timestamp] = (e: String) => {
      val format = DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT'Z (zzzz)")
      StringDecoder
        .makeSafe("Instant")(s => Instant.from(format.parse(s)))(e)
        .map(Timestamp.from)
        .left
        .map(x => DecodeError.TypeError(x.message))
    }

    /** Reads a CSV file with tabular data, returning a set of rows to save in the database.
      * This overload will find the source file based on the slick table and the network provided.
      *
      * @tparam T is the slick table for which we want to parse rows objects
      * @tparam H a generic representation used to automatically map the row from values
      * @param table used to find which table rows are requested as output
      * @param network will be used to find the source csv to parse in the search path
      * @param separator defaults to ',', will be used to split the csv tokens
      * @param hd implicitly provide a way to read field types from the csv headers
      * @param g type assertion that a Generic HList can be created from the table row shape
      * @param m type assertion to state the the HList can be used to map the contents of fields,
      *          via the polymorphic [[Trimmer]] function
      * @return the list of parsed rows for the specific table
      */
    def readTableRowsFromCsv[T <: AbstractTable[_], H <: HList](
        table: TableQuery[T],
        network: String,
        separator: Char = ','
    )(
        implicit
        hd: HeaderDecoder[T#TableElementType],
        g: Generic.Aux[T#TableElementType, H],
        m: Mapper.Aux[Trimmer.type, H, H]
    ): Option[List[T#TableElementType]] =
      readRowsFromCsv[T#TableElementType, H](
        csvSource = getClass.getResource(s"/${table.baseTableRow.tableName}/$network.csv"),
        separator
      )

    /** Reads a CSV file with tabular data, returning a set of rows to save in the database
      * This overload will use the provided csv file
      *
      * To call this method you will need to provide the expected type parameters explicitly,
      * otherwise the generic method won't be able to fill out the pieces.
      * To obtain the specific HList type for the [[H]] constraint, we need to pull a "trick",
      * by using shapeless generic facilities:
      *
      * Creating first the [[shapeless.Generic\[Row]]] instance and then accessing its [[Repr]]
      * type attribute, which will be the specific [[HList]]'s recursive subtype needed to
      * represent the specific [[Row]] fields.
      * e.g.
      * {{{
      * import shapeless._
      *
      * val genericRow = Generic[MyRow]
      *
      * val rowList: List[MyRow] = ConfigUtil.Csv.readTableRowsFromCsv[MyRow, genericRow.Repr](csvUrl)
      *
      * }}}
      *
      * @tparam Row is the type of the parsed objects
      * @tparam H a generic representation used to automatically map the row from values
      * @param csvSource points to the csv file to parse
      * @param separator defaults to ',', will be used to split the csv tokens
      * @param hd implicitly provide a way to read field types from the csv headers
      * @param g type assertion that a Generic HList can be created from the table row shape
      * @param m type assertion to state the the HList can be used to map the contents of fields,
      *          via the polymorphic [[Trimmer]] function
      * @return the list of parsed rows
      */
    def readRowsFromCsv[Row, H <: HList](
        csvSource: java.net.URL,
        separator: Char = ','
    )(
        implicit
        hd: HeaderDecoder[Row],
        g: Generic.Aux[Row, H],
        m: Mapper.Aux[Trimmer.type, H, H]
    ): Option[List[Row]] = {
      import kantan.csv._
      import kantan.csv.ops._

      /* Uses a Generic to transform the instance into an HList, maps over it and convert it back into the case class */
      def trimStringFields[C](c: C)(implicit g: Generic.Aux[C, H]): C = {
        val hlist = g.to(c)
        val trimmed = hlist.map(Trimmer)
        g.from(trimmed)
      }

      Option(csvSource).map { validSource =>
        val reader: CsvReader[ReadResult[Row]] =
          validSource.asCsvReader[Row](rfc.withHeader.withCellSeparator(separator))

        // separates List[Either[L, R]] into List[L] and List[R]
        val (errors, rows) = reader.toList.foldRight((List.empty[ReadError], List.empty[Row]))(
          (acc, pair) => acc.fold(l => (l :: pair._1, pair._2), r => (pair._1, trimStringFields(r) :: pair._2))
        )

        if (errors.nonEmpty) {
          val messages = errors.map(_.getMessage).mkString("\n", "\n", "\n")
          logger.error("Error while reading registered source file {}: {}", csvSource.toExternalForm(), messages)
        }

        rows
      }
    }

  }

}
