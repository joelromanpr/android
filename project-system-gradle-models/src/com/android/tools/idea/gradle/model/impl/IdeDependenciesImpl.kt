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
package com.android.tools.idea.gradle.model.impl

import com.android.tools.idea.gradle.model.IdeAndroidLibrary
import com.android.tools.idea.gradle.model.IdeDependencies
import com.android.tools.idea.gradle.model.IdeJavaLibrary
import com.android.tools.idea.gradle.model.IdeModuleLibrary
import java.io.File
import java.io.Serializable

data class IdeDependenciesImpl(
  override val androidLibraries: Collection<IdeAndroidLibrary>,
  override val javaLibraries: Collection<IdeJavaLibrary>,
  override val moduleDependencies: Collection<IdeModuleLibrary>,
  override val runtimeOnlyClasses: Collection<File>
) : IdeDependencies, Serializable

class ThrowingIdeDependencies : IdeDependencies, Serializable {
  override val androidLibraries: Collection<IdeAndroidLibrary> get() = throw NotImplementedError()
  override val javaLibraries: Collection<IdeJavaLibrary> get() = throw NotImplementedError()
  override val moduleDependencies: Collection<IdeModuleLibrary> get() = throw NotImplementedError()
  override val runtimeOnlyClasses: Collection<File> get() = throw NotImplementedError()
}
