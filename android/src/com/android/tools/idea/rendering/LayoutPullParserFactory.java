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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.Features;
import com.android.ide.common.rendering.api.HardwareConfig;
import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.res.ResourceHelper;
import com.intellij.codeInsight.template.emmet.generators.LoremGenerator;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.EnumSet;
import java.util.Set;

import static com.android.SdkConstants.*;
import static com.android.ide.common.rendering.api.SessionParams.RenderingMode.FULL_EXPAND;
import static com.android.ide.common.rendering.api.SessionParams.RenderingMode.V_SCROLL;

/**
 * The {@linkplain LayoutPullParserFactory} is responsible for creating
 * layout pull parsers for various different types of files.
 */
public class LayoutPullParserFactory {
  static final boolean DEBUG = false;

  private static final String[] VALID_XML_TAGS = {TAG_APPWIDGET_PROVIDER, TAG_PREFERENCE_SCREEN};
  private static final String[] ADAPTIVE_ICON_TAGS =  {TAG_ADAPTIVE_ICON, TAG_MASKABLE_ICON};
  private static final String[] FONT_FAMILY_TAGS = {TAG_FONT_FAMILY};

  private static final EnumSet<ResourceFolderType> FOLDER_NEEDS_READ_ACCESS =
    EnumSet.of(ResourceFolderType.DRAWABLE, ResourceFolderType.MENU, ResourceFolderType.XML, ResourceFolderType.FONT);

  /**
   * Returns whether the passed file is an {@link XmlFile} and starts with any of the given rootTags
   */
  private static boolean isXmlWithRootTag(@NotNull PsiFile file, @NotNull String[] rootTags) {
    if (!(file instanceof XmlFile)) {
      return false;
    }

    ApplicationManager.getApplication().assertReadAccessAllowed();
    XmlTag rootTag = ((XmlFile)file).getRootTag();
    if (rootTag == null) {
      return false;
    }

    String tag = rootTag.getName();
    for (String validRootTags : rootTags) {
      if (validRootTags.equals(tag)) {
        return true;
      }
    }

    return false;
  }

  public static boolean isSupported(@NotNull PsiFile file) {
    ResourceFolderType folderType = ResourceHelper.getFolderType(file);
    if (folderType == null) {
      return false;
    }
    switch (folderType) {
      case LAYOUT:
      case DRAWABLE:
      case MENU:
        return true;
      case MIPMAP:
        return isXmlWithRootTag(file, ADAPTIVE_ICON_TAGS);
      case XML:
        return isXmlWithRootTag(file, VALID_XML_TAGS);
      case FONT:
        // Temporarily disabled until layoutlib font-family rendering is fixed http://b/36402602
        //return isXmlWithRootTag(file, FONT_FAMILY_TAGS);
        return false;
      default:
        return false;
    }
  }

  @Nullable
  public static ILayoutPullParser create(@NotNull final RenderTask renderTask) {
    final ResourceFolderType folderType = renderTask.getFolderType();
    if (folderType == null) {
      return null;
    }

    if (FOLDER_NEEDS_READ_ACCESS.contains(folderType)
        && !ApplicationManager.getApplication().isReadAccessAllowed()) {
      return ApplicationManager.getApplication().runReadAction((Computable<ILayoutPullParser>)() -> create(renderTask));
    }

    XmlFile file = renderTask.getPsiFile();
    if (file == null) {
      throw new IllegalArgumentException("RenderTask always should always have PsiFile when it has ResourceFolderType");
    }

    switch (folderType) {
      case LAYOUT: {
        RenderLogger logger = renderTask.getLogger();
        Set<XmlTag> expandNodes = renderTask.getExpandNodes();
        HardwareConfig hardwareConfig = renderTask.getHardwareConfigHelper().getConfig();
        return LayoutPsiPullParser.create(file, logger, expandNodes, hardwareConfig.getDensity());
      }
      case DRAWABLE:
      case MIPMAP:
        renderTask.setDecorations(false);
        return createDrawableParser(file);
      case MENU:
        if (renderTask.supportsCapability(Features.ACTION_BAR)) {
          return new MenuLayoutParserFactory(renderTask).render();
        }
        renderTask.setRenderingMode(V_SCROLL);
        renderTask.setDecorations(false);
        return new MenuPreviewRenderer(renderTask, file).render();
      case XML: {
        // Switch on root type
        XmlTag rootTag = file.getRootTag();
        if (rootTag != null) {
          String tag = rootTag.getName();
          if (tag.equals(TAG_APPWIDGET_PROVIDER)) {
            // Widget
            renderTask.setDecorations(false);
            return createWidgetParser(rootTag);
          }
          else if (tag.equals(TAG_PREFERENCE_SCREEN)) {
            RenderLogger logger = renderTask.getLogger();
            Set<XmlTag> expandNodes = renderTask.getExpandNodes();
            HardwareConfig hardwareConfig = renderTask.getHardwareConfigHelper().getConfig();
            return LayoutPsiPullParser.create(file, logger, expandNodes, hardwareConfig.getDensity());
          }
        }
        return null;

      }
      case FONT:
        renderTask.setDecorations(false);
        renderTask.setRenderingMode(FULL_EXPAND);
        return createFontFamilyParser(file);
      default:
        // Should have been prevented by isSupported(PsiFile)
        assert false : folderType;
        return null;
    }
  }

