/*
Copyright 2020 The Regents of the University of California (Regents)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package chiseltest.internal

import java.io.File

import firrtl.AnnotationSeq
import firrtl.options.TargetDirAnnotation

import scala.collection.mutable

class FailedExpectException(val message: String, val failedCodeStackDepth: Int) extends Exception(message)

/** Interface into the testing environment, like ScalaTest
  */
trait TestEnvInterface {
  protected val batchedFailures: mutable.ArrayBuffer[Exception] = new mutable.ArrayBuffer

  def topFileName: Option[String]

  /**
    * Will add a TargetDirAnnotation with defaultDir with "test_run_dir" path prefix to the annotations
    * if there is not a TargetDirAnnotation already present
    *
    * @param defaultDir     a default directory
    * @param annotationSeq  annotations to add it to, unless one is already there
    * @return
    */
  def addDefaultTargetDir(defaultDir: String, annotationSeq: AnnotationSeq): AnnotationSeq = {
    if (annotationSeq.exists { x => x.isInstanceOf[TargetDirAnnotation] }) {
      annotationSeq
    } else {
      val target = TargetDirAnnotation("test_run_dir" + File.separator + defaultDir)
      AnnotationSeq(annotationSeq ++ Seq(target))
    }
  }

  /** Logs a tester failure at this point.
    * Failures queued until the next checkpoint.
    */
  def testerFail(msg: String): Unit = {
    batchedFailures += new FailedExpectException(msg, 4)
  }

  protected def getExpectDetailedTrace(trace: Seq[StackTraceElement], inFile: String): String = {
    val fullTrace = Context().backend.getParentTraceElements ++ trace

    // In the threading case, this needs to be overridden to trace through parent threads
    val lineNumbers = fullTrace.collect {
      case ste if ste.getFileName == inFile => ste.getLineNumber
    }.mkString(", ")
    if (lineNumbers.isEmpty) {
      s" (no lines in $inFile)"
    } else {
      s" (lines in $inFile: $lineNumbers)"
    }
  }

  /** Expect a specific value on a wire, calling testerFail if the expectation isn't met.
    * Failures queued until the next checkpoint.
    */
  def testerExpect(expected: Any, actual: Any, signal: String, msg: Option[String]): Unit = {
    if (expected != actual) {
      val appendMsg = msg match {
        case Some(_) => s": $msg"
        case _ => ""
      }

      val trace = new Throwable
      val expectStackDepth = trace.getStackTrace.indexWhere(ste =>
        ste.getClassName == "chiseltest.package$testableData" && ste.getMethodName == "expect")
      require(expectStackDepth != -1,
        s"Failed to find expect in stack trace:\r\n${trace.getStackTrace.mkString("\r\n")}")

      val trimmedTrace = trace.getStackTrace.drop(expectStackDepth + 2)
      val detailedTrace = topFileName.map(getExpectDetailedTrace(trimmedTrace.toSeq, _)).getOrElse("")

      val message = s"$signal=$actual did not equal expected=$expected$appendMsg$detailedTrace"
      val stackIndex = expectStackDepth + 1
      batchedFailures += new FailedExpectException(message, stackIndex)
    }
  }

  /** If there are any failures, reports them and end the test now.
    */
  def checkpoint(): Unit = {
    // TODO: report multiple exceptions simultaneously
    for (failure <- batchedFailures) {
      throw failure
    }
  }
}