/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.sqlite.model

import java.sql.JDBCType

/**
 * Abstraction over the schema of a single Sqlite database.
 *
 * @see [com.android.tools.idea.sqlite.SqliteService.readSchema].
 */
interface SqliteSchema {

  /**
   * The list of tables in the database
   */
  val tables: List<SqliteTable>

  companion object {
    /**
     * An empty schema, used when the schema is not known yet.
     */
    val EMPTY: SqliteSchema = object : SqliteSchema {
      override val tables: List<SqliteTable> = emptyList()
    }
  }
}

/** Representation of the Sqlite database table */
data class SqliteTable(val name: String, val columns: List<SqliteColumn>)

/** Representation of the Sqlite table row */
data class SqliteRow(val values: List<SqliteColumnValue>)

/** Representation of a Sqlite table column */
data class SqliteColumn(val name: String, val type: JDBCType)

/** Representation of a Sqlite table column value */
data class SqliteColumnValue(val column: SqliteColumn, val value: Any?)