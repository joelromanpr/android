/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.precheck;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PreSyncCheckResult {
  @NotNull static final PreSyncCheckResult SUCCESS = new PreSyncCheckResult(true, null);

  private final boolean mySuccess;
  @Nullable private final String myFailureCause;

  @NotNull
  public static PreSyncCheckResult failure(@NotNull String cause) {
    return new PreSyncCheckResult(false, cause);
  }

  private PreSyncCheckResult(boolean success, @Nullable String failureCause) {
    mySuccess = success;
    myFailureCause = failureCause;
  }

  public boolean isSuccess() {
    return mySuccess;
  }

  @Nullable
  public String getFailureCause() {
    return myFailureCause;
  }
}
