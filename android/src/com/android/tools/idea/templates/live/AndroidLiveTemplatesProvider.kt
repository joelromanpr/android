/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.templates.live

import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider

/**
 * Provides available live templates bundled with the plugin.
 */
class AndroidLiveTemplatesProvider : DefaultLiveTemplatesProvider {
  override fun getDefaultLiveTemplateFiles(): Array<String> {
    return arrayOf(
      "liveTemplates/Android",
      "liveTemplates/AndroidKotlin",
      "liveTemplates/AndroidComments",
      "liveTemplates/AndroidCommentsKotlin",
      "liveTemplates/AndroidLog",
      "liveTemplates/AndroidLogKotlin",
      "liveTemplates/AndroidParcelable",
      "liveTemplates/AndroidTesting",
      "liveTemplates/AndroidXML"
    )
  }

  override fun getHiddenLiveTemplateFiles(): Array<String>? = null
}