  private static ILayoutPullParser createDrawableParser(XmlFile file) {
    // Build up a menu layout based on what we find in the menu file
    // This is *simulating* what happens in an Android app. We should get first class
    // menu rendering support in layoutlib to properly handle this.
    Document document = DomPullParser.createEmptyPlainDocument();
    assert document != null;
    Element imageView = addRootElement(document, IMAGE_VIEW);
    setAndroidAttr(imageView, ATTR_LAYOUT_WIDTH, VALUE_FILL_PARENT);
    setAndroidAttr(imageView, ATTR_LAYOUT_HEIGHT, VALUE_FILL_PARENT);

    setAndroidAttr(imageView, ATTR_SRC,
                   PREFIX_RESOURCE_REF + ResourceHelper.getFolderType(file).getName() + "/" + ResourceHelper.getResourceName(file));

    if (DEBUG) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println(XmlPrettyPrinter.prettyPrint(document, true));
    }

    // Allow tools:background in drawable XML files to manually set the render background.
    // Useful for example when dealing with vectors or shapes where the color happens to
    // be close to the IDE default background.
    String background = AndroidPsiUtils.getRootTagAttributeSafely(file, ATTR_BACKGROUND, TOOLS_URI);
    if (background != null && !background.isEmpty()) {
      setAndroidAttr(imageView, ATTR_BACKGROUND, background);
    }

    // Allow tools:scaleType in drawable XML files to manually set the scale type. This is useful
    // when the drawable looks poor in the default scale type. (http://b.android.com/76267)
    String scaleType = AndroidPsiUtils.getRootTagAttributeSafely(file, ATTR_SCALE_TYPE, TOOLS_URI);
    if (scaleType != null && !scaleType.isEmpty()) {
      setAndroidAttr(imageView, ATTR_SCALE_TYPE, scaleType);
    }

