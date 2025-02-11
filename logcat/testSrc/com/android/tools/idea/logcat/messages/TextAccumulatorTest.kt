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
package com.android.tools.idea.logcat.messages

import com.android.tools.idea.logcat.messages.TextAccumulator.Range
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.editor.markup.TextAttributes
import org.junit.Test
import java.awt.Color

private val blue = TextAttributes().apply { foregroundColor = Color.blue }
private val red = TextAttributes().apply { foregroundColor = Color.red }

/**
 * Tests for [TextAccumulator]
 */
class TextAccumulatorTest {
  @Test
  fun accumulate_noColor() {
    val buffer = TextAccumulator()

    buffer.accumulate("foo")
    buffer.accumulate("bar")

    assertThat(buffer.text).isEqualTo("foobar")
    assertThat(buffer.highlightRanges).isEmpty()
  }

  @Test
  fun accumulate_withColor() {
    val buffer = TextAccumulator()

    buffer.accumulate("foo-")
    buffer.accumulate("blue", blue)
    buffer.accumulate("-bar-")
    buffer.accumulate("red", red)

    assertThat(buffer.text).isEqualTo("foo-blue-bar-red")
    assertThat(buffer.highlightRanges).containsExactly(Range(4, 8, blue), Range(13, 16, red))
  }

  @Test
  fun accumulate_withHint() {
    val buffer = TextAccumulator()

    buffer.accumulate("foo", hint="foo")
    buffer.accumulate("-")
    buffer.accumulate("bar", hint="bar")

    assertThat(buffer.text).isEqualTo("foo-bar")
    assertThat(buffer.hintRanges).containsExactly(Range(0, 3, "foo"), Range(4, 7, "bar"))
  }
}
