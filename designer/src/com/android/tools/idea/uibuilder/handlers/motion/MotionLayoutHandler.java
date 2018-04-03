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
package com.android.tools.idea.uibuilder.handlers.motion;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.target.ComponentAssistantActionTarget;
import com.android.tools.idea.common.scene.target.Target;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.uibuilder.api.AccessoryPanelInterface;
import com.android.tools.idea.uibuilder.api.CustomPanel;
import com.android.tools.idea.uibuilder.handlers.assistant.TransitionLayoutAssistantPanel;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintLayoutHandler;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.property.assistant.ComponentAssistantFactory;
import com.android.tools.idea.uibuilder.surface.AccessoryPanel;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static com.android.SdkConstants.ATTR_TRANSITION_POSITION;

public class MotionLayoutHandler extends ConstraintLayoutHandler {

  public static class MotionLayoutComponentHelper {
    private NlComponent myTransitionLayoutComponent;
    private Method myCallSetTransitionPosition;
    private Method myCallEvaluate;
    private Method myGetMaxTimeMethod;

    public MotionLayoutComponentHelper(@NotNull NlComponent component) {
      myTransitionLayoutComponent = component;
    }

    private void setTransitionPosition(Object instance, float position) {
      if (myCallSetTransitionPosition == null) {
        try {
          myCallSetTransitionPosition = instance.getClass().getMethod("setTransitionPosition", float.class);
        }
        catch (NoSuchMethodException e) {
          e.printStackTrace();
        }
      }
      if (myCallSetTransitionPosition != null) {
        try {
          RenderService.runRenderAction(() -> {
            try {
              myCallSetTransitionPosition.invoke(instance, Float.valueOf(position));
            }
            catch (ClassCastException | IllegalAccessException | InvocationTargetException e) {
              myCallSetTransitionPosition = null;
              e.printStackTrace();
            }
          });
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }
    }

    private void evaluate(Object instance) {
      if (myCallEvaluate == null) {
        try {
          myCallEvaluate = instance.getClass().getMethod("evaluate");
        }
        catch (NoSuchMethodException e) {
          e.printStackTrace();
        }
      }
      if (myCallEvaluate != null) {
        try {
          RenderService.runRenderAction(() -> {
            try {
              myCallEvaluate.invoke(instance);
            }
            catch (ClassCastException |IllegalAccessException | InvocationTargetException e) {
              myCallEvaluate = null;
              e.printStackTrace();
            }
          });
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }
    }

    public void setValue(float value) {
      ViewInfo info = NlComponentHelperKt.getViewInfo(myTransitionLayoutComponent);
      if (info == null) {
        return;
      }
      Object instance = info.getViewObject();
      setTransitionPosition(instance, value);
      evaluate(instance);
      NlModel model = myTransitionLayoutComponent.getModel();
      model.notifyLiveUpdate(false);
    }

    public long getMaxTimeMs() {
      ViewInfo info = NlComponentHelperKt.getViewInfo(myTransitionLayoutComponent);
      if (info == null) {
        return 0;
      }

      Object instance = info.getViewObject();
      if (myGetMaxTimeMethod == null) {
        try {
          myGetMaxTimeMethod = instance.getClass().getMethod("getTransitionTimeMs");
        }
        catch (NoSuchMethodException e) {
          e.printStackTrace();
        }
      }

      if (myGetMaxTimeMethod != null) {
        try {
          return RenderService.runRenderAction(() -> {
            try {
              return (long)myGetMaxTimeMethod.invoke(instance);
            }
            catch (IllegalAccessException | InvocationTargetException e) {
              myGetMaxTimeMethod = null;
              e.printStackTrace();
            }

            return 0;
          }).longValue();
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }
      return 0;
    }
  }

  static class AnimationPositionPanel extends JPanel implements CustomPanel {

    private NlComponent myComponent;
    private MotionLayoutComponentHelper myTransitionHandler;

    @Override
    @NotNull
    public JPanel getPanel() {
      return this;
    }

    @Override
    public void useComponent(@Nullable NlComponent component) {
      myComponent = component;
      if (component == null) {
        myTransitionHandler = null;
      }
      else {
        NlComponent transitionLayoutComponent = null;
        if (NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.MOTION_LAYOUT)) {
          transitionLayoutComponent = component;
        }
        else {
          NlComponent parent = myComponent.getParent();
          if (parent != null && NlComponentHelperKt.isOrHasSuperclass(parent, SdkConstants.MOTION_LAYOUT)) {
            transitionLayoutComponent = parent;
          }
        }

        myTransitionHandler = transitionLayoutComponent != null ? new MotionLayoutComponentHelper(transitionLayoutComponent) : null;
      }
    }

    @Override
    public void refresh() {
    }

    public AnimationPositionPanel() {
      setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
      add(new JLabel("Animation position:"));
      JSlider slider = new JSlider();
      slider.setValue(0);
      slider.setMinimum(0);
      slider.setMaximum(100);
      slider.addChangeListener(e -> {
        if (myTransitionHandler == null) {
          return;
        }
        float value = slider.getValue() / 100f;
        myTransitionHandler.setValue(value);
      });
      add(slider);
    }
  }

  @Override
  @Nullable
  public CustomPanel getCustomPanel() {
    return new AnimationPositionPanel();
  }

  @Override
  @NotNull
  public CustomPanel getLayoutCustomPanel() {
    return new AnimationPositionPanel();
  }

  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(ATTR_TRANSITION_POSITION);
  }

  @Nullable
  private static ComponentAssistantFactory getComponentAssistant(@NotNull DesignSurface surface, @NotNull NlComponent component) {
    if (!StudioFlags.NELE_MOTION_LAYOUT_ANIMATIONS.get() || !SdkConstants.MOTION_LAYOUT.isEquals(component.getTagName())) {
      return null;
    }

    return (context) -> new TransitionLayoutAssistantPanel(surface, context.getComponent(), context.getDoClose());
  }

  @NotNull
  @Override
  public List<Target> createTargets(@NotNull SceneComponent sceneComponent) {
    ComponentAssistantFactory panelFactory = getComponentAssistant(sceneComponent.getScene().getDesignSurface(), sceneComponent.getNlComponent());

    return panelFactory != null ?
           ImmutableList.of(new ComponentAssistantActionTarget(panelFactory)) :
           ImmutableList.of();
  }

  @Override
  public boolean needsAccessoryPanel(@NotNull AccessoryPanel.Type type) {
    switch (type) {
      case SOUTH_PANEL:
      case EAST_PANEL:
        return true;
    }
    return false;
  }

  @Override
  @NotNull
  public AccessoryPanelInterface createAccessoryPanel(@NotNull AccessoryPanel.Type type,
                                                      @NotNull NlComponent parent,
                                                      @NotNull AccessoryPanelVisibility panelVisibility) {
    switch (type) {
      case SOUTH_PANEL: return new MotionLayoutTimelinePanel(parent, panelVisibility);
      case EAST_PANEL: return new MotionLayoutAttributePanel(parent, panelVisibility);
    }
     throw new IllegalArgumentException("Unsupported type");
  }
}
