/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.npw.module.recipes.macrobenchmarkModule

import com.android.tools.idea.wizard.template.renderIf

fun appAndroidManifest(needsTargetFlag: Boolean) = """
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
${renderIf(needsTargetFlag) { "xmlns:tools=\"http://schemas.android.com/tools\"" }}>

  <application>
    <profileable android:shell="true" ${renderIf(needsTargetFlag) { "tools:targetApi=\"q\"" }}/>
  </application>

</manifest>
""".trimIndent()