    return new DomPullParser(document.getDocumentElement());
  }

  @Nullable
  private static ILayoutPullParser createWidgetParser(XmlTag rootTag) {
    // See http://developer.android.com/guide/topics/appwidgets/index.html:

    // Build up a menu layout based on what we find in the menu file
    // This is *simulating* what happens in an Android app. We should get first class
    // menu rendering support in layoutlib to properly handle this.
    String layout = rootTag.getAttributeValue("initialLayout", ANDROID_URI);
    String preview = rootTag.getAttributeValue("previewImage", ANDROID_URI);
    if (layout == null && preview == null) {
      return null;
    }

    Document document = DomPullParser.createEmptyPlainDocument();
    assert document != null;
    Element root = addRootElement(document, layout != null ? VIEW_INCLUDE : IMAGE_VIEW);
    if (layout != null) {
      root.setAttribute(ATTR_LAYOUT, layout);
      setAndroidAttr(root, ATTR_LAYOUT_WIDTH, VALUE_FILL_PARENT);
      setAndroidAttr(root, ATTR_LAYOUT_HEIGHT, VALUE_FILL_PARENT);
    }
    else {
      root.setAttribute(ATTR_SRC, preview);
      setAndroidAttr(root, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
      setAndroidAttr(root, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
    }

    if (DEBUG) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println(XmlPrettyPrinter.prettyPrint(document, true));
    }

    return new DomPullParser(document.getDocumentElement());
  }

  @Nullable
  private static ILayoutPullParser createFontFamilyParser(XmlFile file) {
    XmlTag rootTag = file.getRootTag();

    if (rootTag == null || !TAG_FONT_FAMILY.equals(rootTag.getName())) {
      return null;
    }

    Document document = DomPullParser.createEmptyPlainDocument();
    assert document != null;
    Element rootLayout = addRootElement(document, LINEAR_LAYOUT);
    setAndroidAttr(rootLayout, ATTR_LAYOUT_WIDTH, VALUE_FILL_PARENT);
    setAndroidAttr(rootLayout, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
    setAndroidAttr(rootLayout, ATTR_ORIENTATION, VALUE_VERTICAL);

    String loremText = new LoremGenerator().generate(5, true);
    String fontRefName = PREFIX_RESOURCE_REF + ResourceHelper.getFolderType(file).getName() + "/" + ResourceHelper.getResourceName(file);
    for (XmlTag fontTag : rootTag.getSubTags()) {
      Element fontElement = document.createElement(TEXT_VIEW);
      setAndroidAttr(fontElement, ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
      setAndroidAttr(fontElement, ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
      setAndroidAttr(fontElement, ATTR_TEXT, loremText);
      setAndroidAttr(fontElement, ATTR_FONT_FAMILY, fontRefName);
      setAndroidAttr(fontElement, ATTR_TEXT_SIZE, "40sp");

      String fontStyle = fontTag.getAttributeValue("fontStyle",ANDROID_URI);
      if (StringUtil.isNotEmpty(fontStyle)) {
        setAndroidAttr(fontElement, ATTR_TEXT_STYLE, fontStyle);
      }

      rootLayout.appendChild(fontElement);
    }

    return new DomPullParser(document.getDocumentElement());
  }

  public static boolean needSave(@Nullable ResourceFolderType type) {
    // Only layouts are delegates to the IProjectCallback#getParser where we can supply a
    // parser directly from the live document; others read contents from disk via layoutlib.
    // TODO: Work on adding layoutlib support for this.
    return type != ResourceFolderType.LAYOUT;
  }

  public static void saveFileIfNecessary(PsiFile psiFile) {
    if (!needSave(ResourceHelper.getFolderType(psiFile.getVirtualFile()))) { // Avoid need for read lock in get parent
      return;
    }

    VirtualFile file = psiFile.getVirtualFile();
    if (file == null) {
      return;
    }

    final FileDocumentManager fileManager = FileDocumentManager.getInstance();
    if (!fileManager.isFileModified(file)) {
      return;
    }

    final com.intellij.openapi.editor.Document document;
    document = fileManager.getCachedDocument(file);
    if (document == null || !fileManager.isDocumentUnsaved(document)) {
      return;
    }

    Application application = ApplicationManager.getApplication();
    application.invokeAndWait(() -> application.runWriteAction(() -> fileManager.saveDocument(document)));
  }

  protected static Element addRootElement(@NotNull Document document, @NotNull String tag) {
    Element root = document.createElement(tag);

    //root.setAttribute(XMLNS_ANDROID, ANDROID_URI);

    // Set up a proper name space
    Attr attr = document.createAttributeNS(XMLNS_URI, XMLNS_ANDROID);
    attr.setValue(ANDROID_URI);
    root.getAttributes().setNamedItemNS(attr);

    document.appendChild(root);
    return root;
  }

  protected static Element setAndroidAttr(Element element, String name, String value) {
    element.setAttributeNS(ANDROID_URI, name, value);
    //element.setAttribute(ANDROID_NS_NAME + ':' + name, value);
    //Attr attr = element.getOwnerDocument().createAttributeNS(XMLNS_URI, XMLNS_ANDROID);
    //attr.setValue(ANDROID_URI);
    //root.getAttributes().setNamedItemNS(attr);

    return element;
  }

  public static ILayoutPullParser createEmptyParser() {
    Document document = DomPullParser.createEmptyPlainDocument();
    assert document != null;
    Element root = addRootElement(document, FRAME_LAYOUT);
    setAndroidAttr(root, ATTR_LAYOUT_WIDTH, VALUE_FILL_PARENT);
    setAndroidAttr(root, ATTR_LAYOUT_HEIGHT, VALUE_FILL_PARENT);
    return new DomPullParser(document.getDocumentElement());
  }
}
