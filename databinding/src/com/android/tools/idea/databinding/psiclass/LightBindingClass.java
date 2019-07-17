/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.databinding.psiclass;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_ID;
import static com.android.ide.common.resources.ResourcesUtil.stripPrefixFromId;

import com.android.SdkConstants;
import com.android.tools.idea.databinding.DataBindingUtil;
import com.android.tools.idea.databinding.cache.ResourceCacheValueProvider;
import com.android.tools.idea.databinding.index.ViewIdInfo;
import com.android.tools.idea.res.binding.BindingLayoutXml;
import com.android.tools.idea.res.binding.PsiDataBindingResourceItem;
import com.google.common.collect.ImmutableSet;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReferenceList;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.XmlRecursiveElementWalkingVisitor;
import com.intellij.psi.impl.light.LightField;
import com.intellij.psi.impl.light.LightFieldBuilder;
import com.intellij.psi.impl.light.LightIdentifier;
import com.intellij.psi.impl.light.LightMethod;
import com.intellij.psi.impl.light.LightMethodBuilder;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlTag;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.android.augment.AndroidLightClassBase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * In-memory PSI for classes generated from a layout file (or a list of related layout files from
 * different configurations)
 * <p>
 * See also: https://developer.android.com/topic/libraries/data-binding/expressions#binding_data
 * <p>
 * In the case of common, single-config layouts, only a single "Binding" class will be generated.
 * However, if there are multi-config layouts, e.g. "layout" and "layout-land", a base "Binding"
 * class as well as layout-specific implementations, e.g. "BindingImpl", "BindingLandImpl", will
 * be generated.
 */
public class LightBindingClass extends AndroidLightClassBase {
  private final Object myCacheLock = new Object();

  private final LightBindingClassConfig myConfig;

  private CachedValue<PsiMethod[]> myPsiMethodsCache;
  private CachedValue<PsiField[]> myPsiFieldsCache;

  private PsiReferenceList myExtendsList;
  private PsiClassType[] myExtendsListTypes;

  public LightBindingClass(@NotNull PsiManager psiManager, @NotNull LightBindingClassConfig config) {
    super(psiManager, ImmutableSet.of(PsiModifier.PUBLIC, PsiModifier.FINAL));
    myConfig = config;
    setModuleInfo(getFacet().getModule(), false);

    CachedValuesManager cachedValuesManager = CachedValuesManager.getManager(getProject());
    myPsiMethodsCache =
      cachedValuesManager.createCachedValue(new ResourceCacheValueProvider<PsiMethod[]>(getFacet(), myCacheLock) {
        @Override
        protected PsiMethod[] doCompute() {
          List<PsiMethod> methods = new ArrayList<>();

          PsiMethod constructor = createConstructor();
          methods.add(constructor);

          for (PsiDataBindingResourceItem variable : myConfig.getPsiVariables()) {
            createVariableMethods(variable, methods);
          }

          if (myConfig.shouldGenerateGettersAndStaticMethods()) {
            PsiElementFactory factory = PsiElementFactory.getInstance(getProject());
            createStaticMethods(factory.createType(LightBindingClass.this), methods);
          }

          return methods.toArray(PsiMethod.EMPTY_ARRAY);
        }

        @Override
        protected PsiMethod[] defaultValue() {
          return PsiMethod.EMPTY_ARRAY;
        }
      }, false);

    myPsiFieldsCache = cachedValuesManager
      .createCachedValue(() -> CachedValueProvider.Result.create(computeFields(), PsiModificationTracker.MODIFICATION_COUNT));
  }

  /**
   * Convenience method to grab the facet out of the config, since it is needed so often
   */
  @NotNull
  private AndroidFacet getFacet() {
    return myConfig.getTargetLayout().getPsi().getFacet();
  }

  private PsiField[] computeFields() {
    List<ViewIdInfo> viewIds = myConfig.getViewIds();
    if (viewIds.isEmpty()) {
      return PsiField.EMPTY_ARRAY;
    }

    return viewIds.stream().map(viewId -> createPsiField(viewId)).toArray(PsiField[]::new);
  }

  /**
   * Creates a private no-argument constructor.
   */
  @NotNull
  private PsiMethod createConstructor() {
    LightMethodBuilder constructor = new LightMethodBuilder(this, JavaLanguage.INSTANCE);
    constructor.setConstructor(true);
    constructor.addModifier("private");
    return constructor;
  }

