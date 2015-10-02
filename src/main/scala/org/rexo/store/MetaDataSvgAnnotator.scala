package org.rexo.store

import edu.umass.cs.iesl.xml_annotator.Annotator
import edu.umass.cs.iesl.xml_annotator.Annotator._
import edu.umass.cs.iesl.xml_annotator.Annotator.SegmentCon
import edu.umass.cs.iesl.xml_annotator.Annotator.Single
import org.rexo.extraction.NewHtmlTokenizationSvg
import org.rexo.extra.types.{Token, Sequence}
import scala.collection.mutable.ArrayBuffer
import java.util.logging.{Logger, Level}

/**
 * Created by klimzaporojets on 11/24/14.
 */
object MetaDataSvgAnnotator {
  val logger = Logger.getLogger("MetaDataSvgAnnotator")

  val bodyAnnotations:Map[String, (String,Char)] =
    Map("paragraph"->("paragraph", 'p'), "section-marker"->("section-marker", 's'),
              "table-marker"->("table-marker", 't'), "figure-marker"->("figure-marker", 'f'))

  val referenceAnnotations:Map[String, (String,Char)] = Map("biblio-"-> ("biblio-marker", 'b'))


  val referenceLabels:Map[String,String] = Map("inside" -> "-I", "begin" -> "-B")
  val bodyLabels:Map[String,String] = Map("inside" -> "-inside", "begin" -> "-begin")

  private def getAnnotations(annoData:(String, (String,Char), Map[String, String]), annotator:Annotator,
                              labelNames:Map[String,String]):Annotator = {
    val resAnnot = { 
      val lineBIndexSet = annotator.getBIndexSet(Single(SegmentCon("line")))
      val table = lineBIndexSet.flatMap(index => {
        val elements = annotator.getElements("line")(index)
        val (blockIndex, charIndex) = annotator.mkIndexPair(index)
        val annotatorPage =  NewHtmlTokenizationSvg.getPageNumber(elements.get(blockIndex).get, annotator.getDom()) + 1
        if(annoData._3.contains(blockIndex + "_" + annotatorPage)){
          val valLabel = annoData._3.get(blockIndex + "_" + annotatorPage).get
//          if(valLabel.contains("-inside"))
          if(valLabel.contains(labelNames("inside")))
            Some(index -> I)
//          else if (valLabel.contains("-begin"))
          else if (valLabel.contains(labelNames("begin")))
            Some(index -> B(annoData._2._2))
          else if (valLabel.contains("last"))
            Some(index -> L)
          else if (valLabel.contains("unique"))
            Some(index -> U(annoData._2._2))
          else if (valLabel.contains("other"))
            Some(index -> O)
          else throw new Exception("Error in svg annotator, no associated label found")
        }
        else {
//          println(elements.map(_._2.getText).mkString(" "))
          None
        }
      }).toMap
      annotator.annotate(List(annoData._2._1 -> annoData._2._2), Single(SegmentCon("line")), table)
    }

//    println(resAnnot.getTextSeq("biblio-marker").map(_._2).mkString(" "))

    resAnnot
  }

  private def getParagraphIds(lines:List[(String,String)], paragraphId:Int, beginLabel:String):List[(Int,String,String)] = {

    def loop(acc: List[(Int,String,String)], lines:List[(String,String)], paragraphId:Int, beginLabel:String):List[(Int,String,String)] = {

      val current = {
        if(lines.head._2.contains(beginLabel)) {
          paragraphId+1
        } else {
          paragraphId
        }
      }

      if(lines.size == 1) {
        ((current, lines.head._1, lines.head._2)::acc).reverse
      } else {
        loop((current, lines.head._1, lines.head._2)::acc, lines.tail, current,beginLabel)
      }
    }

    loop(List(), lines, paragraphId, beginLabel)

  }

  private def trimOthersInList(listTags:List[(Int,String,String)]):List[(Int,String,String)] = {
    if(listTags.isEmpty) listTags
    else {
      val rest = if (listTags.size > 1) listTags.tail else List()
      val head = listTags.head
      if (head._3.equals("other"))
        trimOthersInList(rest)
      else listTags
    }
  }

