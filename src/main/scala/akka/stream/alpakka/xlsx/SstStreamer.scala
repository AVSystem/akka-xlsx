package akka.stream.alpakka.xlsx

import java.util.zip.ZipFile

import akka.stream.Materializer
import akka.stream.alpakka.xml.scaladsl.XmlParsing
import akka.stream.alpakka.xml.{Characters, EndElement, ParseEvent, StartElement}
import akka.stream.contrib.ZipInputStreamSource.ZipEntryData
import akka.stream.scaladsl.{Keep, Sink, Source, StreamConverters}
import akka.util.ByteString

import scala.concurrent.{ExecutionContext, Future}

object SstStreamer {

  private final val EntryName = "xl/sharedStrings.xml"
  private[xlsx] val defaultSink = Sink.seq[(Int, String)].mapMaterializedValue(_.map(_.toMap)(ExecutionContext.fromExecutor(_.run())))


  def readSst(zipFile: ZipFile)(implicit materializer: Materializer): Future[Map[Int, String]] = {
    readSst(zipFile, defaultSink)
  }

  def readSst(
      zipFile: ZipFile,
      mapSink: Sink[(Int, String), Future[Map[Int, String]]]
  )(implicit materializer: Materializer): Future[Map[Int, String]] = {
    Option(zipFile.getEntry(EntryName))
      .map(entry => read(StreamConverters.fromInputStream(() => zipFile.getInputStream(entry)), mapSink))
      .getOrElse(Future.successful(Map.empty))
  }

  def readSst(source: Iterable[(ZipEntryData, ByteString)])(implicit materializer: Materializer): Future[Map[Int, String]] = {
    readSst(source, defaultSink)
  }

  def readSst(
      source: Iterable[(ZipEntryData, ByteString)],
      mapSink: Sink[(Int, String), Future[Map[Int, String]]]
  )(implicit materializer: Materializer): Future[Map[Int, String]] = {
    read(
      Source.fromIterator(() => source.iterator.collect { case (zipEntry, bytes) if zipEntry.name == EntryName => bytes }),
      mapSink
    )
  }


  private def read(
      inputSource: Source[ByteString, _],
      mapSink: Sink[(Int, String), Future[Map[Int, String]]]
  )(implicit materializer: Materializer) = {
    inputSource
      .via(XmlParsing.parser)
      .statefulMapConcat[(Int, String)](() => {
        var count                              = 0
        var si                                 = false
        var lastContent: Option[StringBuilder] = None

        data: ParseEvent => data match {
          case StartElement("si", _, _, _, _) =>
            si = true
            Nil
          case EndElement("si") =>
            val sst = lastContent match {
              case Some(builder) =>
                lastContent = None
                (count, builder.toString()) :: Nil
              case None =>
                Nil
            }
            si = false
            count += 1
            sst
          case Characters(text) if si =>
            lastContent match {
              case Some(builder) => builder.append(text)
              case None => lastContent = Some(new StringBuilder().append(text))
            }
            Nil
          case _ => Nil
        }
      })
      .toMat(mapSink)(Keep.right)
      .run()
  }

}
