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
package com.android.tools.idea.uibuilder.scene.decorator;

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintUtilities;
import com.android.tools.idea.uibuilder.scene.*;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.scene.draw.DrawConnection;
import com.android.tools.idea.uibuilder.scene.target.Target;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;

/**
 * This defines the decorator
 */
public class ConstraintLayoutDecorator extends SceneDecorator {
  final static String[] LEFT_DIR = {
    SdkConstants.ATTR_LAYOUT_LEFT_TO_LEFT_OF, SdkConstants.ATTR_LAYOUT_LEFT_TO_RIGHT_OF
  };
  final static String[] RIGHT_DIR = {
    SdkConstants.ATTR_LAYOUT_RIGHT_TO_RIGHT_OF, SdkConstants.ATTR_LAYOUT_RIGHT_TO_LEFT_OF
  };
  final static String[] TOP_DIR = {
    SdkConstants.ATTR_LAYOUT_TOP_TO_TOP_OF, SdkConstants.ATTR_LAYOUT_TOP_TO_BOTTOM_OF
  };
  final static String[] BOTTOM_DIR = {
    SdkConstants.ATTR_LAYOUT_BOTTOM_TO_BOTTOM_OF, SdkConstants.ATTR_LAYOUT_BOTTOM_TO_TOP_OF
  };
  final static String[][] ourConnections = {LEFT_DIR, RIGHT_DIR, TOP_DIR, BOTTOM_DIR};

  final static String[] MARGIN_ATTR = {
    SdkConstants.ATTR_LAYOUT_MARGIN_LEFT,
    SdkConstants.ATTR_LAYOUT_MARGIN_RIGHT,
    SdkConstants.ATTR_LAYOUT_MARGIN_TOP,
    SdkConstants.ATTR_LAYOUT_MARGIN_BOTTOM
  };
  final static String[] BIAS_ATTR = {
    SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS,
    SdkConstants.ATTR_LAYOUT_HORIZONTAL_BIAS,
    SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS,
    SdkConstants.ATTR_LAYOUT_VERTICAL_BIAS
  };
  final static boolean[] FLIP_BIAS = {
    true, false, false, true,
  };

  enum ConnectionType {
    SAME, BACKWARD
  }

  final static ConnectionType[] DIR_TABLE = {ConnectionType.SAME, ConnectionType.BACKWARD};
  final static String[] ourDirections = {"LEFT", "RIGHT", "TOP", "BOTTOM"};
  final static String[] ourDirectionsType = {"LEFT_TYPE", "RIGHT_TYPE", "TOP_TYPE", "BOTTOM_TYPE"};
  final static int[] ourOppositeDirection = {1, 0, 3, 2};

  private void convert(@NotNull SceneContext sceneContext, Rectangle rect) {
    rect.x = sceneContext.getSwingX(rect.x);
    rect.y = sceneContext.getSwingY(rect.y);
    rect.width = sceneContext.getSwingDimension(rect.width);
    rect.height = sceneContext.getSwingDimension(rect.height);
  }

  private void gatherProperties(@NotNull SceneComponent component,
                                @NotNull SceneComponent child) {
    for (int i = 0; i < ourDirections.length; i++) {
      getConnection(component, child, ourConnections[i], ourDirections[i], ourDirectionsType[i]);
    }
  }

  /**
   * This caches connections on each child SceneComponent by accessing NLcomponent attributes
   *
   * @param component
   * @param child
   * @param atributes
   * @param dir
   * @param dirType
   */
  private void getConnection(SceneComponent component, SceneComponent child, String[] atributes, String dir, String dirType) {
    String id = null;
    ConnectionType type = ConnectionType.SAME;
    for (int i = 0; i < atributes.length; i++) {
      id = child.getNlComponent().getAttribute(SdkConstants.SHERPA_URI, atributes[i]);
      type = DIR_TABLE[i];
      if (id != null) {
        break;
      }
    }
    if (id == null) {
      child.myCache.put(dir, id);
      child.myCache.put(dirType, ConnectionType.SAME);
      return;
    }
    if (id.equals("parent")) {
      child.myCache.put(dir, component);
      child.myCache.put(dirType, type);
      return;
    }
    for (SceneComponent con : component.getChildren()) {
      if (id.substring(5).equals(con.getId())) {
        child.myCache.put(dir, con);
        child.myCache.put(dirType, type);
        return;
      }
    }
    child.myCache.put(dirType, ConnectionType.SAME);
  }

