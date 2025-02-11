/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.diagnostics.crash

import com.android.tools.analytics.AnalyticsSettings
import com.android.tools.idea.diagnostics.crash.exception.NoPiiException
import com.android.tools.idea.serverflags.protos.ExceptionConfiguration
import com.google.common.base.Throwables
import com.google.common.collect.ImmutableList
import com.intellij.execution.filters.CompositeFilter.ApplyFilterException
import com.intellij.idea.IdeaLogger
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import org.jetbrains.annotations.TestOnly
import java.lang.Integer.min
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.Arrays
import java.util.GregorianCalendar
import java.util.stream.Collectors
import java.util.stream.Stream

class ExceptionDataCollection {

  companion object {
    /**
     * [Throwable] classes with messages expected to be useful for debugging and not to contain PII.
     */
    private val THROWABLE_CLASSES_TO_TRACK_MESSAGES = ImmutableList.of(
      LinkageError::class.java,
      ReflectiveOperationException::class.java,
      ArrayIndexOutOfBoundsException::class.java,
      ClassCastException::class.java,
      IndexOutOfBoundsException::class.java,
      NoPiiException::class.java)

    @JvmStatic
    fun getInstance(): ExceptionDataCollection {
      return ApplicationManager.getApplication().getService(ExceptionDataCollection::class.java)
    }

    private val LOG = Logger.getInstance(ExceptionDataCollection::class.java)
    private const val LOGGER_ERROR_MESSAGE_EXCEPTION = "com.android.diagnostic.LoggerErrorMessage"
    private const val LOG_COLLECTION_ENABLED = false
    private val LOGGER_CLASSES = ImmutableList.of(
      Logger::class.java.name, IdeaLogger::class.java.name)
  }

  private var configs: Map<String, ExceptionConfiguration>

  private var logCache = LogCache()

  init {
    try {
      configs = ExceptionDataConfiguration.getInstance().getConfigurations()
    }
    catch (t: Throwable) {
      LOG.error("Cannot initialize exception log collection", t)
      configs = emptyMap()
    }
  }

  fun getExceptionUploadFields(t: Throwable, forceExceptionMessage: Boolean, includeLogs: Boolean): UploadFields {
    try {
      val cause = StudioExceptionReport.getRootCause(t)
      val configs = getExceptionUploadConfigurations(cause)

      val includeMessage =
        configs.values.any { it.action.includeExceptionMessage } ||
        forceExceptionMessage ||
        THROWABLE_CLASSES_TO_TRACK_MESSAGES.stream().anyMatch { it.isInstance(t) }
      val includeFullStack = configs.values.any { it.action.includeFullStack }
      val logs =
        if (includeLogs)
          configs
            .mapValues { logCache.getLogAndClearFor(it.key) }
            .filterValues { it.isNotEmpty() }
            .toSortedMap()
        else
          emptyMap()

      return UploadFields(
        description = getDescription(if (includeFullStack) t else cause, !includeMessage, includeFullStack),
        logs = logs)
    } catch (t: Throwable) {
      LOG.error("getExceptionUploadFields() call failed",t)
      return UploadFields(getDescription(t, stripMessage = true, includeFullStack = true), emptyMap())
    }
  }

  private fun getExceptionUploadConfigurations(t: Throwable): Map<String, ExceptionConfiguration> {
    val now = GregorianCalendar()
    now.time = AnalyticsSettings.dateProvider.now()

    return configs.filterValues { config ->
      // Expiration field is mandatory
      if (!config.hasExpirationDate())
        return@filterValues false

      val expirationDate = GregorianCalendar()
      expirationDate.set(config.expirationDate.year, config.expirationDate.month - 1, config.expirationDate.day)
      // Check for expired entry
      if (now.after(expirationDate))
        return@filterValues false

      var match = true
      // There must be at least one filter. The exception has to match all defined filters.
      var atLeastOneFilter = false
      val filter = config.exceptionFilter
      if (filter.hasExceptionType()) {
        match = (match && t.javaClass.name == filter.exceptionType)
        atLeastOneFilter = true
      }
      if (filter.hasSignature()) {
        val signature = calculateSignature(t)
        match = (match && signature == filter.signature)
        atLeastOneFilter = true
      }
      if (filter.framePatternsCount > 0) {
        atLeastOneFilter = true
        val stackTrace = t.stackTrace
        // Each frame pattern must match exception's stacktrace
        match = match && filter.framePatternsList.all { frameToMatch ->
          stackTrace.any { frame -> (frame.className + frame.methodName) == frameToMatch }
        }
      }
      return@filterValues atLeastOneFilter && match
    }
  }

