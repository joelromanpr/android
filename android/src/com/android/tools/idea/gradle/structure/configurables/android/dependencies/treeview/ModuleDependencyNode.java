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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview;

import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsModelNode;
import com.android.tools.idea.gradle.structure.configurables.ui.treeview.AbstractPsNode;
import com.android.tools.idea.gradle.structure.model.android.*;
import com.google.common.collect.Lists;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.tools.idea.gradle.structure.model.PsDependency.TextType.PLAIN_TEXT;

public class ModuleDependencyNode extends AbstractDependencyNode<PsModuleAndroidDependency> {
  private final List<AbstractPsModelNode<?>> myChildren = Lists.newArrayList();

  public ModuleDependencyNode(@NotNull AbstractPsNode parent,
                              @NotNull PsModuleAndroidDependency dependency) {
    super(parent, dependency);
    setUp(dependency);
  }

  public ModuleDependencyNode(@NotNull AbstractPsNode parent,
                              @NotNull List<PsModuleAndroidDependency> dependencies) {
    super(parent, dependencies);
    setUp(dependencies.get(0));
  }

  private void setUp(@NotNull PsModuleAndroidDependency moduleDependency) {
    myName = moduleDependency.toText(PLAIN_TEXT);
    PsAndroidArtifact referredModuleMainArtifact = moduleDependency.findReferredArtifact();
    if (referredModuleMainArtifact != null) {
      PsAndroidArtifactDependencyCollection referredModuleDependencyCollection =
        new PsAndroidArtifactDependencyCollection(referredModuleMainArtifact);
      referredModuleDependencyCollection.forEach(dependency -> {
        AbstractPsModelNode<?> child = AbstractDependencyNode.createNode(this, referredModuleDependencyCollection, dependency);
        if (child != null) {
          myChildren.add(child);
        }
      });
    }
  }

  @Override
  @NotNull
  public SimpleNode[] getChildren() {
    return myChildren.toArray(new SimpleNode[myChildren.size()]);
  }
}
