/* NEST (New Scala Test)
 * Copyright 2007-2010 LAMP/EPFL
 */

package scala.tools

import nsc.io.{ Path, Process, Directory }
import util.{ PathResolver }
import nsc.Properties.{ propOrElse, propOrNone, propOrEmpty }

package object partest { 
  import nest.NestUI

  def basename(name: String): String = Path(name).stripExtension
  def resultsToStatistics(results: Iterable[(_, Int)]): (Int, Int) = {
    val (files, failures) = results map (_._2 == 0) partition (_ == true)
    (files.size, failures.size)
  }
  
  object PartestDefaults {
    import nsc.Properties._
    private def wrapAccessControl[T](body: => Option[T]): Option[T] =
      try body catch { case _: java.security.AccessControlException => None }
    
    def testRootName  = propOrNone("scalatest.root")
    def srcDirName    = propOrElse("partest.srcdir", "files")
    def testRootDir   = testRootName map (x => Directory(x))

    def classPath   = PathResolver.Environment.javaUserClassPath    // XXX

    def javaCmd     = propOrElse("scalatest.javacmd", "java")
    def javacCmd    = propOrElse("scalatest.javac_cmd", "javac")
    def javaOpts    = propOrElse("scalatest.java_opts", "")
    def scalacOpts  = propOrElse("scalatest.scalac_opts", "-deprecation")

    def testBuild   = propOrNone("scalatest.build")
    def errorCount  = propOrElse("scalatest.errors", "0").toInt
    def numActors   = propOrElse("scalatest.actors", "8").toInt
    def poolSize    = wrapAccessControl(propOrNone("actors.corePoolSize"))

    def timeout     = "1200000"
  }
  
  def vmArgString = {    
    val str = Process.javaVmArguments mkString " "
    "Java VM started with arguments: '%s'" format str
  }
  
  def allPropertiesString = {
    import collection.JavaConversions._
    System.getProperties.toList.sorted map { case (k, v) => "%s -> %s\n".format(k, v) } mkString
  }
  
  def showAllJVMInfo {
    NestUI.verbose(vmArgString)
    NestUI.verbose(allPropertiesString)
  }
   
  def isPartestDebug = List("partest.debug", "scalatest.debug") map propOrEmpty contains "true"
}