  private val regex = Regex("""(.*)\$[0-9]+""")

  fun calculateSignature(t: Throwable): String {
    val digest = MessageDigest.getInstance("SHA-1")
    val originalStackTrace = t.stackTrace
    val stackTrace = removeLoggerErrorFrames(t.stackTrace)
    val isLoggerError = originalStackTrace.size != stackTrace.size
    val sb = StringBuilder()
    stackTrace.slice(1 until min(6, stackTrace.size)).joinTo(sb, "") {
      var methodName = it.methodName
      val matchResult = regex.matchEntire(methodName)

      if (matchResult != null)
        methodName = matchResult.groupValues[1]

      return@joinTo if (methodName != "")
        "${it.className}.$methodName"
      else
        ""
    }
    val className = if (isLoggerError) LOGGER_ERROR_MESSAGE_EXCEPTION else t.javaClass.name
    val signaturePrefix = "$className at ${stackTrace[0].className}.${stackTrace[0].methodName}"
    sb.append(signaturePrefix)
    val hashBytes = digest.digest(sb.toString().toByteArray(Charset.forName("UTF-8")))
    val hash = hashBytes.joinToString("") { it.toInt().and(0xff).toString(16).padStart(2, '0') }.substring(0, 8)
    return "$signaturePrefix-$hash"
  }

  private fun removeLoggerErrorFrames(stackTraceElements: Array<StackTraceElement>): Array<StackTraceElement> {
    // Detect if the exception is from Logger.error. If so, change the type to LoggerErrorMessage
    // and remove the logger frames.
    var firstNonLoggerFrame = 0
    while (firstNonLoggerFrame < stackTraceElements.size &&
           stackTraceElements[firstNonLoggerFrame].methodName == "error" &&
           LOGGER_CLASSES.contains(stackTraceElements[firstNonLoggerFrame].className)) {
      firstNonLoggerFrame++
    }
    return stackTraceElements.sliceArray(firstNonLoggerFrame until stackTraceElements.size)
  }

  /**
   * Returns an exception description (similar to [Throwables.getStackTraceAsString]}) with the exception message
   * removed in order to strip off any PII. The exception message is include for some specific exceptions where we know that the
   * message will not have any PII.
   *
   * Doesn't remove the exception message when it is a user-reported exception.
   */
  fun getDescription(t: Throwable, stripMessage: Boolean, includeFullStack: Boolean): String {
    return StringBuilder().also { sb ->
      getDescription(t, stripMessage, includeFullStack, null, 1, sb)
    }.toString()
  }

