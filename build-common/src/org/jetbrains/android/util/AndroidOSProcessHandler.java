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
package org.jetbrains.android.util;

import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AndroidOSProcessHandler extends BaseOSProcessHandler {
  @NonNls private static final String IGNORING = "ignoring";
  @NonNls private static final String SKIPPING = "skipping";
  @NonNls private static final String DEBUGGABLE_ERROR = "androidmanifest.xml already defines debuggable";

  private final List<String> myInfoMessages = new ArrayList<>();
  private final List<String> myErrorMessages = new ArrayList<>();
  
  public AndroidOSProcessHandler(@NotNull final Process process, @NotNull final String commandLine) {
    super(process, commandLine, null);
  }

  @Override
  public void notifyTextAvailable(@NotNull String text, @NotNull Key outputType) {
    super.notifyTextAvailable(text, outputType);
    
    if (StringUtil.isEmptyOrSpaces(text)) {
      return;
    }
    String[] lines = text.split("[\\n\\r]+");
    for (String line : lines) {
      String l = StringUtil.toLowerCase(line);
      if (outputType == ProcessOutputTypes.STDOUT) {
        myInfoMessages.add(line);
      }
      else if (outputType == ProcessOutputTypes.STDERR) {
        if (l.contains(IGNORING) || l.contains(SKIPPING) || l.contains(DEBUGGABLE_ERROR)) {
          myInfoMessages.add(line);
        }
        else {
          myErrorMessages.add(line);
        }
      }
    }
  }

  @NotNull
  public List<String> getInfoMessages() {
    return myInfoMessages;
  }

  @NotNull
  public List<String> getErrorMessages() {
    return myErrorMessages;
  }
}
