/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.sqlite.controllers

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.sqlite.OfflineModeManager.DownloadProgress
import com.android.tools.idea.sqlite.OfflineModeManager.DownloadState.COMPLETED
import com.android.tools.idea.sqlite.cli.SqliteCliArg
import com.android.tools.idea.sqlite.cli.SqliteCliArgs
import com.android.tools.idea.sqlite.cli.SqliteCliClientImpl
import com.android.tools.idea.sqlite.cli.SqliteCliProvider.Companion.SQLITE3_PATH_ENV
import com.android.tools.idea.sqlite.cli.SqliteCliProvider.Companion.SQLITE3_PATH_PROPERTY
import com.android.tools.idea.sqlite.cli.SqliteCliProviderImpl
import com.android.tools.idea.sqlite.cli.SqliteQueries
import com.android.tools.idea.sqlite.model.DatabaseFileData
import com.android.tools.idea.sqlite.model.Delimiter
import com.android.tools.idea.sqlite.model.ExportFormat.CSV
import com.android.tools.idea.sqlite.model.ExportFormat.DB
import com.android.tools.idea.sqlite.model.ExportFormat.SQL
import com.android.tools.idea.sqlite.model.ExportRequest
import com.android.tools.idea.sqlite.model.ExportRequest.ExportDatabaseRequest
import com.android.tools.idea.sqlite.model.ExportRequest.ExportQueryResultsRequest
import com.android.tools.idea.sqlite.model.ExportRequest.ExportTableRequest
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteDatabaseId.FileSqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteDatabaseId.LiveSqliteDatabaseId
import com.android.tools.idea.sqlite.model.SqliteRow
import com.android.tools.idea.sqlite.model.SqliteStatement
import com.android.tools.idea.sqlite.model.SqliteValue
import com.android.tools.idea.sqlite.model.createSqliteStatement
import com.android.tools.idea.sqlite.model.isInMemoryDatabase
import com.android.tools.idea.sqlite.model.isQueryStatement
import com.android.tools.idea.sqlite.repository.DatabaseRepository
import com.android.tools.idea.sqlite.ui.exportToFile.ExportToFileDialogView
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.exists
import com.intellij.util.io.isDirectory
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executor
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * @param downloadDatabase allows to download a database from the device (works for file-based databases (i.e. not in-memory))
 * @param deleteDatabase allows to delete files downloaded using [downloadDatabase]
 */
