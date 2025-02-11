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
package com.android.tools.idea.appinspection.ide

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * A project service that exposes control for the App Inspection tool window.
 *
 * TODO(b/188695273): DELETE ME
 */
@Service
class AppInspectionToolWindowService(project: Project) {
  /**
   * The control API for the app inspection tool window. Null if app inspection
   * tool window hasn't been initialized yet.
   */
  var appInspectionToolWindowControl: AppInspectionToolWindowControl? = null
    internal set
}