  @NotNull
  @Override
  public String getQualifiedName() {
    return myConfig.getQualifiedName();
  }

  @Nullable
  @Override
  public PsiClass getContainingClass() {
    return null;
  }

  @NotNull
  @Override
  public PsiField[] getFields() {
    return myPsiFieldsCache.getValue();
  }

  @NotNull
  @Override
  public PsiField[] getAllFields() {
    return getFields();
  }

  @NotNull
  @Override
  public PsiMethod[] getMethods() {
    return myPsiMethodsCache.getValue();
  }

  @Override
  public PsiClass getSuperClass() {
    return JavaPsiFacade.getInstance(getProject())
        .findClass(myConfig.getSuperName(), getFacet().getModule().getModuleWithDependenciesAndLibrariesScope(false));
  }

  @Override
  public PsiReferenceList getExtendsList() {
    if (myExtendsList == null) {
      PsiElementFactory factory = PsiElementFactory.getInstance(getProject());
      PsiJavaCodeReferenceElement referenceElementByType = factory.createReferenceElementByType(getExtendsListTypes()[0]);
      myExtendsList = factory.createReferenceList(new PsiJavaCodeReferenceElement[]{referenceElementByType});
    }
    return myExtendsList;
  }

  @NotNull
  @Override
  public PsiClassType[] getSuperTypes() {
    return getExtendsListTypes();
  }

  @NotNull
  @Override
  public PsiClassType[] getExtendsListTypes() {
    if (myExtendsListTypes == null) {
      myExtendsListTypes = new PsiClassType[]{
        PsiType.getTypeByName(myConfig.getSuperName(), getProject(),
                              getFacet().getModule().getModuleWithDependenciesAndLibrariesScope(false))};
    }
    return myExtendsListTypes;
  }


  @NotNull
  @Override
  public PsiMethod[] getAllMethods() {
    return getMethods();
  }

  @NotNull
  @Override
  public PsiMethod[] findMethodsByName(@NonNls String name, boolean checkBases) {
    List<PsiMethod> matched = null;
    for (PsiMethod method : getMethods()) {
      if (name.equals(method.getName())) {
        if (matched == null) {
          matched = new ArrayList<>();
        }
        matched.add(method);
      }
    }
    return matched == null ? PsiMethod.EMPTY_ARRAY : matched.toArray(PsiMethod.EMPTY_ARRAY);
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    boolean continueProcessing = super.processDeclarations(processor, state, lastParent, place);
    if (!continueProcessing) {
      return false;
    }
    List<BindingLayoutXml.Import> imports = myConfig.getTargetLayout().getXml().getImports();
    if (imports.isEmpty()) {
      return true;
    }
    ElementClassHint classHint = processor.getHint(ElementClassHint.KEY);
    if (classHint != null && classHint.shouldProcess(ElementClassHint.DeclarationKind.CLASS)) {
      NameHint nameHint = processor.getHint(NameHint.KEY);
      String name = nameHint != null ? nameHint.getName(state) : null;
      for (BindingLayoutXml.Import imp : imports) {
        if (imp.getAlias() != null) {
          continue; // Aliases are pre-resolved in replaceImportAliases.
        }
        String qName = imp.getType();
        if (name != null && !qName.endsWith(name)) {
          continue;
        }

        Module module = myConfig.getTargetLayout().getPsi().getModule();
        if (module == null) {
          return true; // this should not really happen but just to be safe
        }
        PsiClass aClass = JavaPsiFacade.getInstance(getProject()).findClass(qName, module
          .getModuleWithDependenciesAndLibrariesScope(true));
        if (aClass != null) {
          if (!processor.execute(aClass, state)) {
            // found it!
            return false;
          }
        }
      }
    }
    return true;
  }

