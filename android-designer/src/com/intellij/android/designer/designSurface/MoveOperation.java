/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.android.designer.designSurface;

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.AlphaComponent;
import com.intellij.designer.model.RadComponent;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class MoveOperation implements EditOperation {
  private final OperationContext myContext;
  private List<RadComponent> myComponents;
  private List<JComponent> myFeedbackList;

  public MoveOperation(OperationContext context) {
    myContext = context;
  }

  @Override
  public void setComponent(RadComponent component) {
  }

  @Override
  public void setComponents(List<RadComponent> components) {
    myComponents = components;
  }

  @Override
  public void showFeedback() {
    FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
    int size = myComponents.size();

    if (myFeedbackList == null) {
      myFeedbackList = new ArrayList<JComponent>();

      for (int i = 0; i < size; i++) {
        JComponent feedback = new AlphaComponent(Color.GREEN, Color.LIGHT_GRAY);
        myFeedbackList.add(feedback);
        layer.add(feedback);
      }
    }

    for (int i = 0; i < size; i++) {
      myFeedbackList.get(i).setBounds(myContext.getTransformedRectangle(myComponents.get(i).getBounds(layer)));
    }

    layer.repaint();
  }

  @Override
  public void eraseFeedback() {
    if (myFeedbackList != null) {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

      for (JComponent feedback : myFeedbackList) {
        layer.remove(feedback);
      }

      layer.repaint();
      myFeedbackList = null;
    }
  }

  @Override
  public boolean canExecute() {
    return true;
  }

  @Override
  public void execute() throws Exception {
    for (RadComponent component : myComponents) {
      Rectangle bounds = myContext.getTransformedRectangle(component.getBounds());
      RadViewComponent viewComponent = (RadViewComponent)component;
      viewComponent.setBounds(bounds.x, bounds.y, bounds.width, bounds.height);
    }
  }
}