  private def trimOthers(parIdsGrouped:Map[Int,List[(Int,String,String)]]):Map[Int,List[(Int,String,String)]] =
    parIdsGrouped.map{entry => (entry._1 -> trimOthersInList(entry._2.reverse).reverse)}

  private def hasSameLine(token1:Token, token2:Token):Boolean =
    token1.getNumericProperty("lineNum").asInstanceOf[Int] == token2.getNumericProperty("lineNum").asInstanceOf[Int]

  //ORIGINAL RECURSIVE getSegmentRawType
//  private def getSegmentRawType(tokens:ArrayBuffer[Token], labelId:Int, labels:Sequence, perLine:Boolean):Map[String,String]= {
//    val currentToken:Token = tokens.head
//    val tokenBlockId:Int = currentToken.getProperty("divElement").asInstanceOf[scala.Tuple2[Any, Any]]._1.toString.toInt
//    val pageNumber:Int = currentToken.getProperty("pageNum").asInstanceOf[Double].toInt
//    if (tokens.size>1) {
//      val tokTail = tokens.tail
//      if(perLine && hasSameLine(currentToken, tokTail.head)){
//        return Map((tokenBlockId + "_" + pageNumber).toString -> labels.get(labelId).toString) ++ (getSegmentRawType(tokTail, labelId, labels, perLine))
//      } else {
//        return Map((tokenBlockId + "_" + pageNumber).toString -> labels.get(labelId).toString) ++ (getSegmentRawType(tokTail, labelId+1, labels, perLine))
//      }
//    } else {
//      return Map((tokenBlockId + "_" + pageNumber).toString -> labels.get(labelId).toString)
//    }
//  }

  //TAIL RECURSIVE getSegmentRawType
  private def getSegmentRawType(tokens:Iterable[Token], labelId:Int, labels:Sequence, perLine:Boolean): Map[String,String] = {
    require(!tokens.isEmpty)
    def loop(mapAcc: Map[String, String])(toks: List[Token], lId: Int): Map[String, String] = {
      val currentToken:Token = toks.head
      val tokenBlockId:Int = currentToken.getProperty("divElement").asInstanceOf[(Any, Any)]._1.toString.toInt
      val pageNumber:Int = currentToken.getProperty("pageNum").asInstanceOf[Double].toInt
      val tokTail = toks.tail
      val pair = (tokenBlockId + "_" + pageNumber) -> labels.get(lId).toString
      val newLId = if(toks.length > 1 && perLine && hasSameLine(currentToken, tokTail.head)) lId else lId + 1
//      println(s"labels(${tokenBlockId}_$pageNumber)=${labels.get(lId)}: ${currentToken.getText}")

      /* Known bug where the last column in a two-column pdf is cut off, don't want to throw an exception if newLId == labels.size */
//      if(newLId == labels.size && toks.size > 1)
//        logger.log(Level.WARNING, s"Document cutoff: part of this document was probably lost (token: `${currentToken.getText}', page: ${pageNumber}, toks left: ${toks.size}: ${tokTail.map("`" + _.text + "'").mkString(" ")})")
      if (toks.size > 1 && newLId != labels.size)
        loop(mapAcc + pair)(tokTail, newLId)
      else mapAcc + pair
    }
    loop(Map[String, String]())(tokens.toList, labelId)
  }

  private def insertLast(listTags:List[(Int,String,String)]):List[(Int,String,String)] =
  {
    if(listTags.isEmpty) listTags
    else if(listTags.size == 1) List((listTags.head._1,listTags.head._2, "unique"))
    else {
      val last = listTags.reverse.head
      val prev_last = listTags.reverse.tail.reverse
      prev_last :+(last._1, last._2, "last")
    }
  }

