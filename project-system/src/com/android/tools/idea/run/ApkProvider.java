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
package com.android.tools.idea.run;

import com.android.ddmlib.IDevice;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * An interface for providing information about the APKs to install on devices and/or emulators
 * during a run configuration execution.
 */
public interface ApkProvider {
  /**
   * @return The app and test APKs to install.
   */
  @NotNull
  Collection<ApkInfo> getApks(@NotNull IDevice device) throws ApkProvisionException;

  @NotNull
  List<ValidationError> validate();
}
