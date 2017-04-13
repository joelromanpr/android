/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.level2.GraphItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Creates a deep copy of a {@link GraphItem}.
 */
public final class IdeGraphItem extends IdeModel implements GraphItem {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @NotNull private final String myArtifactAddress;
  @NotNull private final List<GraphItem> myDependencies;
  @Nullable private final String myRequestedCoordinates;

  public IdeGraphItem(@NotNull GraphItem item) {
    myArtifactAddress = item.getArtifactAddress();

    myDependencies = new ArrayList<>();
    for (GraphItem dependency : item.getDependencies()) {
      myDependencies.add(new IdeGraphItem(dependency));
    }

    myRequestedCoordinates = item.getRequestedCoordinates();
  }

  @Override
  @NotNull
  public String getArtifactAddress() {
    return myArtifactAddress;
  }

  @Override
  @NotNull
  public List<GraphItem> getDependencies() {
    return myDependencies;
  }

  @Override
  @Nullable
  public String getRequestedCoordinates() {
    return myRequestedCoordinates;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeGraphItem)) {
      return false;
    }
    IdeGraphItem item = (IdeGraphItem)o;
    return Objects.equals(myArtifactAddress, item.myArtifactAddress) &&
           Objects.equals(myDependencies, item.myDependencies) &&
           Objects.equals(myRequestedCoordinates, item.myRequestedCoordinates);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myArtifactAddress, myDependencies, myRequestedCoordinates);
  }

  @Override
  public String toString() {
    return "IdeGraphItem{" +
           "myArtifactAddress='" + myArtifactAddress + '\'' +
           ", myDependencies=" + myDependencies +
           ", myRequestedCoordinates='" + myRequestedCoordinates + '\'' +
           '}';
  }
}