  def annotateRec(annotations:List[(String, (String,Char), Map[String, String])], annotator:Annotator, labelNames:Map[String,String]):Annotator = {
    if(annotations.size==1)
      getAnnotations(annotations.head, annotator, labelNames)
    else
      getAnnotations(annotations.head, annotateRec(annotations.tail, annotator,labelNames), labelNames)
  }

  def annotateReferences(segmentation: NewHtmlTokenizationSvg, labels:Sequence, annotator:Annotator): Annotator =
  {
    val bIndexRaw = getSegmentRawType(segmentation.tokens, 0, labels, true)

    val annotations = referenceAnnotations.map{anno=>
      val annotation = anno._1
      val blockIdxToRawType = {
        annotator.getBIndexSet(Single(SegmentCon("line"))).map{index =>
          val elements = annotator.getElements("line")(index)
          val (blockIndex, charIndex) = annotator.mkIndexPair(index)
          val annotatorPage =  NewHtmlTokenizationSvg.getPageNumber(elements.get(blockIndex).get, annotator.getDom()) + 1
          val bKey = blockIndex + "_" + annotatorPage
          if(bIndexRaw.contains(bKey) && bIndexRaw(bKey).contains(annotation)) {
//            println(bIndexRaw(bKey) + ": " + elements.map(_._2.getText))
            (bKey, bIndexRaw(bKey))
          }
          else {
            if(bIndexRaw.contains(bKey)) {
//              println(s"other (${bIndexRaw.contains(bKey)}): " + elements.map(_._2.getText))
//              println("want: " + anno._1 + " have: " + bIndexRaw(bKey))
            }
            (bKey, "other")
          }
        }
      }.toList



      val parIds:List[(Int,String,String)] = getParagraphIds(blockIdxToRawType, 0, "-B")

      val parIdsGrouped:Map[Int,List[(Int,String,String)]] = parIds.groupBy(_._1)

      val parIdsTrimmed = trimOthers(parIdsGrouped)

      val lastInserted = parIdsTrimmed.map{entry => (entry._1 -> insertLast(entry._2))}

      val hashAnnotations = lastInserted.values.map{ value =>
        value.map{listTags => (listTags._2 -> listTags._3)}}.flatten.toMap

      (anno._1, anno._2, hashAnnotations)
    }
    annotateRec(annotations.toList, annotator, referenceLabels)
  }

  def annotateBody(segmentation: NewHtmlTokenizationSvg, labels:Sequence, annotator: Annotator): Annotator =
  {
    val bIndexRaw = getSegmentRawType(segmentation.tokens, 0, labels, false)

    val annotations = bodyAnnotations.map{anno=>
      val annotation = anno._1 //print(anno._1)

      val blockIdxToRawType = {

        val lineBIndexSet = annotator.getBIndexSet(Single(SegmentCon("line")))
        lineBIndexSet.toList.map { case index =>
          val elements = annotator.getElements("line")(index)
          val (blockIndex, charIndex) = annotator.mkIndexPair(index)

          val annotatorPage =  NewHtmlTokenizationSvg.getPageNumber(elements.get(blockIndex).get, annotator.getDom()) + 1

          if(bIndexRaw.contains(blockIndex + "_" + annotatorPage) &&
                bIndexRaw.get(blockIndex + "_" + annotatorPage).get.contains(annotation) ){
            (blockIndex + "_" + annotatorPage, bIndexRaw.get(blockIndex + "_" + annotatorPage).get)
          }
          else{
            (blockIndex + "_" + annotatorPage, "other")
          }
        }
      }

      val parIds = getParagraphIds(blockIdxToRawType, 0, "-begin")

      val parIdsGrouped = parIds.groupBy(_._1)

      val parIdsTrimmed = trimOthers(parIdsGrouped)

      val lastInserted = parIdsTrimmed.map{entry=> entry._1 -> insertLast(entry._2)}

      val hashAnnotations = lastInserted.values.flatMap{value =>
        value.map{listTags => listTags._2 -> listTags._3 }
      }.toMap

      (anno._1, anno._2, hashAnnotations)
    }

    annotateRec(annotations.toList, annotator, bodyLabels)
  }
}
