package akka.stream.alpakka.xlsx

import java.util.zip.ZipFile

import akka.stream.Materializer
import akka.stream.alpakka.xml.scaladsl.XmlParsing
import akka.stream.alpakka.xml.{EndElement, ParseEvent, StartElement}
import akka.stream.contrib.ZipInputStreamSource.ZipEntryData
import akka.stream.scaladsl.{Keep, Sink, Source, StreamConverters}
import akka.util.ByteString

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Try

object StyleStreamer {

  private final val EntryName = "xl/styles.xml"
  private val defaultSink = Sink.fold[Map[Int, Int], (Int, Int)](Map.empty)((v1, v2) => v1 + v2)


  def readStyles(zipFile: ZipFile)(implicit materializer: Materializer): Future[Map[Int, Int]] = {
    readStyles(zipFile, defaultSink)
  }

  def readStyles(
      zipFile: ZipFile,
      mapSink: Sink[(Int, Int), Future[Map[Int, Int]]]
  )(implicit materializer: Materializer): Future[Map[Int, Int]] = {
    Option(zipFile.getEntry(EntryName)) match {
      case Some(entry) => read(StreamConverters.fromInputStream(() => zipFile.getInputStream(entry)), mapSink)
      case None        => Source.empty[(Int, Int)].toMat(mapSink)(Keep.right).run()
    }
  }


  def readStyles(source: Iterable[(ZipEntryData, ByteString)])(implicit materializer: Materializer): Future[Map[Int, Int]] = {
    readStyles(source, defaultSink)
  }

  def readStyles(
      source: Iterable[(ZipEntryData, ByteString)],
      mapSink: Sink[(Int, Int), Future[Map[Int, Int]]]
  )(implicit materializer: Materializer): Future[Map[Int, Int]] = {
    read(
      Source.fromIterator(() => source.collect { case (zipEntry, bytes) if zipEntry.name == EntryName => bytes }.iterator),
      mapSink
    )
  }


  private def read(
      inputSource: Source[ByteString, _],
      mapSink: Sink[(Int, Int), Future[Map[Int, Int]]]
  )(implicit materializer: Materializer) = {
    inputSource
      .via(XmlParsing.parser)
      .statefulMapConcat[(Int, Int)](() => {
        val numFmtMapping: mutable.Map[Int, Int] = mutable.Map.empty
        var insideCellXfs: Boolean               = false
        var insideNumFmts: Boolean               = false
        var count                                = 0
        (data: ParseEvent) =>
          data match {
            case StartElement("numFmts", _, _, _, _) =>
              insideNumFmts = true
              Nil
            case StartElement("numFmt", attrs, _, _, _) =>
              val numFmtId = attrs.find(_.name == "numFmtId").flatMap(a => Try(Integer.parseInt(a.value)).toOption)
              val numFmtString = attrs.find(_.name == "formatCode").map(_.value)

              val isDateCode = DateParser.isADateFormat(numFmtId.getOrElse(0), numFmtString)

              numFmtId match {
                case Some(numId) if isDateCode =>
                  numFmtMapping += (numId -> 14)
                case _ =>
              }
              Nil
            case EndElement("numFmt") => // ignored
              Nil
            case EndElement("numFmts") if insideNumFmts =>
              insideNumFmts = false
              Nil
            case StartElement("cellXfs", _, _, _, _) =>
              insideCellXfs = true
              Nil
            case StartElement("xf", attrs, _, _, _) if insideCellXfs =>
              val numFmtIdValue =
                attrs
                  .find(_.name == "numFmtId")
                  .flatMap(a => Try(Integer.valueOf(a.value).intValue()).toOption)
                  .getOrElse(0)
              val applyNumberFormatValue = attrs
                .find(_.name == "applyNumberFormat")
                .flatMap(a => Try(Integer.valueOf(a.value).intValue()).toOption)
                .forall(_ == 1)

              val overwriteFmtIdValue = numFmtMapping.get(numFmtIdValue)
              val data = {
                if (applyNumberFormatValue) (count -> overwriteFmtIdValue.getOrElse(numFmtIdValue)) :: Nil
                else Nil
              }
              count += 1
              data
            case EndElement("xf") if insideCellXfs => // ignored
              Nil
            case EndElement("cellXfs") =>
              insideCellXfs = false
              Nil
            case _ => Nil
          }
      })
      .toMat(mapSink)(Keep.right)
      .run()
  }

}