  private void createVariableMethods(@NotNull PsiDataBindingResourceItem item, @NotNull List<PsiMethod> outPsiMethods) {
    PsiManager psiManager = getManager();

    String typeName = item.getExtra(SdkConstants.ATTR_TYPE);
    String variableType = DataBindingUtil.getQualifiedType(typeName, myConfig.getTargetLayout(), true);
    if (variableType == null) {
      return;
    }
    PsiType type = DataBindingUtil.parsePsiType(variableType, getFacet(), this);
    if (type == null) {
      return;
    }

    String javaName = DataBindingUtil.convertToJavaFieldName(item.getName());
    String capitalizedName = StringUtil.capitalize(javaName);
    LightMethodBuilder setter = createPublicMethod("set" + capitalizedName, PsiType.VOID);
    setter.addParameter(javaName, type);
    if (myConfig.settersShouldBeAbstract()) {
      setter.addModifier("abstract");
    }
    outPsiMethods.add(new LightDataBindingMethod(item.getXmlTag(), psiManager, setter, this, JavaLanguage.INSTANCE));

    if (myConfig.shouldGenerateGettersAndStaticMethods()) {
      LightMethodBuilder getter = createPublicMethod("get" + capitalizedName, type);
      outPsiMethods.add(new LightDataBindingMethod(item.getXmlTag(), psiManager, getter, this, JavaLanguage.INSTANCE));
    }
  }

  private void createStaticMethods(@NotNull PsiClassType ownerType, @NotNull List<PsiMethod> outPsiMethods) {
    Project project = getProject();
    Module module = getFacet().getModule();
    PsiClassType viewGroupType =
        PsiType.getTypeByName(SdkConstants.CLASS_VIEWGROUP, project, module.getModuleWithDependenciesAndLibrariesScope(true));
    PsiClassType layoutInflaterType =
        PsiType.getTypeByName(SdkConstants.CLASS_LAYOUT_INFLATER, project, module.getModuleWithDependenciesAndLibrariesScope(true));
    PsiClassType dataBindingComponent =
        PsiType.getJavaLangObject(getManager(), module.getModuleWithDependenciesAndLibrariesScope(true));
    PsiClassType viewType =
        PsiType.getTypeByName(SdkConstants.CLASS_VIEW, project, module.getModuleWithDependenciesAndLibrariesScope(true));

    DeprecatableLightMethodBuilder inflate4Arg = createPublicStaticMethod("inflate", ownerType);
    inflate4Arg.addParameter("inflater", layoutInflaterType);
    inflate4Arg.addParameter("root", viewGroupType);
    inflate4Arg.addParameter("attachToRoot", PsiType.BOOLEAN);
    inflate4Arg.addParameter("bindingComponent", dataBindingComponent);
    // methods receiving DataBindingComponent are deprecated. see: b/116541301
    inflate4Arg.setDeprecated(true);

    LightMethodBuilder inflate3Arg = createPublicStaticMethod("inflate", ownerType);
    inflate3Arg.addParameter("inflater", layoutInflaterType);
    inflate3Arg.addParameter("root", viewGroupType);
    inflate3Arg.addParameter("attachToRoot", PsiType.BOOLEAN);

    DeprecatableLightMethodBuilder inflate2Arg = createPublicStaticMethod("inflate", ownerType);
    inflate2Arg.addParameter("inflater", layoutInflaterType);
    inflate2Arg.addParameter("bindingComponent", dataBindingComponent);
    // methods receiving DataBindingComponent are deprecated. see: b/116541301
    inflate2Arg.setDeprecated(true);

    LightMethodBuilder inflate1Arg = createPublicStaticMethod("inflate", ownerType);
    inflate1Arg.addParameter("inflater", layoutInflaterType);

    LightMethodBuilder bind = createPublicStaticMethod("bind", ownerType);
    bind.addParameter("view", viewType);

    DeprecatableLightMethodBuilder bindWithComponent = createPublicStaticMethod("bind", ownerType);
    bindWithComponent.addParameter("view", viewType);
    bindWithComponent.addParameter("bindingComponent", dataBindingComponent);
    // methods receiving DataBindingComponent are deprecated. see: b/116541301
    bindWithComponent.setDeprecated(true);

    PsiManager psiManager = getManager();
    PsiMethod[] methods = new PsiMethod[]{inflate1Arg, inflate2Arg, inflate3Arg, inflate4Arg, bind, bindWithComponent};
    for (PsiMethod method : methods) {
      outPsiMethods.add(
        new LightDataBindingMethod(myConfig.getTargetLayout().getPsi().getXmlPsiFile(), psiManager, method, this, JavaLanguage.INSTANCE));
    }
  }

  @NotNull
  private DeprecatableLightMethodBuilder createPublicStaticMethod(@NotNull String name, @NotNull PsiType returnType) {
    DeprecatableLightMethodBuilder method = createPublicMethod(name, returnType);
    method.addModifier("static");
    return method;
  }

