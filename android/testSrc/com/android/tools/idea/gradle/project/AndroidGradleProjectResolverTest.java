/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.tools.idea.gradle.project.AndroidGradleProjectResolver.ProjectResolverFunctionFactory;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.*;
import com.intellij.util.Function;
import junit.framework.TestCase;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.project.GradleExecutionHelper;
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings;

import static org.easymock.classextension.EasyMock.*;

/**
 * Tests for {@link AndroidGradleProjectResolver}.
 */
public class AndroidGradleProjectResolverTest extends TestCase {
  private GradleExecutionHelper myHelper;
  private ProjectResolverFunctionFactory myFunctionFactory;
  private Function<ProjectConnection, DataNode<ProjectData>> myProjectResolverFunction;
  private AndroidGradleProjectResolver myProjectResolver;

  @SuppressWarnings("unchecked")
  @Override
  public void setUp() throws Exception {
    super.setUp();
    myHelper = createMock(GradleExecutionHelper.class);
    myFunctionFactory = createMock(ProjectResolverFunctionFactory.class);
    myProjectResolverFunction = createMock(Function.class);
    myProjectResolver = new AndroidGradleProjectResolver(myHelper, myFunctionFactory);
  }

  @SuppressWarnings("unchecked")
  public void testResolveProjectInfo() throws Exception {
    String projectId = "dummy";
    ExternalSystemTaskId id = ExternalSystemTaskId.create(ExternalSystemTaskType.RESOLVE_PROJECT, projectId);
    String projectPath = "~/basic/build.gradle";
    GradleExecutionSettings settings = createMock(GradleExecutionSettings.class);

    ExternalSystemTaskNotificationListener listener = new ExternalSystemTaskNotificationListenerAdapter() {
    };
    expect(myFunctionFactory.createFunction(id, projectPath, settings, listener)).andReturn(myProjectResolverFunction);
    DataNode<ProjectData> projectInfo = createMock(DataNode.class);
    expect(myHelper.execute(projectPath, settings, myProjectResolverFunction)).andReturn(projectInfo);

    replay(myFunctionFactory, myHelper);


    DataNode<ProjectData> resolved = myProjectResolver.resolveProjectInfo(id, projectPath, true, settings, listener);
    assertSame(projectInfo, resolved);

    verify(myFunctionFactory, myHelper);
  }
}
