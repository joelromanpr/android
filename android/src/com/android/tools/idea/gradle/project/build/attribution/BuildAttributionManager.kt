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
package com.android.tools.idea.gradle.project.build.attribution

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import org.gradle.tooling.events.ProgressListener

data class BasicBuildAttributionInfo(
  val agpVersion: GradleVersion?
)

interface BuildAttributionManager : ProgressListener {
  fun onBuildStart()

  fun onBuildSuccess(request: GradleBuildInvoker.Request): BasicBuildAttributionInfo

  fun onBuildFailure(request: GradleBuildInvoker.Request)

  fun openResultsTab()

  fun shouldShowBuildOutputLink(): Boolean
}