@UiThread
class ExportToFileController(
  private val project: Project,
  private val view: ExportToFileDialogView,
  private val databaseRepository: DatabaseRepository,
  private val downloadDatabase: (LiveSqliteDatabaseId, handleError: (String, Throwable?) -> Unit) -> Flow<DownloadProgress>,
  private val deleteDatabase: (DatabaseFileData) -> Unit,
  taskExecutor: Executor,
  edtExecutor: Executor,
  private val notifyExportComplete: (ExportRequest) -> Unit,
  private val notifyExportError: (ExportRequest, Throwable?) -> Unit
) : Disposable {
  private val edtDispatcher = edtExecutor.asCoroutineDispatcher()
  private val taskDispatcher = taskExecutor.asCoroutineDispatcher()
  private val listener = object : ExportToFileDialogView.Listener {
    override suspend fun exportRequestSubmitted(params: ExportRequest) = export(params)
  }

  fun setUp() {
    view.addListener(listener)
  }

  override fun dispose() {
    view.removeListener(listener)
  }

  private suspend fun export(params: ExportRequest) = withContext(edtDispatcher) {
    try {
      doExport(params)
      notifyExportComplete(params)
    }
    catch (t: Throwable) {
      // TODO(161081452): refine what we catch
      // TODO(161081452): add logging
      notifyExportError(params, t)
    }
  }

  private suspend fun doExport(params: ExportRequest): Unit = withContext(taskDispatcher) {
    when (params) {
      is ExportDatabaseRequest -> {
        when (params.format) {
          is CSV -> exportDatabaseToCsv(params.srcDatabase, params.format as CSV, params.dstPath)
          is SQL -> exportDatabaseToSql(params.srcDatabase, params.dstPath)
          is DB -> {
            if (params.srcDatabase.isInMemoryDatabase()) throwNotSupportedParams(params)
            else exportDatabaseToSqliteBinary(params.srcDatabase, params.dstPath)
          }
        }
      }
      is ExportTableRequest -> {
        when (params.format) {
          is CSV -> exportTableToCsv(params.srcDatabase, params.srcTable, params.format as CSV, params.dstPath)
          is SQL -> exportTableToSql(params.srcDatabase, params.srcTable, params.dstPath)
          else -> throwNotSupportedParams(params)
        }
      }
      is ExportQueryResultsRequest -> {
        if (!params.srcQuery.isQueryStatement) throwNotSupportedParams(params)

        when (params.format) {
          is CSV -> exportQueryToCsv(params.srcDatabase, params.srcQuery, params.format as CSV, params.dstPath)
          else -> throwNotSupportedParams(params)
        }
      }
    }
  }

  @Suppress("BlockingMethodInNonBlockingContext") // IO on taskDispatcher
  private suspend fun exportDatabaseToCsv(database: SqliteDatabaseId, format: CSV, dstPath: Path) = withContext(taskDispatcher) {
    // TODO(161081452): expose an option to let the user decide if to export views; defaulting now to not exporting views
    val tableNames: List<String> = databaseRepository.fetchSchema(database).tables.filter { !it.isView }.map { it.name }

    // TODO(161081452): skip temporary files (write directly to zip stream)
    val dstDir = findOrCreateDir(dstPath.parent)
    val tmpDir = Files.createTempDirectory(dstDir, ".tmp")
    Closeable { FileUtil.delete(tmpDir) }.use {
      val tmpFileToEntryName: List<TempExportedData> = tableNames.mapIndexed { ix, name ->
        val path = tmpDir.toAbsolutePath().resolve(".$ix.tmp") // using indexes for file names to avoid file naming issues
        doExport(ExportTableRequest(database, name, format, path))
        TempExportedData(path, "$name.csv")
      }

      createZipFile(dstPath, tmpFileToEntryName)
    }
  }

  /**
   * Exports the whole database to a single sqlite3 db file (binary format). Downloads the database from the device if needed.
   * @param database file-based database (i.e. not in-memory)
   */
  private suspend fun exportDatabaseToSqliteBinary(database: SqliteDatabaseId, dstPath: Path) = withContext(taskDispatcher) {
    executeTaskOnLocalDatabaseCopy(database) { srcPath ->
      findOrCreateDir(dstPath.parent)
      cloneDatabase(srcPath, dstPath)
    }
  }

  /**
   * Executes a [task] on a local (offline) copy of the database. Downloads the database from the device if needed.
   * @param database file-based database (i.e. not in-memory)
   */
  private suspend fun executeTaskOnLocalDatabaseCopy(database: SqliteDatabaseId, task: suspend (srcPath: Path) -> Unit) =
    withContext(taskDispatcher) {
      // TODO(161081452): [P1] verify behaviour when switching modes (online->offline or offline->online) while export operation in progress
      when (database) {
        is FileSqliteDatabaseId -> {
          task(database.databaseFileData.mainFile.toNioPath())
        }
        is LiveSqliteDatabaseId -> {
          downloadDatabase(database).let { files ->
            Closeable { files.forEach { deleteDatabase(it) } }.use {
              files.let {
                if (it.size != 1) throw IllegalStateException("Unexpected number of downloaded database files: ${it.size}")
                task(it.single().mainFile.toNioPath())
              }
            }
          }
        }
      }
    }

  private suspend fun cloneDatabase(srcPath: Path, dstPath: Path) = withContext(taskDispatcher) {
    runSqliteCliCommand(
      SqliteCliArgs
        .builder()
        .database(srcPath)
        .clone(dstPath)
        .build()
    )
  }

  private suspend fun runSqliteCliCommand(args: List<SqliteCliArg>) = withContext(taskDispatcher) {
    // TODO(161081452): expose the exception message in the UI / maybe as a help link?
    val sqlite3: Path = SqliteCliProviderImpl(project).getSqliteCli() ?: throw IllegalStateException(
      "Unable to find sqlite3 tool (part of Android SDK's platform-tools). " +
      "As a workaround consider setting an environment variable $SQLITE3_PATH_ENV or " +
      "a system property $SQLITE3_PATH_PROPERTY pointing to the file."
    )
    val commandResponse = SqliteCliClientImpl(sqlite3, taskDispatcher).runSqliteCliCommand(args)
    if (commandResponse.exitCode != 0) {
      throw IllegalStateException("Issue while executing sqlite3 command: ${commandResponse.errOutput}. Arguments: $args.")
    }
  }

  private suspend fun downloadDatabase(database: LiveSqliteDatabaseId) = withContext(taskDispatcher) {
    val flow = downloadDatabase(database) { message, throwable ->
      throw IllegalStateException("Issue while downloading database (${database.name}): $message", throwable)
    }

    flow
      .filter { it.downloadState == COMPLETED }
      .map { it.filesDownloaded }
      .toList()
      .flatten()
  }

  private suspend fun findOrCreateDir(dir: Path): Path = withContext(taskDispatcher) {
    val dirExists = dir.exists() && dir.isDirectory()
    val dirReady = dirExists || dir.toFile().mkdirs()
    if (!dirReady) throw IllegalStateException("Unable to access or create directory: $dir.")
    dir
  }

  private suspend fun exportTableToCsv(database: SqliteDatabaseId, srcTable: String, format: CSV, dstPath: Path) =
    withContext(taskDispatcher) {
      val query = createSqliteStatement(SqliteQueries.selectTableContents(srcTable))
      exportQueryToCsv(database, query, format, dstPath)
    }


  private suspend fun exportTableToSql(database: SqliteDatabaseId, srcTable: String, dstPath: Path) = withContext(taskDispatcher) {
    executeTaskOnLocalDatabaseCopy(database) { srcPath ->
      findOrCreateDir(dstPath.parent)
      exportTableToSql(srcPath, srcTable, dstPath)
    }
  }

  private suspend fun exportDatabaseToSql(database: SqliteDatabaseId, dstPath: Path) = withContext(taskDispatcher) {
    executeTaskOnLocalDatabaseCopy(database) { srcPath ->
      findOrCreateDir(dstPath.parent)
      exportDatabaseToSql(srcPath, dstPath)
    }
  }

  private suspend fun exportTableToSql(databasePath: Path, srcTable: String, dstPath: Path) = withContext(taskDispatcher) {
    runSqliteCliCommand(
      SqliteCliArgs
        .builder()
        .database(databasePath)
        .dumpTable(srcTable)
        .output(dstPath)
        .build()
    )
  }

  private suspend fun exportDatabaseToSql(databasePath: Path, dstPath: Path) = withContext(taskDispatcher) {
    runSqliteCliCommand(
      SqliteCliArgs
        .builder()
        .database(databasePath)
        .dump()
        .output(dstPath)
        .build()
    )
  }

  private suspend fun exportQueryToCsv(database: SqliteDatabaseId, query: SqliteStatement, format: CSV, dstPath: Path) =
    withContext(taskDispatcher) {
      writeRowsToCsvFile(
        executeQuery(database, query), // TODO(161081452): lock and download in chunks
        format.delimiter,
        dstPath
      )
    }

  private suspend fun createSqliteStatement(statementText: String): SqliteStatement = withContext(edtDispatcher) {
    createSqliteStatement(project, statementText)
  }

  private suspend fun executeQuery(srcDatabase: SqliteDatabaseId, srcQuery: SqliteStatement): List<SqliteRow> =
    withContext(taskDispatcher) {
      val resultSet = databaseRepository.runQuery(srcDatabase, srcQuery).await()
      // TODO(161081452): convert to a streaming implementation not to crash / hang on huge tables
      resultSet.getRowBatch(0, Integer.MAX_VALUE).await()
    }

  // TODO(161081452): change to writing a chunk at a time
  // TODO(161081452): move out to an IO class
  @Suppress("BlockingMethodInNonBlockingContext") // the warning tries to make us use Dispatchers.IO
  private suspend fun writeRowsToCsvFile(rows: List<SqliteRow>, delimiter: Delimiter, dstPath: Path) = withContext(taskDispatcher) {
    val delimiterString = delimiter.delimiter.toString()

    dstPath.toFile().bufferedWriter().use { writer ->
      // header
      rows.firstOrNull()?.values?.map { it.columnName }?.let {
        writer.append(it.joinToString(delimiterString))
        writer.newLine()
      }
      // data
      rows.forEach { row ->
        writer.append(row.values.joinToString(delimiterString) { it.value.asString })
        writer.newLine()
      }
    }
  }

  private suspend fun createZipFile(dstPath: Path, sourceToName: List<TempExportedData>) = withContext(taskDispatcher) {
    @Suppress("BlockingMethodInNonBlockingContext")
    FileOutputStream(dstPath.toFile()).use { fileOutputStream ->
      ZipOutputStream(fileOutputStream).use { zipOutputStream ->
        sourceToName.forEach { (source, name) ->
          FileInputStream(source.toFile()).use { fileInputStream ->
            zipOutputStream.putNextEntry(ZipEntry(name))
            val buffer = ByteArray(1024)
            generateSequence { fileInputStream.read(buffer) }
              .takeWhile { bytesReadCount -> bytesReadCount > 0 }
              .forEach { bytesReadCount -> zipOutputStream.write(buffer, 0, bytesReadCount) }
          }
        }
      }
    }
  }

  private val SqliteValue.asString
    get() : String =
      when (this) {
        is SqliteValue.StringValue -> value
        is SqliteValue.NullValue -> ""
      }

  private fun throwNotSupportedParams(params: ExportRequest) {
    // TODO(161081452): refine error handling
    throw IllegalArgumentException("Unsupported export request: $params")
  }

  private data class TempExportedData(val tempFile: Path, val finalFileName: String)
}