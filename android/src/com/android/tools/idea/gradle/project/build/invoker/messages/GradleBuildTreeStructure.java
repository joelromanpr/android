/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.invoker.messages;

import static com.intellij.ide.errorTreeView.NewErrorTreeViewPanel.createExportPrefix;
import static com.intellij.ide.errorTreeView.NewErrorTreeViewPanel.createRendererPrefix;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.intellij.ide.errorTreeView.ErrorTreeElement;
import com.intellij.ide.errorTreeView.ErrorTreeElementKind;
import com.intellij.ide.errorTreeView.ErrorViewStructure;
import com.intellij.ide.errorTreeView.GroupingElement;
import com.intellij.ide.errorTreeView.NavigatableMessageElement;
import com.intellij.ide.errorTreeView.SimpleMessageElement;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.containers.ContainerUtil;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stores the messages to be displayed in the "Messages" window.
 */
public class GradleBuildTreeStructure extends ErrorViewStructure {
  private final List<ErrorTreeElement> myMessages = ContainerUtil.createLockFreeCopyOnWriteList();
  private final ListMultimap<ErrorTreeElementKind, ErrorTreeElement> myMessagesByType = ArrayListMultimap.create();
  private final ListMultimap<String, NavigatableMessageElement> myGroupNameToMessagesMap = ArrayListMultimap.create();

  @NotNull private final Project myProject;
  @NotNull final GradleBuildTreeViewConfiguration myConfiguration;

  GradleBuildTreeStructure(@NotNull Project project, @NotNull GradleBuildTreeViewConfiguration configuration) {
    super(project, false);
    myProject = project;
    myConfiguration = configuration;
  }

  @Nullable
  @Override
  public ErrorTreeElement getFirstMessage(@NotNull ErrorTreeElementKind kind) {
    List<ErrorTreeElement> elements = myMessagesByType.get(kind);
    return elements.isEmpty() ? null : elements.get(0);
  }

  @Override
  public int getChildCount(GroupingElement groupingElement) {
    return myGroupNameToMessagesMap.get(groupingElement.getName()).size();
  }

  @NotNull
  @Override
  public ErrorTreeElement[] getChildElements(@NotNull Object element) {
    if (element instanceof ErrorTreeElement && element.getClass().getName().contains("MyRootElement")) {
      List<ErrorTreeElement> messages = Lists.newArrayListWithExpectedSize(myMessages.size());
      for (ErrorTreeElement message : myMessages) {
        if (canShow(message)) {
           messages.add(message);
        }
      }
      return messages.toArray(ErrorTreeElement.EMPTY_ARRAY);
    }
    if (element instanceof GroupingElement) {
      List<NavigatableMessageElement> children = myGroupNameToMessagesMap.get(((GroupingElement)element).getName());
      List<ErrorTreeElement> messages = Lists.newArrayListWithExpectedSize(children.size());
      for (NavigatableMessageElement message : children) {
        if (canShow(message)) {
          messages.add(message);
        }
      }
      return messages.toArray(ErrorTreeElement.EMPTY_ARRAY);
    }
    return ErrorTreeElement.EMPTY_ARRAY;
  }

  private boolean canShow(@NotNull ErrorTreeElement element) {
    if (element instanceof GroupingElement) {
      List<NavigatableMessageElement> children = myGroupNameToMessagesMap.get(((GroupingElement)element).getName());
      for (NavigatableMessageElement child : children) {
        if (canShow(child)) {
          return true;
        }
      }
      return false;
    }
    return myConfiguration.canShow(element.getKind());
  }

  @Override
  public ErrorTreeElement addMessage(@NotNull ErrorTreeElementKind kind,
                                     @NotNull String[] text,
                                     @Nullable VirtualFile underFileGroup,
                                     @Nullable VirtualFile file,
                                     int line,
                                     int column,
                                     @Nullable Object data) {
    if (underFileGroup != null || file != null) {
      if (file == null) line = column = -1;

      int uiLine = line < 0 ? -1 : line + 1;
      int uiColumn = column < 0 ? -1 : column + 1;

      VirtualFile group = underFileGroup != null ? underFileGroup : file;
      VirtualFile nav = file != null ? file : underFileGroup;

      return addNavigatableMessage(group.getPresentableUrl(), new OpenFileDescriptor(myProject, nav, line, column), kind, text, data,
                                   createExportPrefix(uiLine),
                                   createRendererPrefix(uiLine, uiColumn), group);
    }

    return addSimpleMessage(kind, text, data);
  }

  @Override
  public ErrorTreeElement addNavigatableMessage(@Nullable String groupName,
                                                @Nullable Navigatable navigatable,
                                                @NotNull ErrorTreeElementKind kind,
                                                @NotNull String[] message,
                                                @Nullable Object data,
                                                @NotNull String exportText,
                                                @NotNull String rendererTextPrefix,
                                                @Nullable VirtualFile file) {
    if (groupName != null) {
      //noinspection ConstantConditions
      GroupingElement grouping = getGroupingElement(groupName, data, file);
      //noinspection ConstantConditions

      NavigatableMessageElement e = new NavigatableMessageElement(kind, grouping, message, navigatable, exportText, rendererTextPrefix);
      myMessagesByType.put(kind, e);
      myGroupNameToMessagesMap.put(groupName, e);

      myMessages.add(grouping);
      return e;
    }
    //noinspection ConstantConditions

    final NavigatableMessageElement e = new NavigatableMessageElement(kind, null, message, navigatable, exportText, rendererTextPrefix);
    myMessages.add(e);
    return e;
  }

  private SimpleMessageElement addSimpleMessage(@NotNull ErrorTreeElementKind kind, @NotNull String[] text, @Nullable Object data) {
    //noinspection ConstantConditions
    SimpleMessageElement element = new SimpleMessageElement(kind, text, data);
    myMessagesByType.put(kind, element);
    myMessages.add(element);
    return element;
  }

}