  private fun getDescription(t: Throwable,
                             stripMessage: Boolean,
                             includeFullStack: Boolean,
                             containingThrowable: Throwable?,
                             depth: Int, sb: StringBuilder) {
    if (depth >= 20) {
      return
    }

    if (!stripMessage) {
      // Keep the message if the exception is user-reported.
      // Note: User-reported exceptions in IntelliJ are not proper Throwable objects.
      //   All such exceptions are wrapped in IdeaReportingEvent$TextBasedThrowable, for which
      //   t.getStackTrace() and t.printStackTrace() return different stack traces. t.getStackTrace() gives the
      //   stack of the object creation, t.printStackTrace() - stack of the wrapped exception.

      val traceAsString = Throwables.getStackTraceAsString(t)
      sb.append(fixStackTraceStringForLoggerErrorMethod(traceAsString))
      return
    }

    // Remove the exception message
    val originalStackTraceElements = t.stackTrace
    val stackTraceElements = removeLoggerErrorFrames(t.stackTrace)

    val isLoggerErrorException = stackTraceElements.size != originalStackTraceElements.size
    val exceptionName = if (isLoggerErrorException) LOGGER_ERROR_MESSAGE_EXCEPTION else t.javaClass.name
    sb.append("$exceptionName: <elided>\n") // note: some message is needed for the backend to parse the report properly

    var commonFrames = 0
    if (containingThrowable != null) {
      var currThis = stackTraceElements.size - 1
      val containingTrace = containingThrowable.stackTrace
      var currContaining = containingTrace.size - 1
      while (currThis >= 0 && currContaining >= 0 && stackTraceElements[currThis] == containingTrace[currContaining]) {
        currThis--
        currContaining--
        commonFrames++
      }
    }

    for (i in 0 until stackTraceElements.size - commonFrames) {
      sb.append("\tat ${stackTraceElements[i]}\n")
    }
    if (commonFrames > 0) {
      sb.append("\t... $commonFrames more")
    }
    if (includeFullStack) {
      t.cause?.let { cause ->
        sb.append("Caused by: ")
        getDescription(cause, stripMessage, includeFullStack, t, depth + 1, sb)
      }
    }
  }

  /**
   * Logger.error messages show up as java.lang.Throwable exceptions with a top frame being
   * Logger.error. This method will detect if the exception string is from such method. If so,
   * it changes its type to com.android.diagnostic.LoggerErrorMessage and removes the top frame
   * (Logger.error frame).
   */
  private fun fixStackTraceStringForLoggerErrorMethod(s: String): String {
    val lines = s.split("\n".toRegex()).toTypedArray()
    if (lines.size <= 2)
      return s

    val throwableText = Throwable::class.java.name
    val throwableColonText = "$throwableText:"

    // Non-logger exception
    if (lines[0] != throwableText && !lines[0].startsWith(throwableColonText))
      return s

    // Message can be multiline. Find the line with the first frame.
    val indexOfFirstFrame = lines.indexOfFirst { it.startsWith("\tat ") }

    // No frames, only exception message, return original exception
    if (indexOfFirstFrame == -1)
      return s

    var indexOfFirstNonLoggerFrame = indexOfFirstFrame
    while (indexOfFirstNonLoggerFrame < lines.size &&
           (lines[indexOfFirstNonLoggerFrame].startsWith("\tat ${Logger::class.java.name}.error(") ||
            lines[indexOfFirstNonLoggerFrame].startsWith("\tat ${IdeaLogger::class.java.name}.error("))) {
      indexOfFirstNonLoggerFrame++
    }

    // Only Logger.error frames or no logger.error frames
    if (indexOfFirstNonLoggerFrame == lines.size || indexOfFirstNonLoggerFrame == indexOfFirstFrame)
      return s

    // Update exception type
    lines[0] = LOGGER_ERROR_MESSAGE_EXCEPTION + lines[0].substring(throwableText.length)

    // Remove Logger.error frames and update exception type
    val exceptionMessageLines = Arrays.stream(lines).limit(indexOfFirstFrame.toLong())
    val stackFramesLines = Arrays.stream(lines).skip(indexOfFirstNonLoggerFrame.toLong())
    return Stream.concat(exceptionMessageLines, stackFramesLines).collect(Collectors.joining("\n"))
  }

  fun requiresConfirmation(t: Throwable): Boolean {
    try {
      val cause = StudioExceptionReport.getRootCause(t)
      val exceptionUploadConfigurations = getExceptionUploadConfigurations(cause)
      return exceptionUploadConfigurations.any { (_, config) ->
        (!config.action.hasRequiresConfirmation() || config.action.requiresConfirmation)
      }
    } catch (t: Throwable) {
      LOG.warn("cannot compute if exception requires confirmation", t)
      return false
    }
  }

  @TestOnly
  fun unregisterLogAppenders() {

  }
}

data class UploadFields(val description: String, val logs: Map<String, String>)
