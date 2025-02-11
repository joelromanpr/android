/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.jetbrains.android.dom.lint;

import com.android.tools.lint.detector.api.Severity;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.Required;
import com.intellij.util.xml.SubTagList;
import java.util.List;

public interface IssueDomElement extends DomElement {
  @Required
  @Attribute("id")
  @Convert(IssueIdConverter.class)
  GenericAttributeValue<String> getId();

  @Attribute("severity")
  @Convert(SeverityConverter.class)
  GenericAttributeValue<Severity> getSeverity();

  @SubTagList("ignore")
  List<IgnoreDomElement> getIgnores();
}
