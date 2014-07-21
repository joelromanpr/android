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
package com.android.tools.idea.tests.gui.framework;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.fest.swing.core.BasicRobot;
import org.fest.swing.core.Robot;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

import static com.android.tools.idea.tests.gui.framework.GuiTestRunner.canRunGuiTests;
import static junit.framework.Assert.assertNotNull;

@RunWith(GuiTestRunner.class)
public abstract class GuiTestCase {
  protected Robot myRobot;

  @Before
  public void setUp() throws Exception {
    if (!canRunGuiTests()) {
      // We currently do not support running UI tests in headless environments.
      return;
    }

    Application application = ApplicationManager.getApplication();
    assertNotNull(application); // verify that we are using the IDE's ClassLoader.

    myRobot = BasicRobot.robotWithCurrentAwtHierarchy();
 }

  @After
  public void tearDown() {
    if (myRobot != null) {
      myRobot.cleanUpWithoutDisposingWindows();
    }
  }
}
