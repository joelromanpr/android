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
package com.android.tools.idea.wizard;

import com.android.tools.idea.templates.Parameter;
import com.android.tools.idea.templates.TemplateMetadata;
import com.android.utils.XmlUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.intellij.openapi.util.text.StringUtil;
import junit.framework.TestCase;
import org.w3c.dom.Document;

import java.util.Map;

public class ParameterDefaultValueComputerTest extends TestCase {
  private static final String METADATA_XML = "<?xml version=\"1.0\"?>\n" +
                                             "<template\n" +
                                             "    format=\"4\"\n" +
                                             "    revision=\"2\"\n" +
                                             "    name=\"Android Manifest File\"\n" +
                                             "    description=\"Creates an Android Manifest XML File.\"\n" +
                                             "    >\n" +
                                             "\n" +
                                             "    <category value=\"Other\" />\n" +
                                             "\n" +
                                             "    <parameter\n" +
                                             "        id=\"p1\"\n" +
                                             "        name=\"p1 name\"\n" +
                                             "        type=\"boolean\"\n" +
                                             "        constraints=\"\"\n" +
                                             "        default=\"false\" />\n" +
                                             "\n" +
                                             "    <parameter\n" +
                                             "        id=\"p2\"\n" +
                                             "        name=\"p2 name\"\n" +
                                             "        type=\"string\"\n" +
                                             "        constraints=\"\"\n" +
                                             "        default=\"Hello\" />\n" +
                                             "\n" +
                                             "    <parameter\n" +
                                             "        id=\"p3\"\n" +
                                             "        name=\"p3 name\"\n" +
                                             "        type=\"string\"\n" +
                                             "        constraints=\"\"/>\n" +
                                             "\n" +
                                             "    <parameter\n" +
                                             "        id=\"p4\"\n" +
                                             "        name=\"p4 name\"\n" +
                                             "        type=\"string\"\n" +
                                             "        suggest=\"${p2}, World\"/>\n" +
                                             "\n" +
                                             "    <parameter\n" +
                                             "        id=\"p5\"\n" +
                                             "        name=\"p5 name\"\n" +
                                             "        type=\"string\"\n" +
                                             "        suggest=\"${p4}!\"/>\n" +
                                             "\n" +
                                             "    <parameter\n" +
                                             "        id=\"p6\"\n" +
                                             "        name=\"p6 name\"\n" +
                                             "        type=\"boolean\"\n" +
                                             "        suggest=\"${(p1 = false)?c}\"/>\n" +
                                             "\n" +
                                             "    <execute file=\"recipe.xml.ftl\" />\n" +
                                             "\n" +
                                             "</template>\n";
  private Map<String, Parameter> myParameterMap;

  public void testSimpleValuesDerival() {
    Map<Parameter, Object> defaultValuesMap =
      ParameterDefaultValueComputer.newDefaultValuesMap(myParameterMap.values(), ImmutableMap.<Parameter, Object>of());
    assertEquals(Boolean.FALSE, defaultValuesMap.get(myParameterMap.get("p1")));
    assertEquals("Hello", defaultValuesMap.get(myParameterMap.get("p2")));
    assertEquals("", defaultValuesMap.get(myParameterMap.get("p3")));
  }

  public void testComputedValuesDerival() {
    Map<Parameter, Object> defaultValuesMap =
      ParameterDefaultValueComputer.newDefaultValuesMap(myParameterMap.values(), ImmutableMap.<Parameter, Object>of());
    assertEquals("Hello, World", defaultValuesMap.get(myParameterMap.get("p4")));
    assertEquals("Hello, World!", defaultValuesMap.get(myParameterMap.get("p5")));
    assertEquals(Boolean.TRUE, defaultValuesMap.get(myParameterMap.get("p6")));
  }

  public void testComputedValuesDerivedFromNotNull() {
    Map<Parameter, Object> values = Maps.newHashMap();
    Map<Parameter, Object> defaultValuesMap =
      ParameterDefaultValueComputer.newDefaultValuesMap(myParameterMap.values(), values);
    values.put(myParameterMap.get("p2"), "Goodbye");
    assertEquals("Goodbye, World", defaultValuesMap.get(myParameterMap.get("p4")));
    assertEquals("Goodbye, World!", defaultValuesMap.get(myParameterMap.get("p5")));

    values.put(myParameterMap.get("p4"), "Value");
    assertEquals("Value!", defaultValuesMap.get(myParameterMap.get("p5")));
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Document document = XmlUtils.parseDocumentSilently(METADATA_XML, false);
    assert document != null;
    TemplateMetadata templateMetadata = new TemplateMetadata(document);
    myParameterMap = Maps.newHashMap();
    for (Parameter parameter : templateMetadata.getParameters()) {
      String name = parameter.id;
      if (!StringUtil.isEmpty(name)) {
        myParameterMap.put(name, parameter);
      }
    }
  }

}