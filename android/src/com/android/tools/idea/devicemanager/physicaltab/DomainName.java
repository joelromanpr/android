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
package com.android.tools.idea.devicemanager.physicaltab;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class DomainName extends Key {
  private static final @NotNull Pattern PATTERN = Pattern.compile("adb-(.*)-.*\\._adb-tls-connect\\._tcp\\.?");
  private final @NotNull String myValue;

  DomainName(@NotNull String value) {
    assert PATTERN.matcher(value).matches();
    myValue = value;
  }

  @Override
  protected @NotNull ConnectionType getConnectionType() {
    return ConnectionType.WI_FI;
  }

  @Override
  protected @NotNull SerialNumber getSerialNumber() {
    Matcher matcher = PATTERN.matcher(myValue);

    if (!matcher.matches()) {
      throw new AssertionError(myValue);
    }

    return new SerialNumber(matcher.group(1));
  }

  @Override
  public int hashCode() {
    return myValue.hashCode();
  }

  @Override
  public boolean equals(@Nullable Object object) {
    return object instanceof DomainName && myValue.equals(((DomainName)object).myValue);
  }

  @Override
  public @NotNull String toString() {
    return myValue;
  }
}