  @NotNull
  private DeprecatableLightMethodBuilder createPublicMethod(@NotNull String name, @NotNull PsiType returnType) {
    DeprecatableLightMethodBuilder method = new DeprecatableLightMethodBuilder(getManager(), JavaLanguage.INSTANCE, name);
    method.setContainingClass(this);
    method.setMethodReturnType(returnType);
    method.addModifier("public");
    return method;
  }

  @Nullable
  private PsiField createPsiField(@NotNull ViewIdInfo idInfo) {
    String name = DataBindingUtil.convertToJavaFieldName(idInfo.getId());
    PsiType type = DataBindingUtil.resolveViewPsiType(idInfo, getFacet());
    if (type == null) {
      return null;
    }
    LightFieldBuilder field = new LightFieldBuilder(PsiManager.getInstance(getProject()), name, type);
    field.setModifiers("public", "final");
    return new LightDataBindingField(idInfo, getManager(), field, this);
  }

  @Override
  public boolean isInterface() {
    return false;
  }

  @NotNull
  @Override
  public PsiElement getNavigationElement() {
    return myConfig.getTargetLayout().getPsi().getNavigationElement();
  }

  @Override
  @NotNull
  public String getName() {
    return myConfig.getClassName();
  }

  @Nullable
  @Override
  public PsiFile getContainingFile() {
    return myConfig.getTargetLayout().getPsi().getXmlPsiFile();
  }

  @Override
  public boolean isValid() {
    // it is always valid. Not having this valid creates IDE errors because it is not always resolved instantly
    return true;
  }

  /**
   * The light method class that represents the generated data binding methods for a layout file.
   */
  public static class LightDataBindingMethod extends LightMethod {
    private PsiElement myNavigationElement;

    public LightDataBindingMethod(@NotNull PsiElement navigationElement,
                                  @NotNull PsiManager manager,
                                  @NotNull PsiMethod method,
                                  @NotNull PsiClass containingClass,
                                  @NotNull Language language) {
      super(manager, method, containingClass, language);
      myNavigationElement = navigationElement;
    }

    @Override
    public TextRange getTextRange() {
      return TextRange.EMPTY_RANGE;
    }

    @NotNull
    @Override
    public PsiElement getNavigationElement() {
      return myNavigationElement;
    }

    @Override
    public PsiIdentifier getNameIdentifier() {
      return new LightIdentifier(getManager(), getName());
    }
  }

  /**
   * The light field class that represents the generated view fields for a layout file.
   */
  public static class LightDataBindingField extends LightField {
    private final ViewIdInfo myViewIdInfo;

    private final CachedValue<XmlTag> tagCache = CachedValuesManager.getManager(getProject())
      .createCachedValue(() -> CachedValueProvider.Result.create(computeTag(), PsiModificationTracker.MODIFICATION_COUNT));

    public LightDataBindingField(@NotNull ViewIdInfo viewIdInfo,
                                 @NotNull PsiManager manager,
                                 @NotNull PsiField field,
                                 @NotNull PsiClass containingClass) {
      super(manager, field, containingClass);
      myViewIdInfo = viewIdInfo;
    }

    @Nullable
    private XmlTag computeTag() {
      final Ref<XmlTag> resultTag = new Ref<>();
      if (getContainingFile() != null) {
        getContainingFile().accept(new XmlRecursiveElementWalkingVisitor() {
          @Override
          public void visitXmlTag(XmlTag tag) {
            super.visitXmlTag(tag);
            String idValue = tag.getAttributeValue(ATTR_ID, ANDROID_URI);
            if (idValue != null && myViewIdInfo.getId().equals(stripPrefixFromId(idValue))) {
              resultTag.set(tag);
              stopWalking();
            }
          }
        });
      }
      return resultTag.get();
    }

    @Override
    @Nullable
    public PsiFile getContainingFile() {
      PsiClass containingClass = super.getContainingClass();
      return containingClass == null ? null : containingClass.getContainingFile();
    }

    @Override
    public TextRange getTextRange() {
      return TextRange.EMPTY_RANGE;
    }

    @Override
    @NotNull
    public PsiElement getNavigationElement() {
      return tagCache.getValue();
    }

    @Override
    @NotNull
    public PsiElement setName(@NotNull String name) {
      // This method is called by rename refactoring and has to succeed in order for the refactoring to succeed.
      // There no need to change the name since once the refactoring is complete, this object will be replaced
      // by a new one reflecting the changed source code.
      return this;
    }
  }
}
