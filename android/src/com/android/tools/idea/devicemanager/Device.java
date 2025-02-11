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
package com.android.tools.idea.devicemanager;

import com.android.resources.Density;
import com.android.tools.idea.devicemanager.physicaltab.Key;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class Device {
  protected final @NotNull Key myKey;
  protected final @NotNull DeviceType myType;
  protected final @NotNull String myName;
  protected final @NotNull String myTarget;
  protected final @NotNull String myApi;

  protected static abstract class Builder {
    protected @Nullable Key myKey;
    protected @NotNull DeviceType myType = DeviceType.PHONE;
    protected @Nullable String myName;
    protected @Nullable String myTarget;
    protected @Nullable String myApi;

    protected abstract @NotNull Device build();
  }

  protected Device(@NotNull Builder builder) {
    assert builder.myKey != null;
    myKey = builder.myKey;

    myType = builder.myType;

    assert builder.myName != null;
    myName = builder.myName;

    assert builder.myTarget != null;
    myTarget = builder.myTarget;

    assert builder.myApi != null;
    myApi = builder.myApi;
  }

  public final @NotNull Key getKey() {
    return myKey;
  }

  public final @NotNull DeviceType getType() {
    return myType;
  }

  protected abstract @NotNull Icon getIcon();

  public final @NotNull String getName() {
    return myName;
  }

  public abstract boolean isOnline();

  public final @NotNull String getTarget() {
    return myTarget;
  }

  public final @NotNull String getApi() {
    return myApi;
  }

  public static @Nullable Resolution getDp(int density, @Nullable Resolution resolution) {
    if (density == -1) {
      return null;
    }

    if (resolution == null) {
      return null;
    }

    int width = (int)Math.ceil((double)Density.DEFAULT_DENSITY * resolution.getWidth() / density);
    int height = (int)Math.ceil((double)Density.DEFAULT_DENSITY * resolution.getHeight() / density);

    return new Resolution(width, height);
  }

  @Override
  public final @NotNull String toString() {
    return myName;
  }
}