  @Override
  public void buildList(@NotNull DisplayList list, long time, @NotNull SceneContext sceneContext, @NotNull SceneComponent component) {
    Color color = Color.red;
    if (component.getDrawState() == SceneComponent.DrawState.HOVER) {
      color = Color.yellow;
    }
    // Cache connections between children
    for (SceneComponent child : component.getChildren()) {
      gatherProperties(component, child);
    }

    // Create a simple rectangle for connections
    Rectangle rect = new Rectangle();
    component.fillRect(rect);
    list.addRect(sceneContext, rect, color);
    DisplayList.UNClip unclip = list.addClip(sceneContext, rect);
    ArrayList<SceneComponent> children = component.getChildren();
    // TODO: consider order of build or implement layers, we may want to render connections, children, then targets
    for (int i = 0; i < children.size(); i++) {
      SceneComponent child = children.get(i);
      child.getDecorator().buildList(list, time, sceneContext, child);
      buildListTarget(list, time, sceneContext, component, child);
      buildListConnections(list, time, sceneContext, component, child);
    }
    list.add(unclip);
  }

  /**
   * This is used to build the display list for the targets to control the children of the layout
   * It is called once per child of a ConstraintLayout SceneComponent
   *
   * @param list
   * @param time
   * @param screenView
   * @param component
   * @param child
   */
  private void buildListTarget(@NotNull DisplayList list,
                               long time,
                               @NotNull SceneContext sceneContext,
                               @NotNull SceneComponent component,
                               @NotNull SceneComponent child) {
    ArrayList<Target> targets = child.getTargets();
    int num = targets.size();
    for (int i = 0; i < num; i++) {
      Target target = targets.get(i);
      target.render(list, sceneContext);
    }
  }


  /**
   * This is used to build the display list of Constraints hanging off of of each child.
   * This assume all children have been pre-processed to cache the connections to other SceneComponents
   *
   * @param list
   * @param time
   * @param screenView
   * @param component
   * @param child
   */
  public void buildListConnections(@NotNull DisplayList list,
                                   long time,
                                   @NotNull SceneContext sceneContext,
                                   @NotNull SceneComponent component,
                                   @NotNull SceneComponent child) {
    Rectangle dest_rect = new Rectangle();
    Rectangle source_rect = new Rectangle();
    child.fillDrawRect(time, source_rect);
    convert(sceneContext, source_rect);
    int x = source_rect.x;
    int y = source_rect.y;
    int w = source_rect.width;
    int h = source_rect.height;


    // Extract Scene Components constraints from cache (Table speeds up next step)
    ConnectionType[] connectionTypes = new ConnectionType[ourDirections.length];
    SceneComponent[] connectionTo = new SceneComponent[ourDirections.length];
    for (int i = 0; i < ourDirections.length; i++) {
      connectionTypes[i] = (ConnectionType)child.myCache.get(ourDirectionsType[i]);
      connectionTo[i] = (SceneComponent)child.myCache.get(ourDirections[i]);
    }

    for (int i = 0; i < ourDirections.length; i++) {
      ConnectionType type = connectionTypes[i];
      SceneComponent sc = connectionTo[i];

      if (sc != null) {
        sc.fillDrawRect(time, dest_rect);  // get the destination rectangle
        convert(sceneContext, dest_rect);   // scale to screen space
        int connect = (type == ConnectionType.SAME) ? i : ourOppositeDirection[i];
        boolean toParent = (child.getParent().equals(sc)); // flag a child connection
        int connectType = DrawConnection.TYPE_NORMAL;

        if (connectionTo[ourOppositeDirection[i]] != null) { // opposite side is connected
          connectType = DrawConnection.TYPE_SPRING;
          if (connectionTo[ourOppositeDirection[i]] == sc && ! toParent) { // center
            connectType = DrawConnection.TYPE_CENTER;
          }
        }
        SceneComponent toComponentsTo = (SceneComponent)sc.myCache.get(ourDirections[connect]);
        // Chain detection
        if (type == ConnectionType.BACKWARD // this connection must be backward
            && toComponentsTo == child  // it must connect to some one who connects to me
            && sc.myCache.get(ourDirectionsType[connect]) == ConnectionType.BACKWARD) { // and that connection must be backward as well
          connectType = DrawConnection.TYPE_CHAIN;
          if (sc.myCache.containsKey("chain")) {
            continue; // no need to add element to display list chains only have to go one way
          }
          child.myCache.put("chain","drawn");
        }
        int margin = 0;
        float bias = 0.5f;
        String marginString = child.getNlComponent().getAttribute(SdkConstants.NS_RESOURCES, MARGIN_ATTR[i]);
        if (marginString != null) {
          margin = ConstraintUtilities.getDpValue(child.getNlComponent(), marginString);
        }
        String biasString = child.getNlComponent().getAttribute(SdkConstants.SHERPA_URI, BIAS_ATTR[i]);
        if (biasString != null) {
          try {
            bias = Float.parseFloat(biasString);
            if (FLIP_BIAS[i]) {
              bias = 1 - bias;
            }
          }
          catch (NumberFormatException e) {
          }
        }
        boolean shift = toComponentsTo != null;
        DrawConnection.buildDisplayList(list, connectType, source_rect, i, dest_rect, connect, toParent, shift, margin, bias);
      }
    }
  }
}
