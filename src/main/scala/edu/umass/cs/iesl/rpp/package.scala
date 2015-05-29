package edu.umass.cs.iesl

/**
 * @author Kate Silverstein 
 *         created on 5/22/15
 */
package object rpp {
  implicit class StringBuilderExtras(sb: StringBuilder) {
    def levelIndent(level: Int): String = (0 until level).map(_ => "\t").mkString("")
    def appendLine(line: String)(implicit level: Int = 0): Unit = sb.append(levelIndent(level) + line + "\n")
  }

}