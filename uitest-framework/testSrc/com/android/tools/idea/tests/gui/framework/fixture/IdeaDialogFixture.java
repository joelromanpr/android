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
package com.android.tools.idea.tests.gui.framework.fixture;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickCancelButton;
import static org.fest.reflect.core.Reflection.field;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import java.lang.ref.WeakReference;
import javax.swing.JDialog;
import org.fest.reflect.exception.ReflectionError;
import org.fest.reflect.reference.TypeRef;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IdeaDialogFixture<T extends DialogWrapper> extends ComponentFixture<IdeaDialogFixture, JDialog>
  implements ContainerFixture<JDialog> {
  @NotNull private final T myDialogWrapper;

  @Nullable
  protected static <T extends DialogWrapper> T getDialogWrapperFrom(@NotNull JDialog dialog, Class<T> dialogWrapperType) {
    try {
      DialogWrapper wrapper = field("myDialogWrapper").ofType(new TypeRef<WeakReference<DialogWrapper>>() {}).in(dialog).get().get();
      if (dialogWrapperType.isInstance(wrapper)) {
        return dialogWrapperType.cast(wrapper);
      }
    }
    catch (ReflectionError ignored) {
    }
    return null;
  }

  public static class DialogAndWrapper<T extends DialogWrapper> {
    public final JDialog dialog;
    public final T wrapper;

    public DialogAndWrapper(@NotNull JDialog dialog, @NotNull T wrapper) {
      this.dialog = dialog;
      this.wrapper = wrapper;
    }
  }

  @NotNull
  public static <T extends DialogWrapper> DialogAndWrapper<T> find(@NotNull Robot robot, @NotNull final Class<T> clz) {
    return find(robot, clz, Matchers.byType(JDialog.class));
  }

  @NotNull
  public static <T extends DialogWrapper> DialogAndWrapper<T> find(@NotNull Robot robot, @NotNull final Class<T> clz,
                                                                   @NotNull final GenericTypeMatcher<JDialog> matcher) {
    final Ref<T> wrapperRef = new Ref<>();
    JDialog dialog = GuiTests.waitUntilShowing(robot, new GenericTypeMatcher<>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        if (matcher.matches(dialog)) {
          T wrapper = getDialogWrapperFrom(dialog, clz);
          if (wrapper != null) {
            wrapperRef.set(wrapper);
            return true;
          }
        }
        return false;
      }
    });
    return new DialogAndWrapper<>(dialog, wrapperRef.get());
  }

  protected IdeaDialogFixture(@NotNull Robot robot, @NotNull JDialog target, @NotNull T dialogWrapper) {
    super(IdeaDialogFixture.class, robot, target);
    myDialogWrapper = dialogWrapper;
  }

  protected IdeaDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<T> dialogAndWrapper) {
    this(robot, dialogAndWrapper.dialog, dialogAndWrapper.wrapper);
  }

  @NotNull
  protected T getDialogWrapper() {
    return myDialogWrapper;
  }

  @NotNull
  public String getTitle() {
    return myDialogWrapper.getTitle();
  }

  public void clickCancel() {
    findAndClickCancelButton(this);
    waitUntilNotShowing(); // Mac dialogs have an animation, wait until it hides
  }

  public void close() {
    robot().close(target());
  }

  public void waitUntilNotShowing() {
    Wait.seconds(1).expecting(target().getTitle() + " dialog to disappear").until(() -> !target().isShowing());
  }
}
