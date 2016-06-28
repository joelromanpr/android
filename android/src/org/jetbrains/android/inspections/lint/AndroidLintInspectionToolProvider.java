package org.jetbrains.android.inspections.lint;

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.resources.ResourceUrl;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.VersionQualifier;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.actions.OverrideResourceAction;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.templates.RepositoryUrlManager;
import com.android.tools.idea.uibuilder.actions.UpgradeConstraintLayoutFix;
import com.android.tools.lint.checks.*;
import com.android.tools.lint.detector.api.Issue;
import com.google.common.collect.Lists;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.refactoring.UnusedResourcesQuickFix;
import org.jetbrains.android.sdk.AndroidSdkData;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.*;
import static com.android.tools.lint.checks.ApiDetector.REQUIRES_API_ANNOTATION;
import static com.android.tools.lint.checks.FragmentDetector.ISSUE;
import static com.android.tools.lint.checks.PluralsDetector.IMPLIED_QUANTITY;
import static com.android.tools.lint.detector.api.TextFormat.RAW;
import static com.android.xml.AndroidManifest.*;

/**
 * Registrations for all the various Lint rules as local IDE inspections, along with quickfixes for many of them
 */
public class AndroidLintInspectionToolProvider {
  public static class AndroidLintCustomErrorInspection extends AndroidLintInspectionBase {
    public AndroidLintCustomErrorInspection() {
      super("Error from Custom Lint Check", IntellijLintIssueRegistry.CUSTOM_ERROR);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      return ShowCustomIssueExplanationFix.getFixes(startElement, message);
    }
  }

  public static class AndroidLintCustomWarningInspection extends AndroidLintInspectionBase {
    public AndroidLintCustomWarningInspection() {
      super("Warning from Custom Lint Check", IntellijLintIssueRegistry.CUSTOM_WARNING);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      return ShowCustomIssueExplanationFix.getFixes(startElement, message);
    }
  }

  public static class AndroidLintAaptCrashInspection extends AndroidLintInspectionBase {
    public AndroidLintAaptCrashInspection() {
      super(AndroidBundle.message("android.lint.inspections.aapt.crash"), ResourceCycleDetector.CRASH);
    }
  }
  public static class AndroidLintInconsistentArraysInspection extends AndroidLintInspectionBase {
    public AndroidLintInconsistentArraysInspection() {
      super(AndroidBundle.message("android.lint.inspections.inconsistent.arrays"), ArraySizeDetector.INCONSISTENT);
    }
  }

  public static class AndroidLintInconsistentLayoutInspection extends AndroidLintInspectionBase {
    public AndroidLintInconsistentLayoutInspection() {
      super(AndroidBundle.message("android.lint.inspections.inconsistent.layout"), LayoutConsistencyDetector.INCONSISTENT_IDS);
    }
  }

  public static class AndroidLintDuplicateIncludedIdsInspection extends AndroidLintInspectionBase {
    public AndroidLintDuplicateIncludedIdsInspection() {
      super(AndroidBundle.message("android.lint.inspections.duplicate.included.ids"), DuplicateIdDetector.CROSS_LAYOUT);
    }
  }

  public static class AndroidLintIconExpectedSizeInspection extends AndroidLintInspectionBase {
    public AndroidLintIconExpectedSizeInspection() {
      super(AndroidBundle.message("android.lint.inspections.icon.expected.size"), IconDetector.ICON_EXPECTED_SIZE);
    }
  }

  public static class AndroidLintIconDipSizeInspection extends AndroidLintInspectionBase {
    public AndroidLintIconDipSizeInspection() {
      super(AndroidBundle.message("android.lint.inspections.icon.dip.size"), IconDetector.ICON_DIP_SIZE);
    }
  }

  public static class AndroidLintIconLocationInspection extends AndroidLintInspectionBase {
    public AndroidLintIconLocationInspection() {
      super(AndroidBundle.message("android.lint.inspections.icon.location"), IconDetector.ICON_LOCATION);
    }
  }

  public static class AndroidLintIconDensitiesInspection extends AndroidLintInspectionBase {
    public AndroidLintIconDensitiesInspection() {
      super(AndroidBundle.message("android.lint.inspections.icon.densities"), IconDetector.ICON_DENSITIES);
    }
  }

  public static class AndroidLintIconMissingDensityFolderInspection extends AndroidLintInspectionBase {
    public AndroidLintIconMissingDensityFolderInspection() {
      super(AndroidBundle.message("android.lint.inspections.icon.missing.density.folder"), IconDetector.ICON_MISSING_FOLDER);
    }
  }

  public static class AndroidLintIconMixedNinePatchInspection extends AndroidLintInspectionBase {
    public AndroidLintIconMixedNinePatchInspection() {
      super(AndroidBundle.message("android.lint.inspections.icon.mixed.nine.patch"), IconDetector.ICON_MIX_9PNG);
    }
  }

  public static class AndroidLintFloatMathInspection extends AndroidLintInspectionBase {
    public AndroidLintFloatMathInspection() {
      super(AndroidBundle.message("android.lint.inspections.float.math"), MathDetector.ISSUE);
    }
  }

  public static class AndroidLintFullBackupContentInspection extends AndroidLintInspectionBase {
    public AndroidLintFullBackupContentInspection() {
      super(AndroidBundle.message("android.lint.inspections.full.backup.content"), FullBackupContentDetector.ISSUE);
    }
  }

  public static class AndroidLintGetInstanceInspection extends AndroidLintInspectionBase {
    public AndroidLintGetInstanceInspection() {
      super(AndroidBundle.message("android.lint.inspections.get.instance"), CipherGetInstanceDetector.ISSUE);
    }
  }

  public static class AndroidLintGifUsageInspection extends AndroidLintInspectionBase {
    public AndroidLintGifUsageInspection() {
      super(AndroidBundle.message("android.lint.inspections.gif.usage"), IconDetector.GIF_USAGE);
    }
  }

  public static class AndroidLintIconDuplicatesInspection extends AndroidLintInspectionBase {
    public AndroidLintIconDuplicatesInspection() {
      super(AndroidBundle.message("android.lint.inspections.icon.duplicates"), IconDetector.DUPLICATES_NAMES);
    }
  }

  public static class AndroidLintIconDuplicatesConfigInspection extends AndroidLintInspectionBase {
    public AndroidLintIconDuplicatesConfigInspection() {
      super(AndroidBundle.message("android.lint.inspections.icon.duplicates.config"), IconDetector.DUPLICATES_CONFIGURATIONS);
    }
  }

  public static class AndroidLintIconNoDpiInspection extends AndroidLintInspectionBase {
    public AndroidLintIconNoDpiInspection() {
      super(AndroidBundle.message("android.lint.inspections.icon.no.dpi"), IconDetector.ICON_NODPI);
    }
  }

  public static class AndroidLintOverdrawInspection extends AndroidLintInspectionBase {
    public AndroidLintOverdrawInspection() {
      super(AndroidBundle.message("android.lint.inspections.overdraw"), OverdrawDetector.ISSUE);
    }
  }

  public static class AndroidLintMissingSuperCallInspection extends AndroidLintInspectionBase {
    public AndroidLintMissingSuperCallInspection() {
      super(AndroidBundle.message("android.lint.inspections.missing.super.call"), CallSuperDetector.ISSUE);
    }
  }

  public static class AndroidLintMissingTranslationInspection extends AndroidLintInspectionBase {
    public AndroidLintMissingTranslationInspection() {
      super(AndroidBundle.message("android.lint.inspections.missing.translation"), TranslationDetector.MISSING);
    }
  }

  public static class AndroidLintExtraTranslationInspection extends AndroidLintInspectionBase {
    public AndroidLintExtraTranslationInspection() {
      super(AndroidBundle.message("android.lint.inspections.extra.translation"), TranslationDetector.EXTRA);
    }
  }

  public static class AndroidLintUnusedResourcesInspection extends AndroidLintInspectionBase {
    public AndroidLintUnusedResourcesInspection() {
      super(AndroidBundle.message("android.lint.inspections.unused.resources"), UnusedResourceDetector.ISSUE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      String resource = UnusedResourceDetector.getUnusedResource(message, RAW);
      if (resource != null) {
        String resourceUrl = "@" + resource.substring(2).replace('.', '/');
        return new AndroidLintQuickFix[]{
          new UnusedResourcesQuickFix(null),
          new UnusedResourcesQuickFix(resource),
          new SetAttributeQuickFix("Add a tools:keep attribute to mark as implicitly used", ATTR_KEEP, TOOLS_URI, resourceUrl)};
      } else {
        return new AndroidLintQuickFix[] {new UnusedResourcesQuickFix(null) };
      }
    }
  }

  public static class AndroidLintUnprotectedSMSBroadcastReceiverInspection extends AndroidLintInspectionBase {
    public AndroidLintUnprotectedSMSBroadcastReceiverInspection() {
      super(AndroidBundle.message("android.lint.inspections.unprotected.smsbroadcast.receiver"), UnsafeBroadcastReceiverDetector.BROADCAST_SMS);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      return new AndroidLintQuickFix[] {
        new SetAttributeQuickFix("Set permission attribute", ATTR_PERMISSION, "android.permission.BROADCAST_SMS")
      };
    }
  }

  public static class AndroidLintUnusedAttributeInspection extends AndroidLintInspectionBase {
    public AndroidLintUnusedAttributeInspection() {
      super(AndroidBundle.message("android.lint.inspections.unused.attribute"), ApiDetector.UNUSED);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      return getApiDetectorFixes(ApiDetector.INLINED, startElement, endElement, message);
    }
  }

  public static class AndroidLintUnusedIdsInspection extends AndroidLintInspectionBase {
    public AndroidLintUnusedIdsInspection() {
      super(AndroidBundle.message("android.lint.inspections.unused.ids"), UnusedResourceDetector.ISSUE_IDS);
    }
  }

  public static class AndroidLintAlwaysShowActionInspection extends AndroidLintInspectionBase {
    public AndroidLintAlwaysShowActionInspection() {
      super(AndroidBundle.message("android.lint.inspections.always.show.action"), AlwaysShowActionDetector.ISSUE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[] { new ReplaceStringQuickFix("Replace with ifRoom", "(always)", "ifRoom") };
    }
  }

  public static class AndroidLintAppCompatMethodInspection extends AndroidLintInspectionBase {
    public AndroidLintAppCompatMethodInspection() {
      super(AndroidBundle.message("android.lint.inspections.app.compat.method"), AppCompatCallDetector.ISSUE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      String oldCall = AppCompatCallDetector.getOldCall(message, RAW);
      String newCall = AppCompatCallDetector.getNewCall(message, RAW);
      if (oldCall != null && newCall != null) {
        return new AndroidLintQuickFix[]{ new ReplaceStringQuickFix("Replace with " + newCall + "()", oldCall, newCall) };
      }
      return AndroidLintQuickFix.EMPTY_ARRAY;
    }
  }

  public static class AndroidLintAppCompatResourceInspection extends AndroidLintInspectionBase {
    public AndroidLintAppCompatResourceInspection() {
      super(AndroidBundle.message("android.lint.inspections.app.compat.resource"), AppCompatResourceDetector.ISSUE);
    }
  }

  private static AndroidLintQuickFix[] getAppIndexingQuickFix(PsiElement startElement, PsiElement endElement, String message) {
    AppIndexingApiDetector.IssueType type = AppIndexingApiDetector.IssueType.parse(message);
    switch (type) {
      case SCHEME_MISSING:
      case URL_MISSING:
        return new AndroidLintQuickFix[]{ new SetAttributeQuickFix("Set scheme", SdkConstants.ATTR_SCHEME, "http")};
      case HOST_MISSING:
        return new AndroidLintQuickFix[]{ new SetAttributeQuickFix("Set host", SdkConstants.ATTR_HOST, null)};
      case MISSING_SLASH:
        PsiElement parent = startElement.getParent();
        if (parent instanceof XmlAttribute) {
          XmlAttribute attr = (XmlAttribute) parent;
          String path = attr.getValue();
          if (path != null) {
            return new AndroidLintQuickFix[]{new ReplaceStringQuickFix("Replace with /" + path, path, "/" + path)};
          }
        }
        break;
      default:
        break;
    }
    return AndroidLintQuickFix.EMPTY_ARRAY;
  }

  public static class AndroidLintGoogleAppIndexingUrlErrorInspection extends AndroidLintInspectionBase {
    public AndroidLintGoogleAppIndexingUrlErrorInspection() {
      super(AndroidBundle.message("android.lint.inspections.google.app.indexing.url.error"), AppIndexingApiDetector.ISSUE_URL_ERROR);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      return getAppIndexingQuickFix(startElement, endElement, message);
    }
  }

  public static class AndroidLintGoogleAppIndexingWarningInspection extends AndroidLintInspectionBase {
    public AndroidLintGoogleAppIndexingWarningInspection() {
      super(AndroidBundle.message("android.lint.inspections.google.app.indexing.warning"), AppIndexingApiDetector.ISSUE_APP_INDEXING);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      return getAppIndexingQuickFix(startElement, endElement, message);
    }
  }

  public static class AndroidLintGoogleAppIndexingApiWarningInspection extends AndroidLintInspectionBase {
    public AndroidLintGoogleAppIndexingApiWarningInspection() {
      super(AndroidBundle.message("android.lint.inspections.google.app.indexing.api.warning"), AppIndexingApiDetector.ISSUE_APP_INDEXING_API);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      return getAppIndexingQuickFix(startElement, endElement, message);
    }
  }

  public static class AndroidLintAppLinksAutoVerifyErrorInspection extends AndroidLintInspectionBase {
    public AndroidLintAppLinksAutoVerifyErrorInspection() {
      super(AndroidBundle.message("android.lint.inspections.app.links.auto.verify.error"), AppLinksAutoVerifyDetector.ISSUE_ERROR);
    }
  }

  public static class AndroidLintAppLinksAutoVerifyWarningInspection extends AndroidLintInspectionBase {
    public AndroidLintAppLinksAutoVerifyWarningInspection() {
      super(AndroidBundle.message("android.lint.inspections.app.links.auto.verify.warning"), AppLinksAutoVerifyDetector.ISSUE_WARNING);
    }
  }

  public static class AndroidLintAssertInspection extends AndroidLintInspectionBase {
    public AndroidLintAssertInspection() {
      super(AndroidBundle.message("android.lint.inspections.assert"), AssertDetector.ISSUE);
    }
  }

  public static class AndroidLintStringFormatCountInspection extends AndroidLintInspectionBase {
    public AndroidLintStringFormatCountInspection() {
      super(AndroidBundle.message("android.lint.inspections.string.format.count"), StringFormatDetector.ARG_COUNT);
    }
  }

  public static class AndroidLintStringFormatMatchesInspection extends AndroidLintInspectionBase {
    public AndroidLintStringFormatMatchesInspection() {
      super(AndroidBundle.message("android.lint.inspections.string.format.matches"), StringFormatDetector.ARG_TYPES);
    }
  }

  public static class AndroidLintStringFormatInvalidInspection extends AndroidLintInspectionBase {
    public AndroidLintStringFormatInvalidInspection() {
      super(AndroidBundle.message("android.lint.inspections.string.format.invalid"), StringFormatDetector.INVALID);
    }
  }

  public static class AndroidLintWrongRegionInspection extends AndroidLintInspectionBase {
    public AndroidLintWrongRegionInspection() {
      super(AndroidBundle.message("android.lint.inspections.wrong.region"), LocaleFolderDetector.WRONG_REGION);
    }
  }

  public static class AndroidLintWrongViewCastInspection extends AndroidLintInspectionBase {
    public AndroidLintWrongViewCastInspection() {
      super(AndroidBundle.message("android.lint.inspections.wrong.view.cast"), ViewTypeDetector.ISSUE);
    }
  }

  public static class AndroidLintUnknownIdInspection extends AndroidLintInspectionBase {
    public AndroidLintUnknownIdInspection() {
      super(AndroidBundle.message("android.lint.inspections.unknown.id"), WrongIdDetector.UNKNOWN_ID);
    }
  }

  public static class AndroidLintCommitTransactionInspection extends AndroidLintInspectionBase {
    public AndroidLintCommitTransactionInspection() {
      super(AndroidBundle.message("android.lint.inspections.commit.transaction"), CleanupDetector.COMMIT_FRAGMENT);
    }
  }

  /**
   * Local inspections processed by AndroidLintExternalAnnotator
   */
  public static class AndroidLintContentDescriptionInspection extends AndroidLintInspectionBase {
    public AndroidLintContentDescriptionInspection() {
      super(AndroidBundle.message("android.lint.inspections.content.description"), AccessibilityDetector.ISSUE);
    }

    @Override
    @NotNull
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[]{
        new SetAttributeQuickFix(AndroidBundle.message("android.lint.fix.add.content.description"),
                                 SdkConstants.ATTR_CONTENT_DESCRIPTION, null)
      };
    }
  }

  public static class AndroidLintButtonOrderInspection extends AndroidLintInspectionBase {
    public AndroidLintButtonOrderInspection() {
      super(AndroidBundle.message("android.lint.inspections.button.order"), ButtonDetector.ORDER);
    }
  }

  public static class AndroidLintBackButtonInspection extends AndroidLintInspectionBase {
    public AndroidLintBackButtonInspection() {
      super(AndroidBundle.message("android.lint.inspections.back.button"), ButtonDetector.BACK_BUTTON);
    }
  }

  public static class AndroidLintBadHostnameVerifierInspection extends AndroidLintInspectionBase {
    public AndroidLintBadHostnameVerifierInspection() {
      super(AndroidBundle.message("android.lint.inspections.bad.hostname.verifier"), BadHostnameVerifierDetector.ISSUE);
    }
  }

  public static class AndroidLintBatteryLifeInspection extends AndroidLintInspectionBase {
    public AndroidLintBatteryLifeInspection() {
      super(AndroidBundle.message("android.lint.inspections.battery.life"), BatteryDetector.ISSUE);
    }
  }

  public static class AndroidLintButtonCaseInspection extends AndroidLintInspectionBase {
    public AndroidLintButtonCaseInspection() {
      super(AndroidBundle.message("android.lint.inspections.button.case"), ButtonDetector.CASE);
    }
  }

  public static class AndroidLintExtraTextInspection extends AndroidLintInspectionBase {
    public AndroidLintExtraTextInspection() {
      super(AndroidBundle.message("android.lint.inspections.extra.text"), ExtraTextDetector.ISSUE);
    }
  }

  public static class AndroidLintHandlerLeakInspection extends AndroidLintInspectionBase {
    public AndroidLintHandlerLeakInspection() {
      super(AndroidBundle.message("android.lint.inspections.handler.leak"), HandlerDetector.ISSUE);
    }
  }

  public static class AndroidLintHardcodedDebugModeInspection extends AndroidLintInspectionBase {
    public AndroidLintHardcodedDebugModeInspection() {
      super(AndroidBundle.message("android.lint.inspections.hardcoded.debug.mode"), HardcodedDebugModeDetector.ISSUE);
    }
  }

  public static class AndroidLintHardwareIdsInspection extends AndroidLintInspectionBase {
    public AndroidLintHardwareIdsInspection() {
      super(AndroidBundle.message("android.lint.inspections.hardware.ids"), HardwareIdDetector.ISSUE);
    }
  }

  public static class AndroidLintDrawAllocationInspection extends AndroidLintInspectionBase {
    public AndroidLintDrawAllocationInspection() {
      super(AndroidBundle.message("android.lint.inspections.draw.allocation"), JavaPerformanceDetector.PAINT_ALLOC);
    }
  }

  public static class AndroidLintUseSparseArraysInspection extends AndroidLintInspectionBase {
    public AndroidLintUseSparseArraysInspection() {
      super(AndroidBundle.message("android.lint.inspections.use.sparse.arrays"), JavaPerformanceDetector.USE_SPARSE_ARRAY);
    }
  }

  public static class AndroidLintUseValueOfInspection extends AndroidLintInspectionBase {
    public AndroidLintUseValueOfInspection() {
      super(AndroidBundle.message("android.lint.inspections.use.value.of"), JavaPerformanceDetector.USE_VALUE_OF);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      String replacedType = JavaPerformanceDetector.getReplacedType(message, RAW);
      if (replacedType != null) {
        return new AndroidLintQuickFix[]{new ReplaceStringQuickFix("Replace with valueOf()", "(new\\s+" + replacedType + ")",
                                                                   replacedType + ".valueOf")};
      }
      return AndroidLintQuickFix.EMPTY_ARRAY;
    }
  }

  public static class AndroidLintLibraryCustomViewInspection extends AndroidLintInspectionBase {
    public AndroidLintLibraryCustomViewInspection() {
      // TODO: Quickfix
      super(AndroidBundle.message("android.lint.inspections.library.custom.view"), NamespaceDetector.CUSTOM_VIEW);
    }
  }

  public static class AndroidLintPackageManagerGetSignaturesInspection extends AndroidLintInspectionBase {
    public AndroidLintPackageManagerGetSignaturesInspection() {
      super(AndroidBundle.message("android.lint.inspections.package.manager.get.signatures"), GetSignaturesDetector.ISSUE);
    }
  }

  public static class AndroidLintParcelClassLoaderInspection extends AndroidLintInspectionBase {
    public AndroidLintParcelClassLoaderInspection() {
      super(AndroidBundle.message("android.lint.inspections.parcel.class.loader"), ReadParcelableDetector.ISSUE);
    }

    @Override
    @NotNull
    public AndroidLintQuickFix[] getQuickFixes(@NotNull final PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      String replace = "null)";
      if (endElement.getPrevSibling() instanceof PsiJavaToken
          && ((PsiJavaToken)endElement.getPrevSibling()).getTokenType() == JavaTokenType.LPARENTH) {
        replace = ")";
      }
      return new AndroidLintQuickFix[]{new ReplaceStringQuickFix("Use getClass().getClassLoader()", replace, "getClass().getClassLoader())")};
    }
  }

  public static class AndroidLintParcelCreatorInspection extends AndroidLintInspectionBase {
    public AndroidLintParcelCreatorInspection() {
      super(AndroidBundle.message("android.lint.inspections.parcel.creator"), ParcelDetector.ISSUE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[]{
        new ParcelableQuickFix(AndroidBundle.message("implement.parcelable.intention.text"), ParcelableQuickFix.Operation.IMPLEMENT),
      };
    }
  }

  public static class AndroidLintPluralsCandidateInspection extends AndroidLintInspectionBase {
    public AndroidLintPluralsCandidateInspection() {
      super(AndroidBundle.message("android.lint.inspections.plurals.candidate"), StringFormatDetector.POTENTIAL_PLURAL);
    }
  }

  public static class AndroidLintPrivateResourceInspection extends AndroidLintInspectionBase {
    public AndroidLintPrivateResourceInspection() {
      super(AndroidBundle.message("android.lint.inspections.private.resource"), PrivateResourceDetector.ISSUE);
    }
  }

  public static class AndroidLintSdCardPathInspection extends AndroidLintInspectionBase {
    public AndroidLintSdCardPathInspection() {
      super(AndroidBundle.message("android.lint.inspections.sd.card.path"), SdCardDetector.ISSUE);
    }
  }

  public static class AndroidLintTextViewEditsInspection extends AndroidLintInspectionBase {
    public AndroidLintTextViewEditsInspection() {
      super(AndroidBundle.message("android.lint.inspections.text.view.edits"), TextViewDetector.ISSUE);
    }
  }

  public static class AndroidLintEnforceUTF8Inspection extends AndroidLintInspectionBase {
    public AndroidLintEnforceUTF8Inspection() {
      super(AndroidBundle.message("android.lint.inspections.enforce.utf8"), Utf8Detector.ISSUE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[]{new ReplaceStringQuickFix(null, null, "utf-8") };
    }
  }

  public static class AndroidLintUnknownIdInLayoutInspection extends AndroidLintInspectionBase {
    public AndroidLintUnknownIdInLayoutInspection() {
      super(AndroidBundle.message("android.lint.inspections.unknown.id.in.layout"), WrongIdDetector.UNKNOWN_ID_LAYOUT);
    }
  }

  public static class AndroidLintSuspiciousImportInspection extends AndroidLintInspectionBase {
    public AndroidLintSuspiciousImportInspection() {
      super(AndroidBundle.message("android.lint.inspections.suspicious.import"), WrongImportDetector.ISSUE);
    }
  }

  public static class AndroidLintAccidentalOctalInspection extends AndroidLintInspectionBase {
    public AndroidLintAccidentalOctalInspection() {
      super(AndroidBundle.message("android.lint.inspections.accidental.octal"), GradleDetector.ACCIDENTAL_OCTAL);
    }
  }

  public static class AndroidLintAdapterViewChildrenInspection extends AndroidLintInspectionBase {
    public AndroidLintAdapterViewChildrenInspection() {
      super(AndroidBundle.message("android.lint.inspections.adapter.view.children"), ChildCountDetector.ADAPTER_VIEW_ISSUE);
    }
  }

  public static class AndroidLintSQLiteStringInspection extends AndroidLintInspectionBase {
    public AndroidLintSQLiteStringInspection() {
      super(AndroidBundle.message("android.lint.inspections.sqlite.string"), SQLiteDetector.ISSUE);
    }
  }

  public static class AndroidLintScrollViewCountInspection extends AndroidLintInspectionBase {
    public AndroidLintScrollViewCountInspection() {
      super(AndroidBundle.message("android.lint.inspections.scroll.view.count"), ChildCountDetector.SCROLLVIEW_ISSUE);
    }
  }

  public static class AndroidLintMissingPrefixInspection extends AndroidLintInspectionBase {
    public AndroidLintMissingPrefixInspection() {
      super(AndroidBundle.message("android.lint.inspections.missing.prefix"), DetectMissingPrefix.MISSING_NAMESPACE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[]{new AddMissingPrefixQuickFix()};
    }
  }

  public static class AndroidLintMissingQuantityInspection extends AndroidLintInspectionBase {
    public AndroidLintMissingQuantityInspection() {
      // TODO: Add fixes
      super(AndroidBundle.message("android.lint.inspections.missing.quantity"), PluralsDetector.MISSING);
    }
  }

  public static class AndroidLintUnusedQuantityInspection extends AndroidLintInspectionBase {
    public AndroidLintUnusedQuantityInspection() {
      // TODO: Add fixes
      super(AndroidBundle.message("android.lint.inspections.unused.quantity"), PluralsDetector.EXTRA);
    }
  }

  public static class AndroidLintDuplicateIdsInspection extends AndroidLintInspectionBase {
    public AndroidLintDuplicateIdsInspection() {
      super(AndroidBundle.message("android.lint.inspections.duplicate.ids"), DuplicateIdDetector.WITHIN_LAYOUT);
    }
  }

  public static class AndroidLintGridLayoutInspection extends AndroidLintInspectionBase {
    public AndroidLintGridLayoutInspection() {
      super(AndroidBundle.message("android.lint.inspections.grid.layout"), GridLayoutDetector.ISSUE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull final PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      String obsolete = GridLayoutDetector.getOldValue(message, RAW);
      String available = GridLayoutDetector.getNewValue(message, RAW);
      if (obsolete != null && available != null) {
        return new AndroidLintQuickFix[]{new ReplaceStringQuickFix("Update to " + available, obsolete, available) {
          @Override
          protected void editBefore(@NotNull Document document) {
            Project project = startElement.getProject();
            final XmlFile file = PsiTreeUtil.getParentOfType(startElement, XmlFile.class);
            if (file != null) {
              AndroidResourceUtil.ensureNamespaceImported(file, AUTO_URI, null);
              PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
            }
          }
        }};
      }
      return AndroidLintQuickFix.EMPTY_ARRAY;
    }
  }

  public static class AndroidLintHardcodedTextInspection extends AndroidLintInspectionBase {
    public AndroidLintHardcodedTextInspection() {
      super(AndroidBundle.message("android.lint.inspections.hardcoded.text"), HardcodedValuesDetector.ISSUE);
    }

    @NotNull
    @Override
    public IntentionAction[] getIntentions(@NotNull final PsiElement startElement, @NotNull PsiElement endElement) {
      return new IntentionAction[]{new AndroidAddStringResourceQuickFix(startElement)};
    }
  }

  public static class AndroidLintInefficientWeightInspection extends AndroidLintInspectionBase {
    public AndroidLintInefficientWeightInspection() {
      super(AndroidBundle.message("android.lint.inspections.inefficient.weight"), InefficientWeightDetector.INEFFICIENT_WEIGHT);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[]{
        new InefficientWeightQuickFix()
      };
    }
  }

  public static class AndroidLintNestedWeightsInspection extends AndroidLintInspectionBase {
    public AndroidLintNestedWeightsInspection() {
      super(AndroidBundle.message("android.lint.inspections.nested.weights"), InefficientWeightDetector.NESTED_WEIGHTS);
    }
  }

  public static class AndroidLintDefaultLocaleInspection extends AndroidLintInspectionBase {
    public AndroidLintDefaultLocaleInspection() {
      super(AndroidBundle.message("android.lint.inspections.default.locale"), LocaleDetector.STRING_LOCALE);
    }
  }

  public static class AndroidLintDeprecatedInspection extends AndroidLintInspectionBase {
    public AndroidLintDeprecatedInspection() {
      super(AndroidBundle.message("android.lint.inspections.deprecated"), DeprecationDetector.ISSUE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      return new AndroidLintQuickFix[]{
        new SingleLineTrueQuickFix(),
        new RemoveAttributeQuickFix()
      };
    }
  }

  public static class AndroidLintDeviceAdminInspection extends AndroidLintInspectionBase {
    public AndroidLintDeviceAdminInspection() {
      // TODO: Add quickfix
      super(AndroidBundle.message("android.lint.inspections.device.admin"), ManifestDetector.DEVICE_ADMIN);
    }
  }

  public static class AndroidLintDisableBaselineAlignmentInspection extends AndroidLintInspectionBase {
    public AndroidLintDisableBaselineAlignmentInspection() {
      super(AndroidBundle.message("android.lint.inspections.disable.baseline.alignment"), InefficientWeightDetector.BASELINE_WEIGHTS);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[]{
        new SetAttributeQuickFix(AndroidBundle.message("android.lint.fix.set.baseline.attribute"),
                                 SdkConstants.ATTR_BASELINE_ALIGNED, VALUE_FALSE)
      };
    }
  }

  public static class AndroidLintManifestOrderInspection extends AndroidLintInspectionBase {
    public AndroidLintManifestOrderInspection() {
      super(AndroidBundle.message("android.lint.inspections.manifest.order"), ManifestDetector.ORDER);
    }
  }

  public static class AndroidLintMockLocationInspection extends AndroidLintInspectionBase {
    public AndroidLintMockLocationInspection() {
      super(AndroidBundle.message("android.lint.inspections.mock.location"), ManifestDetector.MOCK_LOCATION);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[]{new MoveToDebugManifestQuickFix()};
    }
  }

  public static class AndroidLintMultipleUsesSdkInspection extends AndroidLintInspectionBase {
    public AndroidLintMultipleUsesSdkInspection() {
      super(AndroidBundle.message("android.lint.inspections.multiple.uses.sdk"), ManifestDetector.MULTIPLE_USES_SDK);
    }
  }

  public static class AndroidLintUsesMinSdkAttributesInspection extends AndroidLintInspectionBase {
    public AndroidLintUsesMinSdkAttributesInspection() {
      super(AndroidBundle.message("android.lint.inspections.uses.min.sdk.attributes"), ManifestDetector.USES_SDK);
    }
  }

  public static class AndroidLintUsingHttpInspection extends AndroidLintInspectionBase {
    public AndroidLintUsingHttpInspection() {
      super(AndroidBundle.message("android.lint.inspections.using.http"), PropertyFileDetector.HTTP);
    }

    @Override
    @NotNull
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      String escaped = PropertyFileDetector.getSuggestedEscape(message, RAW);
      if (escaped != null) {
        return new AndroidLintQuickFix[]{new ReplaceStringQuickFix(null, null, escaped)};
      }
      return AndroidLintQuickFix.EMPTY_ARRAY;
    }
  }

  public static class AndroidLintValidFragmentInspection extends AndroidLintInspectionBase {
    public AndroidLintValidFragmentInspection() {
      super(AndroidBundle.message("android.lint.inspections.valid.fragment"), ISSUE);
    }
  }

  public static class AndroidLintValidRestrictionsInspection extends AndroidLintInspectionBase {
    public AndroidLintValidRestrictionsInspection() {
      super(AndroidBundle.message("android.lint.inspections.valid.restrictions"), RestrictionsDetector.ISSUE);
    }
  }

  public static class AndroidLintVectorDrawableCompatInspection extends AndroidLintInspectionBase {
    public AndroidLintVectorDrawableCompatInspection() {
      super(AndroidBundle.message("android.lint.inspections.vector.drawable.compat"), VectorDrawableCompatDetector.ISSUE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      XmlAttribute attribute = PsiTreeUtil.getParentOfType(startElement, XmlAttribute.class, false);
      if (attribute != null && ATTR_SRC.equals(attribute.getLocalName())) {
        return new AndroidLintQuickFix[] {new RenameAttributeQuickFix(AUTO_URI, ATTR_SRC_COMPAT)};
      } else {
        return AndroidLintQuickFix.EMPTY_ARRAY;
      }
    }
  }

  public static class AndroidLintVectorRasterInspection extends AndroidLintInspectionBase {
    public AndroidLintVectorRasterInspection() {
      super(AndroidBundle.message("android.lint.inspections.vector.raster"), VectorDetector.ISSUE);
    }
  }

  public static class AndroidLintViewConstructorInspection extends AndroidLintInspectionBase {
    public AndroidLintViewConstructorInspection() {
      super(AndroidBundle.message("android.lint.inspections.view.constructor"), ViewConstructorDetector.ISSUE);
    }
  }

  public static class AndroidLintViewHolderInspection extends AndroidLintInspectionBase {
    public AndroidLintViewHolderInspection() {
      super(AndroidBundle.message("android.lint.inspections.view.holder"), ViewHolderDetector.ISSUE);
    }
  }

  public static class AndroidLintViewTagInspection extends AndroidLintInspectionBase {
    public AndroidLintViewTagInspection() {
      super(AndroidBundle.message("android.lint.inspections.view.tag"), ViewTagDetector.ISSUE);
    }
  }

  public static class AndroidLintWebViewLayoutInspection extends AndroidLintInspectionBase {
    public AndroidLintWebViewLayoutInspection() {
      super(AndroidBundle.message("android.lint.inspections.web.view.layout"), WebViewDetector.ISSUE);
    }
  }

  public static class AndroidLintWearableBindListenerInspection extends AndroidLintInspectionBase {
    public AndroidLintWearableBindListenerInspection() {
      super(AndroidBundle.message("android.lint.inspections.wearable.bind.listener"), ManifestDetector.WEARABLE_BIND_LISTENER);
    }
  }

  public static class AndroidLintMergeRootFrameInspection extends AndroidLintInspectionBase {
    public AndroidLintMergeRootFrameInspection() {
      super(AndroidBundle.message("android.lint.inspections.merge.root.frame"), MergeRootFrameLayoutDetector.ISSUE);
    }
  }

  public static class AndroidLintNegativeMarginInspection extends AndroidLintInspectionBase {
    public AndroidLintNegativeMarginInspection() {
      super(AndroidBundle.message("android.lint.inspections.negative.margin"), NegativeMarginDetector.ISSUE);
    }
  }

  public static class AndroidLintNestedScrollingInspection extends AndroidLintInspectionBase {
    public AndroidLintNestedScrollingInspection() {
      super(AndroidBundle.message("android.lint.inspections.nested.scrolling"), NestedScrollingWidgetDetector.ISSUE);
    }
  }

  public static class AndroidLintNetworkSecurityConfigInspection extends AndroidLintInspectionBase {
    public AndroidLintNetworkSecurityConfigInspection() {
      super(AndroidBundle.message("android.lint.inspections.network.security.config"), NetworkSecurityConfigDetector.ISSUE);
    }
    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      if (NetworkSecurityConfigDetector.isInvalidDigestAlgorithmMessage(message)) {
        List<String> digestAlgs = NetworkSecurityConfigDetector.getSupportedPinDigestAlgorithms();
        AndroidLintQuickFix[] digestFixes = new AndroidLintQuickFix[digestAlgs.size()];
        for (int i = 0; i < digestFixes.length; i++) {
          String algorithm = digestAlgs.get(i);
          digestFixes[i] = new ReplaceStringQuickFix(String.format("Set digest to \"%1$s\"", algorithm), null, algorithm);
        }
        return digestFixes;
      }
      else if (NetworkSecurityConfigDetector.isAttributeSpellingError(message)) {
        XmlTag parentTag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class, false);
        XmlAttribute currentAttr = PsiTreeUtil.getParentOfType(startElement, XmlAttribute.class, false);
        assert parentTag != null;
        assert currentAttr != null;
        List<String> suggestions =
          NetworkSecurityConfigDetector.getAttributeSpellingSuggestions(currentAttr.getName(), parentTag.getName());
        AndroidLintQuickFix[] attrFixes = new AndroidLintQuickFix[suggestions.size()];
        for (int i = 0; i < attrFixes.length; i++) {
          attrFixes[i] = new RenameAttributeQuickFix(null /* no namespace */, suggestions.get(i));
        }
        return attrFixes;
      }
      else if (NetworkSecurityConfigDetector.isTagSpellingError(message)) {
        XmlTag currentTag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class, false);
        assert currentTag != null;
        XmlTag parentTag = currentTag.getParentTag();
        assert parentTag != null;
        List<String> suggestions =
          NetworkSecurityConfigDetector.getTagSpellingSuggestions(currentTag.getName(), parentTag.getName());
        AndroidLintQuickFix[] elementQuickFixes = new AndroidLintQuickFix[suggestions.size()];
        for (int i = 0; i < elementQuickFixes.length; i++) {
          elementQuickFixes[i] = new RenameXmlTagQuickFix(suggestions.get(i));
        }
        return elementQuickFixes;
      } else {
        return AndroidLintQuickFix.EMPTY_ARRAY;
      }
    }
  }

  public static class AndroidLintNewerVersionAvailableInspection extends AndroidLintInspectionBase {
    public AndroidLintNewerVersionAvailableInspection() {
      super(AndroidBundle.message("android.lint.inspections.newer.version.available"), GradleDetector.REMOTE_VERSION);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      String obsolete = GradleDetector.getOldValue(GradleDetector.DEPENDENCY, message, RAW);
      String available = GradleDetector.getNewValue(GradleDetector.DEPENDENCY, message, RAW);
      if (obsolete != null && available != null) {
        return new AndroidLintQuickFix[]{new ReplaceStringQuickFix("Update to " + available, obsolete, available)};
      }
      return AndroidLintQuickFix.EMPTY_ARRAY;
    }
  }

  public static class AndroidLintNfcTechWhitespaceInspection extends AndroidLintInspectionBase {
    public AndroidLintNfcTechWhitespaceInspection() {
      super(AndroidBundle.message("android.lint.inspections.nfc.tech.whitespace"), NfcTechListDetector.ISSUE);
    }
  }

  public static class AndroidLintNotInterpolatedInspection extends AndroidLintInspectionBase {
    public AndroidLintNotInterpolatedInspection() {
      super(AndroidBundle.message("android.lint.inspections.not.interpolated"), GradleDetector.NOT_INTERPOLATED);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      String s = endElement.getText();
      if (s.startsWith("'") && s.endsWith("'") && s.length() > 2) {
        return new AndroidLintQuickFix[]{new ReplaceStringQuickFix("Replace single quotes with double quotes", s,
                                                                   "\"" + s.substring(1, s.length() - 1) + "\"")};
      }
      return AndroidLintQuickFix.EMPTY_ARRAY;
    }
  }

  public static class AndroidLintNotSiblingInspection extends AndroidLintInspectionBase {
    public AndroidLintNotSiblingInspection() {
      super(AndroidBundle.message("android.lint.inspections.not.sibling"), WrongIdDetector.NOT_SIBLING);
    }
  }

  public static class AndroidLintObsoleteLayoutParamInspection extends AndroidLintInspectionBase {
    public AndroidLintObsoleteLayoutParamInspection() {
      super(AndroidBundle.message("android.lint.inspections.obsolete.layout.param"), ObsoleteLayoutParamsDetector.ISSUE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[]{new RemoveAttributeQuickFix()};
    }
  }

  public static class AndroidLintPinSetExpiryInspection extends AndroidLintInspectionBase {
    public AndroidLintPinSetExpiryInspection() {
      super(AndroidBundle.message("android.lint.inspections.pin.set.expiry"), NetworkSecurityConfigDetector.PIN_SET_EXPIRY);
    }
  }

  public static class AndroidLintProguardInspection extends AndroidLintInspectionBase {
    public AndroidLintProguardInspection() {
      super(AndroidBundle.message("android.lint.inspections.proguard"), ProguardDetector.WRONG_KEEP);
    }
  }

  public static class AndroidLintProguardSplitInspection extends AndroidLintInspectionBase {
    public AndroidLintProguardSplitInspection() {
      super(AndroidBundle.message("android.lint.inspections.proguard.split"), ProguardDetector.SPLIT_CONFIG);
    }
  }

  public static class AndroidLintPxUsageInspection extends AndroidLintInspectionBase {
    public AndroidLintPxUsageInspection() {
      super(AndroidBundle.message("android.lint.inspections.px.usage"), PxUsageDetector.PX_ISSUE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[]{new ConvertToDpQuickFix()};
    }
  }

  public static class AndroidLintScrollViewSizeInspection extends AndroidLintInspectionBase {
    public AndroidLintScrollViewSizeInspection() {
      super(AndroidBundle.message("android.lint.inspections.scroll.view.size"), ScrollViewChildDetector.ISSUE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[]{new SetScrollViewSizeQuickFix()};
    }
  }

  public static class AndroidLintExportedServiceInspection extends AndroidLintInspectionBase {
    public AndroidLintExportedServiceInspection() {
      super(AndroidBundle.message("android.lint.inspections.exported.service"), SecurityDetector.EXPORTED_SERVICE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[]{
        new SetAttributeQuickFix(AndroidBundle.message("android.lint.fix.add.permission.attribute"),
                                 SdkConstants.ATTR_PERMISSION, null)
      };
    }
  }

  public static class AndroidLintGradleCompatibleInspection extends AndroidLintInspectionBase {
    public AndroidLintGradleCompatibleInspection() {
      super(AndroidBundle.message("android.lint.inspections.gradle.compatible"), GradleDetector.COMPATIBILITY);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      String before = GradleDetector.getOldValue(GradleDetector.COMPATIBILITY, message, RAW);
      String after = GradleDetector.getNewValue(GradleDetector.COMPATIBILITY, message, RAW);
      if (before != null && after != null) {
        return new AndroidLintQuickFix[]{new ReplaceStringQuickFix("Change to " + after, before, after)};
      }

      return AndroidLintQuickFix.EMPTY_ARRAY;
    }
  }

  public static class AndroidLintGradleDependencyInspection extends AndroidLintInspectionBase {
    public AndroidLintGradleDependencyInspection() {
      super(AndroidBundle.message("android.lint.inspections.gradle.dependency"), GradleDetector.DEPENDENCY);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      String before = GradleDetector.getOldValue(GradleDetector.DEPENDENCY, message, RAW);
      String after = GradleDetector.getNewValue(GradleDetector.DEPENDENCY, message, RAW);
      if (before != null && after != null) {
        return new AndroidLintQuickFix[]{new ReplaceStringQuickFix("Change to " + after, before, after)};
      }

      return AndroidLintQuickFix.EMPTY_ARRAY;
    }
  }

  public static class AndroidLintGradleDeprecatedInspection extends AndroidLintInspectionBase {
    public AndroidLintGradleDeprecatedInspection() {
      super(AndroidBundle.message("android.lint.inspections.gradle.deprecated"), GradleDetector.DEPRECATED);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull final String message) {
      String before = GradleDetector.getOldValue(GradleDetector.DEPRECATED, message, RAW);
      String after = GradleDetector.getNewValue(GradleDetector.DEPRECATED, message, RAW);
      if (before != null && after != null) {
        return new AndroidLintQuickFix[]{new ReplaceStringQuickFix(null, before, after)};
      }
      return AndroidLintQuickFix.EMPTY_ARRAY;
    }
  }

  public static class AndroidLintGradleDynamicVersionInspection extends AndroidLintInspectionBase {
    public AndroidLintGradleDynamicVersionInspection() {
      super(AndroidBundle.message("android.lint.inspections.gradle.dynamic.version"), GradleDetector.PLUS);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull final PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      String before = GradleDetector.getOldValue(GradleDetector.PLUS, message, RAW);
      if (before != null && before.contains("+")) {
        final GradleCoordinate plus = GradleCoordinate.parseCoordinateString(before);
        if (plus != null && plus.getArtifactId() != null) {
          return new AndroidLintQuickFix[]{new ReplaceStringQuickFix("Replace with specific version", plus.getRevision(), "specific version") {
            @Nullable
            @Override
            protected String getNewValue() {
              return RepositoryUrlManager.get().resolveDynamicCoordinateVersion(plus, startElement.getProject());
            }
          }};
        }
      }
      return AndroidLintQuickFix.EMPTY_ARRAY;
    }
  }

  public static class AndroidLintGradleGetterInspection extends AndroidLintInspectionBase {
    public AndroidLintGradleGetterInspection() {
      super(AndroidBundle.message("android.lint.inspections.gradle.getter"), GradleDetector.GRADLE_GETTER);
    }
  }

  public static class AndroidLintGradleIdeErrorInspection extends AndroidLintInspectionBase {
    public AndroidLintGradleIdeErrorInspection() {
      super(AndroidBundle.message("android.lint.inspections.gradle.ide.error"), GradleDetector.IDE_SUPPORT);
    }
  }

  public static class AndroidLintGradleOverridesInspection extends AndroidLintInspectionBase {
    public AndroidLintGradleOverridesInspection() {
      super(AndroidBundle.message("android.lint.inspections.gradle.overrides"), ManifestDetector.GRADLE_OVERRIDES);
    }
  }

  public static class AndroidLintGradlePathInspection extends AndroidLintInspectionBase {
    public AndroidLintGradlePathInspection() {
      super(AndroidBundle.message("android.lint.inspections.gradle.path"), GradleDetector.PATH);
    }
  }

  public static class AndroidLintGradlePluginVersionInspection extends AndroidLintInspectionBase {
    public AndroidLintGradlePluginVersionInspection() {
      super(AndroidBundle.message("android.lint.inspections.gradle.plugin.version"), GradleDetector.GRADLE_PLUGIN_COMPATIBILITY);
    }
  }

  public static class AndroidLintGrantAllUrisInspection extends AndroidLintInspectionBase {
    public AndroidLintGrantAllUrisInspection() {
      super(AndroidBundle.message("android.lint.inspections.grant.all.uris"), SecurityDetector.OPEN_PROVIDER);
    }
  }

  public static class AndroidLintWorldWriteableFilesInspection extends AndroidLintInspectionBase {
    public AndroidLintWorldWriteableFilesInspection() {
      super(AndroidBundle.message("android.lint.inspections.world.writeable.files"), SecurityDetector.WORLD_WRITEABLE);
    }
  }

  public static class AndroidLintSSLCertificateSocketFactoryCreateSocketInspection extends AndroidLintInspectionBase {
    public AndroidLintSSLCertificateSocketFactoryCreateSocketInspection() {
      super(AndroidBundle.message("android.lint.inspections.sslcertificate.socket.factory.create.socket"), SslCertificateSocketFactoryDetector.CREATE_SOCKET);
    }
  }

  public static class AndroidLintSSLCertificateSocketFactoryGetInsecureInspection extends AndroidLintInspectionBase {
    public AndroidLintSSLCertificateSocketFactoryGetInsecureInspection() {
      super(AndroidBundle.message("android.lint.inspections.sslcertificate.socket.factory.get.insecure"), SslCertificateSocketFactoryDetector.GET_INSECURE);
    }
  }

  public static class AndroidLintStateListReachableInspection extends AndroidLintInspectionBase {
    public AndroidLintStateListReachableInspection() {
      super(AndroidBundle.message("android.lint.inspections.state.list.reachable"), StateListDetector.ISSUE);
    }
  }

  public static class AndroidLintSwitchIntDefInspection extends AndroidLintInspectionBase {
    public AndroidLintSwitchIntDefInspection() {
      super(AndroidBundle.message("android.lint.inspections.switch.int.def"), AnnotationDetector.SWITCH_TYPE_DEF);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      final List<String> missingCases = AnnotationDetector.getMissingCases(message, RAW);
      if (missingCases != null && !missingCases.isEmpty()) {
        return new AndroidLintQuickFix[]{new AndroidLintQuickFix() {
          @Override
          public void apply(@NotNull PsiElement startElement,
                            @NotNull PsiElement endElement,
                            @NotNull AndroidQuickfixContexts.Context context) {
            if (startElement.getParent() instanceof PsiSwitchStatement) {
              PsiSwitchStatement switchStatement = (PsiSwitchStatement)startElement.getParent();
              Project project = switchStatement.getProject();
              PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);

              PsiCodeBlock body = switchStatement.getBody();
              if (body == null) {
                return;
              }
              PsiElement anchor = body.getLastChild();
              for (String constant : missingCases) {
                PsiElement parent = anchor.getParent();
                PsiStatement caseStatement = factory.createStatementFromText("case " + constant + ":", anchor);
                parent.addBefore(caseStatement, anchor);
                PsiStatement breakStatement = factory.createStatementFromText("break;", anchor);
                parent.addBefore(breakStatement, anchor);
              }

              CodeStyleManager.getInstance(project).reformat(switchStatement);
            }
          }

          @Override
          public boolean isApplicable(@NotNull PsiElement startElement,
                                      @NotNull PsiElement endElement,
                                      @NotNull AndroidQuickfixContexts.ContextType contextType) {
            return startElement.isValid();
          }

          @NotNull
          @Override
          public String getName() {
            return "Add Missing @IntDef Constants";
          }
        }};
      }
      return AndroidLintQuickFix.EMPTY_ARRAY;
    }
  }

  public static class AndroidLintTextFieldsInspection extends AndroidLintInspectionBase {
    public AndroidLintTextFieldsInspection() {
      super(AndroidBundle.message("android.lint.inspections.text.fields"), TextFieldDetector.ISSUE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[]{
        new SetAttributeQuickFix(AndroidBundle.message("android.lint.fix.add.input.type.attribute"),
                                 SdkConstants.ATTR_INPUT_TYPE, null)
      };
    }
  }

  public static class AndroidLintTooManyViewsInspection extends AndroidLintInspectionBase {
    public AndroidLintTooManyViewsInspection() {
      super(AndroidBundle.message("android.lint.inspections.too.many.views"), TooManyViewsDetector.TOO_MANY);
    }
  }

  public static class AndroidLintTooDeepLayoutInspection extends AndroidLintInspectionBase {
    public AndroidLintTooDeepLayoutInspection() {
      super(AndroidBundle.message("android.lint.inspections.too.deep.layout"), TooManyViewsDetector.TOO_DEEP);
    }
  }

  public static class AndroidLintTrustAllX509TrustManagerInspection extends AndroidLintInspectionBase {
    public AndroidLintTrustAllX509TrustManagerInspection() {
      super(AndroidBundle.message("android.lint.inspections.trust.all.x509.trust.manager"), TrustAllX509TrustManagerDetector.ISSUE);
    }
  }

  public static class AndroidLintTypographyDashesInspection extends AndroidLintTypographyInspectionBase {
    public AndroidLintTypographyDashesInspection() {
      super(AndroidBundle.message("android.lint.inspections.typography.dashes"), TypographyDetector.DASHES);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[] {new TypographyQuickFix(myIssue, message)};
    }
  }

  public static class AndroidLintTypographyQuotesInspection extends AndroidLintTypographyInspectionBase {
    public AndroidLintTypographyQuotesInspection() {
      super(AndroidBundle.message("android.lint.inspections.typography.quotes"), TypographyDetector.QUOTES);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[] {new TypographyQuickFix(myIssue, message)};
    }
  }

  public static class AndroidLintTypographyFractionsInspection extends AndroidLintTypographyInspectionBase {
    public AndroidLintTypographyFractionsInspection() {
      super(AndroidBundle.message("android.lint.inspections.typography.fractions"), TypographyDetector.FRACTIONS);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[] {new TypographyQuickFix(myIssue, message)};
    }
  }

  public static class AndroidLintTypographyEllipsisInspection extends AndroidLintTypographyInspectionBase {
    public AndroidLintTypographyEllipsisInspection() {
      super(AndroidBundle.message("android.lint.inspections.typography.ellipsis"), TypographyDetector.ELLIPSIS);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[] {new TypographyQuickFix(myIssue, message)};
    }
  }

  public static class AndroidLintTypographyOtherInspection extends AndroidLintTypographyInspectionBase {
    public AndroidLintTypographyOtherInspection() {
      super(AndroidBundle.message("android.lint.inspections.typography.other"), TypographyDetector.OTHER);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[] {new TypographyQuickFix(myIssue, message)};
    }
  }

  public static class AndroidLintUseAlpha2Inspection extends AndroidLintInspectionBase {
    public AndroidLintUseAlpha2Inspection() {
      super(AndroidBundle.message("android.lint.inspections.use.alpha2"), LocaleFolderDetector.USE_ALPHA_2);
    }
  }

  public static class AndroidLintUseCompoundDrawablesInspection extends AndroidLintInspectionBase {
    public AndroidLintUseCompoundDrawablesInspection() {
      super(AndroidBundle.message("android.lint.inspections.use.compound.drawables"), UseCompoundDrawableDetector.ISSUE);
    }

    // TODO: implement quickfix
  }

  public static class AndroidLintUselessParentInspection extends AndroidLintInspectionBase {
    public AndroidLintUselessParentInspection() {
      super(AndroidBundle.message("android.lint.inspections.useless.parent"), UselessViewDetector.USELESS_PARENT);
    }

    // TODO: implement quickfix
  }

  public static class AndroidLintUselessLeafInspection extends AndroidLintInspectionBase {
    public AndroidLintUselessLeafInspection() {
      super(AndroidBundle.message("android.lint.inspections.useless.leaf"), UselessViewDetector.USELESS_LEAF);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[]{new RemoveUselessViewQuickFix(myIssue)};
    }
  }

  private abstract static class AndroidLintTypographyInspectionBase extends AndroidLintInspectionBase {
    public AndroidLintTypographyInspectionBase(String displayName, Issue issue) {
      super(displayName, issue);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[] {new TypographyQuickFix(myIssue, message)};
    }
  }

  public static class AndroidLintNewApiInspection extends AndroidLintInspectionBase {
    public AndroidLintNewApiInspection() {
      super(AndroidBundle.message("android.lint.inspections.new.api"), ApiDetector.UNSUPPORTED);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      return getApiDetectorFixes(ApiDetector.UNSUPPORTED, startElement, endElement, message);
    }
  }

  public static class AndroidLintInlinedApiInspection extends AndroidLintInspectionBase {
    public AndroidLintInlinedApiInspection() {
      super(AndroidBundle.message("android.lint.inspections.inlined.api"), ApiDetector.INLINED);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      return getApiDetectorFixes(ApiDetector.INLINED, startElement, endElement, message);
    }
  }

  private static AndroidLintQuickFix[] getApiDetectorFixes(@NotNull Issue issue,
                                                           @NotNull PsiElement startElement,
                                                           @SuppressWarnings("UnusedParameters") @NotNull PsiElement endElement,
                                                           @NotNull String message) {
    // TODO: Return one for each parent context (declaration, method, class, outer class(es)
    int api = ApiDetector.getRequiredVersion(issue, message, RAW);
    if (api != -1) {
      List<AndroidLintQuickFix> list = Lists.newArrayList();
      PsiFile file = startElement.getContainingFile();
      boolean isXml = false;
      if (file instanceof XmlFile) {
        isXml = true;
        ResourceFolderType folderType = ResourceHelper.getFolderType(file);
        if (folderType != null) {
          FolderConfiguration config = ResourceHelper.getFolderConfiguration(file);
          if (config != null) {
            config.setVersionQualifier(new VersionQualifier(api));
            String folder = config.getFolderName(folderType);
            list.add(OverrideResourceAction.createFix(folder));
          }
        }
      }

      list.add(new AddTargetVersionCheckQuickFix(api));
      list.add(new AddTargetApiQuickFix(api, false, startElement));
      ApplicationManager.getApplication().assertReadAccessAllowed();
      if (!isXml && JavaPsiFacade.getInstance(startElement.getProject()).findClass(REQUIRES_API_ANNOTATION,
                                                                       GlobalSearchScope.allScope(startElement.getProject())) != null) {
        list.add(new AddTargetApiQuickFix(api, true, startElement));
      }

      return list.toArray(new AndroidLintQuickFix[list.size()]);
    }
    return AndroidLintQuickFix.EMPTY_ARRAY;
  }

  public static class AndroidLintOverrideInspection extends AndroidLintInspectionBase {
    public AndroidLintOverrideInspection() {
      super(AndroidBundle.message("android.lint.inspections.override"), ApiDetector.OVERRIDE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      return getApiDetectorFixes(ApiDetector.INLINED, startElement, endElement, message);
    }
  }

  public static class AndroidLintDuplicateUsesFeatureInspection extends AndroidLintInspectionBase {
    public AndroidLintDuplicateUsesFeatureInspection() {
      super(AndroidBundle.message("android.lint.inspections.duplicate.uses.feature"), ManifestDetector.DUPLICATE_USES_FEATURE);
    }
  }

  public static class AndroidLintMipmapIconsInspection extends AndroidLintInspectionBase {
    public AndroidLintMipmapIconsInspection() {
      super(AndroidBundle.message("android.lint.inspections.mipmap.icons"), ManifestDetector.MIPMAP);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      PsiElement parent = startElement.getParent();
      if (parent instanceof XmlAttribute) {
        XmlAttribute attribute = (XmlAttribute)parent;
        String value = attribute.getValue();
        if (value != null) {
          ResourceUrl url = ResourceUrl.parse(value);
          if (url != null && !url.framework) {
            return new AndroidLintQuickFix[]{new MigrateDrawableToMipmapFix(url)};
          }
        }
      }
      return AndroidLintQuickFix.EMPTY_ARRAY;
    }
  }

  public static class AndroidLintMissingApplicationIconInspection extends AndroidLintInspectionBase {
    public AndroidLintMissingApplicationIconInspection() {
      super(AndroidBundle.message("android.lint.inspections.missing.application.icon"), ManifestDetector.APPLICATION_ICON);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      return new AndroidLintQuickFix[] { new SetAttributeQuickFix("Set application icon", ATTR_ICON, null) };
    }
  }

  public static class AndroidLintMissingBackupPinInspection extends AndroidLintInspectionBase {
    public AndroidLintMissingBackupPinInspection() {
      super(AndroidBundle.message("android.lint.inspections.missing.backup.pin"), NetworkSecurityConfigDetector.MISSING_BACKUP_PIN);
    }
  }

  public static class AndroidLintResourceCycleInspection extends AndroidLintInspectionBase {
    public AndroidLintResourceCycleInspection() {
      super(AndroidBundle.message("android.lint.inspections.resource.cycle"), ResourceCycleDetector.CYCLE);
    }
  }
  public static class AndroidLintResourceNameInspection extends AndroidLintInspectionBase {
    public AndroidLintResourceNameInspection() {
      super(AndroidBundle.message("android.lint.inspections.resource.name"), ResourcePrefixDetector.ISSUE);
    }
  }

  public static class AndroidLintRtlCompatInspection extends AndroidLintInspectionBase {
    private static final Pattern QUOTED_PARAMETER = Pattern.compile("`.+:(.+)=\"(.*)\"`");

    public AndroidLintRtlCompatInspection() {
      super(AndroidBundle.message("android.lint.inspections.rtl.compat"), RtlDetector.COMPAT);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      if (message.startsWith("To support older versions than API 17")) {
        Matcher matcher = QUOTED_PARAMETER.matcher(message);
        if (matcher.find()) {
          final String name = matcher.group(1);
          final String value = matcher.group(2);
          return new AndroidLintQuickFix[]{new SetAttributeQuickFix(String.format("Set %s", name), name, value)};
        }
      }
      return AndroidLintQuickFix.EMPTY_ARRAY;
    }

  }
  public static class AndroidLintRtlEnabledInspection extends AndroidLintInspectionBase {
    public AndroidLintRtlEnabledInspection() {
      super(AndroidBundle.message("android.lint.inspections.rtl.enabled"), RtlDetector.ENABLED);
    }
  }
  public static class AndroidLintRtlHardcodedInspection extends AndroidLintInspectionBase {
    public AndroidLintRtlHardcodedInspection() {
      super(AndroidBundle.message("android.lint.inspections.rtl.hardcoded"), RtlDetector.USE_START);
    }
  }
  public static class AndroidLintRtlSymmetryInspection extends AndroidLintInspectionBase {
    public AndroidLintRtlSymmetryInspection() {
      super(AndroidBundle.message("android.lint.inspections.rtl.symmetry"), RtlDetector.SYMMETRY);
    }
  }

  // Missing the following issues, because they require classfile analysis:
  // FloatMath, FieldGetter, Override, OnClick, ViewTag, DefaultLocale, SimpleDateFormat,
  // Registered, MissingRegistered, Instantiatable, HandlerLeak, ValidFragment, SecureRandom,
  // ViewConstructor, Wakelock, Recycle, CommitTransaction, WrongCall, DalvikOverride

  // I think DefaultLocale is already handled by a regular IDEA code check.

  public static class AndroidLintAddJavascriptInterfaceInspection extends AndroidLintInspectionBase {
    public AndroidLintAddJavascriptInterfaceInspection() {
      super(AndroidBundle.message("android.lint.inspections.add.javascript.interface"), AddJavascriptInterfaceDetector.ISSUE);
    }
  }

  public static class AndroidLintAllowAllHostnameVerifierInspection extends AndroidLintInspectionBase {
    public AndroidLintAllowAllHostnameVerifierInspection() {
      super(AndroidBundle.message("android.lint.inspections.allow.all.hostname.verifier"), AllowAllHostnameVerifierDetector.ISSUE);
    }
  }

  public static class AndroidLintAllowBackupInspection extends AndroidLintInspectionBase {
    public AndroidLintAllowBackupInspection() {
      super(AndroidBundle.message("android.lint.inspections.allow.backup"), ManifestDetector.ALLOW_BACKUP);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      return new AndroidLintQuickFix[] { new SetAttributeQuickFix("Set backup attribute", ATTR_ALLOW_BACKUP, null) };
    }
  }

  public static class AndroidLintButtonStyleInspection extends AndroidLintInspectionBase {
    public AndroidLintButtonStyleInspection() {
      super(AndroidBundle.message("android.lint.inspections.button.style"), ButtonDetector.STYLE);
    }
  }

  public static class AndroidLintByteOrderMarkInspection extends AndroidLintInspectionBase {
    public AndroidLintByteOrderMarkInspection() {
      super(AndroidBundle.message("android.lint.inspections.byte.order.mark"), ByteOrderMarkDetector.BOM);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      return new AndroidLintQuickFix[] {
        new DefaultLintQuickFix("Remove byte order marks") {
          @Override
          public void apply(@NotNull PsiElement startElement,
                            @NotNull PsiElement endElement,
                            @NotNull AndroidQuickfixContexts.Context context) {
            Document document = FileDocumentManager.getInstance().getDocument(startElement.getContainingFile().getVirtualFile());
            if (document != null) {
              String text = document.getText();
              for (int i = text.length() - 1; i >= 0; i--) {
                char c = text.charAt(i);
                if (c == '\uFEFF') {
                  document.deleteString(i, i + 1);
                }
              }
            }
          }
        }
      };
    }
  }

  public static class AndroidLintCommitPrefEditsInspection extends AndroidLintInspectionBase {
    public AndroidLintCommitPrefEditsInspection() {
      super(AndroidBundle.message("android.lint.inspections.commit.pref.edits"), CleanupDetector.SHARED_PREF);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      if (message.contains("commit") && message.contains("apply")) {
        return new AndroidLintQuickFix[] { new ReplaceStringQuickFix("Replace commit() with apply()", "(commit)\\s*\\(", "apply") };
      }
      return AndroidLintQuickFix.EMPTY_ARRAY;
    }
  }

  public static class AndroidLintCustomViewStyleableInspection extends AndroidLintInspectionBase {
    public AndroidLintCustomViewStyleableInspection() {
      super(AndroidBundle.message("android.lint.inspections.custom.view.styleable"), CustomViewDetector.ISSUE);
    }
  }

  public static class AndroidLintCutPasteIdInspection extends AndroidLintInspectionBase {
    public AndroidLintCutPasteIdInspection() {
      super(AndroidBundle.message("android.lint.inspections.cut.paste.id"), CutPasteDetector.ISSUE);
    }
  }
  public static class AndroidLintDuplicateActivityInspection extends AndroidLintInspectionBase {
    public AndroidLintDuplicateActivityInspection() {
      super(AndroidBundle.message("android.lint.inspections.duplicate.activity"), ManifestDetector.DUPLICATE_ACTIVITY);
    }
  }
  public static class AndroidLintDuplicateDefinitionInspection extends AndroidLintInspectionBase {
    public AndroidLintDuplicateDefinitionInspection() {
      super(AndroidBundle.message("android.lint.inspections.duplicate.definition"), DuplicateResourceDetector.ISSUE);
    }
  }
  public static class AndroidLintEasterEggInspection extends AndroidLintInspectionBase {
    public AndroidLintEasterEggInspection() {
      super(AndroidBundle.message("android.lint.inspections.easter.egg"), CommentDetector.EASTER_EGG);
    }
  }
  public static class AndroidLintExportedContentProviderInspection extends AndroidLintInspectionBase {
    public AndroidLintExportedContentProviderInspection() {
      super(AndroidBundle.message("android.lint.inspections.exported.content.provider"), SecurityDetector.EXPORTED_PROVIDER);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[] {
        new SetAttributeQuickFix("Set exported=\"false\"", ATTR_EXPORTED, VALUE_FALSE)
      };
    }
  }
  public static class AndroidLintExportedPreferenceActivityInspection extends AndroidLintInspectionBase {
    public AndroidLintExportedPreferenceActivityInspection() {
      super(AndroidBundle.message("android.lint.inspections.exported.preference.activity"), PreferenceActivityDetector.ISSUE);
    }
  }
  public static class AndroidLintExportedReceiverInspection extends AndroidLintInspectionBase {
    public AndroidLintExportedReceiverInspection() {
      super(AndroidBundle.message("android.lint.inspections.exported.receiver"), SecurityDetector.EXPORTED_RECEIVER);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[] {
        new SetAttributeQuickFix("Set permission attribute", ATTR_PERMISSION, null)
      };
    }
  }
  public static class AndroidLintIconColorsInspection extends AndroidLintInspectionBase {
    public AndroidLintIconColorsInspection() {
      super(AndroidBundle.message("android.lint.inspections.icon.colors"), IconDetector.ICON_COLORS);
    }
  }
  public static class AndroidLintIconExtensionInspection extends AndroidLintInspectionBase {
    public AndroidLintIconExtensionInspection() {
      super(AndroidBundle.message("android.lint.inspections.icon.extension"), IconDetector.ICON_EXTENSION);
    }
  }
  public static class AndroidLintIconLauncherShapeInspection extends AndroidLintInspectionBase {
    public AndroidLintIconLauncherShapeInspection() {
      super(AndroidBundle.message("android.lint.inspections.icon.launcher.shape"), IconDetector.ICON_LAUNCHER_SHAPE);
    }
  }
  public static class AndroidLintIconXmlAndPngInspection extends AndroidLintInspectionBase {
    public AndroidLintIconXmlAndPngInspection() {
      super(AndroidBundle.message("android.lint.inspections.icon.xml.and.png"), IconDetector.ICON_XML_AND_PNG);
    }
  }
  public static class AndroidLintIllegalResourceRefInspection extends AndroidLintInspectionBase {
    public AndroidLintIllegalResourceRefInspection() {
      super(AndroidBundle.message("android.lint.inspections.illegal.resource.ref"), ManifestDetector.ILLEGAL_REFERENCE);
    }
  }

  public static class AndroidLintImpliedQuantityInspection extends AndroidLintInspectionBase {
    public AndroidLintImpliedQuantityInspection() {
      super(AndroidBundle.message("android.lint.inspections.implied.quantity"), IMPLIED_QUANTITY);
    }
  }
  public static class AndroidLintAuthLeakInspection extends AndroidLintInspectionBase {
    public AndroidLintAuthLeakInspection() {
      super(AndroidBundle.message("android.lint.inspections.credential.leak"), StringAuthLeakDetector.AUTH_LEAK);
    }
  }


  public static class AndroidLintIncludeLayoutParamInspection extends AndroidLintInspectionBase {
    public AndroidLintIncludeLayoutParamInspection() {
      super(AndroidBundle.message("android.lint.inspections.include.layout.param"), IncludeDetector.ISSUE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      List<AndroidLintQuickFix> fixes = Lists.newArrayListWithExpectedSize(2);
      if (IncludeDetector.requestsWidth(message)) {
        fixes.add(new SetAttributeQuickFix("Set layout_width", ATTR_LAYOUT_WIDTH, null));
      }
      if (IncludeDetector.requestsHeight(message)) {
        fixes.add(new SetAttributeQuickFix("Set layout_height", ATTR_LAYOUT_HEIGHT, null));
      }
      return fixes.toArray(new AndroidLintQuickFix[fixes.size()]);
    }
  }

  public static class AndroidLintInflateParamsInspection extends AndroidLintInspectionBase {
    public AndroidLintInflateParamsInspection() {
      super(AndroidBundle.message("android.lint.inspections.inflate.params"), LayoutInflationDetector.ISSUE);
    }
  }
  public static class AndroidLintInOrMmUsageInspection extends AndroidLintInspectionBase {
    public AndroidLintInOrMmUsageInspection() {
      super(AndroidBundle.message("android.lint.inspections.in.or.mm.usage"), PxUsageDetector.IN_MM_ISSUE);
    }
  }
  public static class AndroidLintInnerclassSeparatorInspection extends AndroidLintInspectionBase {
    public AndroidLintInnerclassSeparatorInspection() {
      super(AndroidBundle.message("android.lint.inspections.innerclass.separator"), MissingClassDetector.INNERCLASS);
    }

    @Override
    @NotNull
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      String current = MissingClassDetector.getOldValue(MissingClassDetector.INNERCLASS, message, RAW);
      String proposed = MissingClassDetector.getNewValue(MissingClassDetector.INNERCLASS, message, RAW);
      if (proposed != null && current != null) {
        return new AndroidLintQuickFix[]{new ReplaceStringQuickFix(null, current, proposed)};
      }

      return AndroidLintQuickFix.EMPTY_ARRAY;
    }
  }

  public static class AndroidLintInvalidIdInspection extends AndroidLintInspectionBase {
    public AndroidLintInvalidIdInspection() {
      super(AndroidBundle.message("android.lint.inspections.invalid.id"), WrongIdDetector.INVALID);
    }
  }

  public static class AndroidLintInvalidResourceFolderInspection extends AndroidLintInspectionBase {
    public AndroidLintInvalidResourceFolderInspection() {
      super(AndroidBundle.message("android.lint.inspections.invalid.resource.folder"), LocaleFolderDetector.INVALID_FOLDER);
    }
  }

  public static class AndroidLintInvalidUsesTagAttributeInspection extends AndroidLintInspectionBase {
    public AndroidLintInvalidUsesTagAttributeInspection() {
      super(AndroidBundle.message("android.lint.inspections.invalid.uses.tag.attribute"), AndroidAutoDetector.INVALID_USES_TAG_ISSUE);
    }
    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      final XmlAttribute attribute = PsiTreeUtil.getParentOfType(startElement, XmlAttribute.class);
      XmlAttributeValue attributeValue = attribute == null ? null : attribute.getValueElement();
      if (attributeValue != null && attributeValue.getTextLength() != 0) {
        String value = StringUtil.unquoteString(attributeValue.getText());
        String regexp = "(" + value + ")";
        String[] suggestions = AndroidAutoDetector.getAllowedAutomotiveAppTypes();
        List<AndroidLintQuickFix> fixes = Lists.newArrayListWithExpectedSize(suggestions.length);
        for (String suggestion: suggestions) {
          fixes.add(new ReplaceStringQuickFix("Replace with \"" + suggestion + "\"", regexp, suggestion));
        }
        return fixes.toArray(new AndroidLintQuickFix[fixes.size()]);
      } else {
        return AndroidLintQuickFix.EMPTY_ARRAY;
      }
    }
  }

  public static class AndroidLintJavascriptInterfaceInspection extends AndroidLintInspectionBase {
    public AndroidLintJavascriptInterfaceInspection() {
      super(AndroidBundle.message("android.lint.inspections.javascript.interface"), JavaScriptInterfaceDetector.ISSUE);
    }
  }

  public static class AndroidLintLabelForInspection extends AndroidLintInspectionBase {
    public AndroidLintLabelForInspection() {
      super(AndroidBundle.message("android.lint.inspections.label.for"), LabelForDetector.ISSUE);
    }
  }
  public static class AndroidLintLocaleFolderInspection extends AndroidLintInspectionBase {
    public AndroidLintLocaleFolderInspection() {
      super(AndroidBundle.message("android.lint.inspections.locale.folder"), LocaleFolderDetector.DEPRECATED_CODE);
    }
  }
  public static class AndroidLintLocalSuppressInspection extends AndroidLintInspectionBase {
    public AndroidLintLocalSuppressInspection() {
      super(AndroidBundle.message("android.lint.inspections.local.suppress"), AnnotationDetector.INSIDE_METHOD);
    }
  }

  public static class AndroidLintLogConditionalInspection extends AndroidLintInspectionBase {
    public AndroidLintLogConditionalInspection() {
      super(AndroidBundle.message("android.lint.inspections.log.conditional"), LogDetector.CONDITIONAL);
    }
  }

  public static class AndroidLintLogTagMismatchInspection extends AndroidLintInspectionBase {
    public AndroidLintLogTagMismatchInspection() {
      super(AndroidBundle.message("android.lint.inspections.log.tag.mismatch"), LogDetector.WRONG_TAG);
    }
  }

  public static class AndroidLintLongLogTagInspection extends AndroidLintInspectionBase {
    public AndroidLintLongLogTagInspection() {
      super(AndroidBundle.message("android.lint.inspections.long.log.tag"), LogDetector.LONG_TAG);
    }
  }

  // THIS ISSUE IS PROBABLY NOT NEEDED HERE!
  public static class AndroidLintMangledCRLFInspection extends AndroidLintInspectionBase {
    public AndroidLintMangledCRLFInspection() {
      super(AndroidBundle.message("android.lint.inspections.mangled.crlf"), DosLineEndingDetector.ISSUE);
    }
  }

  public static class AndroidLintManifestResourceInspection extends AndroidLintInspectionBase {
    public AndroidLintManifestResourceInspection() {
      super(AndroidBundle.message("android.lint.inspections.manifest.resource"), ManifestResourceDetector.ISSUE);
    }
  }

  public static class AndroidLintMenuTitleInspection extends AndroidLintInspectionBase {
    public AndroidLintMenuTitleInspection() {
      super(AndroidBundle.message("android.lint.inspections.menu.title"), TitleDetector.ISSUE);
    }

    @Override
    @NotNull
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[] { new SetAttributeQuickFix("Set title", ATTR_TITLE, null) };
    }
  }

  public static class AndroidLintMissingConstraintsInspection extends AndroidLintInspectionBase {
    public AndroidLintMissingConstraintsInspection() {
      super(AndroidBundle.message("android.lint.inspections.missing.constraints"), ConstraintLayoutDetector.ISSUE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      if (ConstraintLayoutDetector.isUpgradeDependencyError(message, RAW)) {
        return new AndroidLintQuickFix[]{new UpgradeConstraintLayoutFix()};
      }
      else {
        return AndroidLintQuickFix.EMPTY_ARRAY;
      }
    }
  }

  public static class AndroidLintMissingIdInspection extends AndroidLintInspectionBase {
    public AndroidLintMissingIdInspection() {
      super(AndroidBundle.message("android.lint.inspections.missing.id"), MissingIdDetector.ISSUE);
    }

    @Override
    @NotNull
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[] { new SetAttributeQuickFix("Set id", ATTR_ID, null) };
    }
  }

  public static class AndroidLintMissingIntentFilterForMediaSearchInspection extends AndroidLintInspectionBase {
    public AndroidLintMissingIntentFilterForMediaSearchInspection() {
      super(AndroidBundle.message("android.lint.inspections.missing.intent.filter.for.media.search"),
            AndroidAutoDetector.MISSING_INTENT_FILTER_FOR_MEDIA_SEARCH);
    }
  }

  public static class AndroidLintMissingMediaBrowserServiceIntentFilterInspection extends AndroidLintInspectionBase {
    public AndroidLintMissingMediaBrowserServiceIntentFilterInspection() {
      super(AndroidBundle.message("android.lint.inspections.missing.media.browser.service.intent.filter"),
            AndroidAutoDetector.MISSING_MEDIA_BROWSER_SERVICE_ACTION_ISSUE);
    }
  }

  public static class AndroidLintMissingOnPlayFromSearchInspection extends AndroidLintInspectionBase {
    public AndroidLintMissingOnPlayFromSearchInspection() {
      super(AndroidBundle.message("android.lint.inspections.missing.on.play.from.search"),
            AndroidAutoDetector.MISSING_ON_PLAY_FROM_SEARCH);
    }
  }

  public static class AndroidLintMissingVersionInspection extends AndroidLintInspectionBase {
    public AndroidLintMissingVersionInspection() {
      super(AndroidBundle.message("android.lint.inspections.missing.version"), ManifestDetector.SET_VERSION);
    }
  }
  public static class AndroidLintOldTargetApiInspection extends AndroidLintInspectionBase {
    public AndroidLintOldTargetApiInspection() {
      super(AndroidBundle.message("android.lint.inspections.old.target.api"), ManifestDetector.TARGET_NEWER);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      String highest = Integer.toString(getHighestApi(startElement)); // TODO: preview platform??
      String label = "Update targetSdkVersion to " + highest;
      if (startElement.getContainingFile() instanceof XmlFile) {
        return new AndroidLintQuickFix[]{new ReplaceStringQuickFix(label, "targetSdkVersion\\s*=\\s*[\"'](.*)[\"']", highest)};
      } else if (startElement.getContainingFile() instanceof GroovyFile) {
        return new AndroidLintQuickFix[]{new ReplaceStringQuickFix(label, null, highest)};
      } else{
        return AndroidLintQuickFix.EMPTY_ARRAY;
      }
    }

    private static int getHighestApi(PsiElement element) {
      int max = SdkVersionInfo.HIGHEST_KNOWN_STABLE_API;
      AndroidFacet instance = AndroidFacet.getInstance(element);
      if (instance != null) {
        AndroidSdkData sdkData = instance.getSdkData();
        if (sdkData != null) {
          for (IAndroidTarget target : sdkData.getTargets()) {
            if (target.isPlatform()) {
              AndroidVersion version = target.getVersion();
              if (version.getApiLevel() > max && !version.isPreview()) {
                max = version.getApiLevel();
              }
            }
          }
        }
      }
      return max;
    }
  }

  public static class AndroidLintOrientationInspection extends AndroidLintInspectionBase {
    public AndroidLintOrientationInspection() {
      super(AndroidBundle.message("android.lint.inspections.orientation"), InefficientWeightDetector.ORIENTATION);
    }

    @Override
    @NotNull
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[] {
        new SetAttributeQuickFix("Set orientation=\"horizontal\" (default)", ATTR_ORIENTATION, VALUE_HORIZONTAL),
        new SetAttributeQuickFix("Set orientation=\"vertical\" (changes layout)", ATTR_ORIENTATION, VALUE_VERTICAL)
      };
    }
  }

  public static class AndroidLintOverrideAbstractInspection extends AndroidLintInspectionBase {
    public AndroidLintOverrideAbstractInspection() {
      super(AndroidBundle.message("android.lint.inspections.override.abstract"), OverrideConcreteDetector.ISSUE);
    }
  }

  public static class AndroidLintPackagedPrivateKeyInspection extends AndroidLintInspectionBase {
    public AndroidLintPackagedPrivateKeyInspection() {
      super(AndroidBundle.message("android.lint.inspections.packaged.private.key"), PrivateKeyDetector.ISSUE);
    }
  }
  public static class AndroidLintPropertyEscapeInspection extends AndroidLintInspectionBase {
    public AndroidLintPropertyEscapeInspection() {
      super(AndroidBundle.message("android.lint.inspections.property.escape"), PropertyFileDetector.ESCAPE);
    }

    @Override
    @NotNull
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      String escaped = PropertyFileDetector.getSuggestedEscape(message, RAW);
      if (escaped != null) {
        return new AndroidLintQuickFix[]{new ReplaceStringQuickFix(null, null, escaped)};
      }
      return AndroidLintQuickFix.EMPTY_ARRAY;
    }
  }

  public static class AndroidLintProtectedPermissionsInspection extends AndroidLintInspectionBase {
    public AndroidLintProtectedPermissionsInspection() {
      super(AndroidBundle.message("android.lint.inspections.protected.permissions"), SystemPermissionsDetector.ISSUE);
    }
  }

  public static class AndroidLintRecycleInspection extends AndroidLintInspectionBase {
    public AndroidLintRecycleInspection() {
      super(AndroidBundle.message("android.lint.inspections.recycle"), CleanupDetector.RECYCLE_RESOURCE);
    }
  }

  public static class AndroidLintRecyclerViewInspection extends AndroidLintInspectionBase {
    public AndroidLintRecyclerViewInspection() {
      super(AndroidBundle.message("android.lint.inspections.recycler.view"), RecyclerViewDetector.FIXED_POSITION);
    }
  }

  public static class AndroidLintReferenceTypeInspection extends AndroidLintInspectionBase {
    public AndroidLintReferenceTypeInspection() {
      super(AndroidBundle.message("android.lint.inspections.reference.type"), DuplicateResourceDetector.TYPE_MISMATCH);
    }

    @Override
    @NotNull
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      String expected = DuplicateResourceDetector.getExpectedType(message, RAW);
      if (expected != null) {
        return new AndroidLintQuickFix[]{new ReplaceStringQuickFix(null, "(@.*/)", "@" + expected + "/")};
      }
      return AndroidLintQuickFix.EMPTY_ARRAY;
    }
  }

  public static class AndroidLintRegisteredInspection extends AndroidLintInspectionBase {
    public AndroidLintRegisteredInspection() {
      super(AndroidBundle.message("android.lint.inspections.registered"), RegistrationDetector.ISSUE);
    }
  }

  public static class AndroidLintRelativeOverlapInspection extends AndroidLintInspectionBase {
    public AndroidLintRelativeOverlapInspection() {
      super(AndroidBundle.message("android.lint.inspections.relative.overlap"), RelativeOverlapDetector.ISSUE);
    }
  }

  public static class AndroidLintRequiredSizeInspection extends AndroidLintInspectionBase {
    public AndroidLintRequiredSizeInspection() {
      super(AndroidBundle.message("android.lint.inspections.required.size"), RequiredAttributeDetector.ISSUE);
    }
  }
  public static class AndroidLintResAutoInspection extends AndroidLintInspectionBase {
    public AndroidLintResAutoInspection() {
      super(AndroidBundle.message("android.lint.inspections.res.auto"), NamespaceDetector.RES_AUTO);
    }

    @Override
    @NotNull
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[] { new ConvertNamespaceQuickFix() };
    }
  }

  public static class AndroidLintSecureRandomInspection extends AndroidLintInspectionBase {
    public AndroidLintSecureRandomInspection() {
      super(AndroidBundle.message("android.lint.inspections.secure.random"), SecureRandomDetector.ISSUE);
    }
  }

  public static class AndroidLintSelectableTextInspection extends AndroidLintInspectionBase {
    public AndroidLintSelectableTextInspection() {
      super(AndroidBundle.message("android.lint.inspections.selectable.text"), TextViewDetector.SELECTABLE);
    }

    @Override
    @NotNull
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[] {
        new SetAttributeQuickFix("Set android:textIsSelectable=true", ATTR_TEXT_IS_SELECTABLE, VALUE_TRUE)
      };
    }
  }

  public static class AndroidLintServiceCastInspection extends AndroidLintInspectionBase {
    public AndroidLintServiceCastInspection() {
      super(AndroidBundle.message("android.lint.inspections.service.cast"), ServiceCastDetector.ISSUE);
    }
  }
  public static class AndroidLintSetJavaScriptEnabledInspection extends AndroidLintInspectionBase {
    public AndroidLintSetJavaScriptEnabledInspection() {
      super(AndroidBundle.message("android.lint.inspections.set.java.script.enabled"), SetJavaScriptEnabledDetector.ISSUE);
    }
  }

  public static class AndroidLintSetTextI18nInspection extends AndroidLintInspectionBase {
    public AndroidLintSetTextI18nInspection() {
      super(AndroidBundle.message("android.lint.inspections.set.text.i18n"), SetTextDetector.SET_TEXT_I18N);
    }
  }

  public static class AndroidLintSetWorldReadableInspection extends AndroidLintInspectionBase {
    public AndroidLintSetWorldReadableInspection() {
      super(AndroidBundle.message("android.lint.inspections.set.world.readable"), SecurityDetector.SET_READABLE);
    }
  }

  public static class AndroidLintSetWorldWritableInspection extends AndroidLintInspectionBase {
    public AndroidLintSetWorldWritableInspection() {
      super(AndroidBundle.message("android.lint.inspections.set.world.writable"), SecurityDetector.SET_WRITABLE);
    }
  }

  public static class AndroidLintShiftFlagsInspection extends AndroidLintInspectionBase {
    public AndroidLintShiftFlagsInspection() {
      super(AndroidBundle.message("android.lint.inspections.shift.flags"), AnnotationDetector.FLAG_STYLE);
    }
  }

  public static class AndroidLintShortAlarmInspection extends AndroidLintInspectionBase {
    public AndroidLintShortAlarmInspection() {
      super(AndroidBundle.message("android.lint.inspections.short.alarm"), AlarmDetector.ISSUE);
    }
  }

  public static class AndroidLintShowToastInspection extends AndroidLintInspectionBase {
    public AndroidLintShowToastInspection() {
      super(AndroidBundle.message("android.lint.inspections.show.toast"), ToastDetector.ISSUE);
    }
  }
  public static class AndroidLintSignatureOrSystemPermissionsInspection extends AndroidLintInspectionBase {
    public AndroidLintSignatureOrSystemPermissionsInspection() {
      super(AndroidBundle.message("android.lint.inspections.signature.or.system.permissions"), SignatureOrSystemDetector.ISSUE);
    }

    @Override
    @NotNull
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[] { new ReplaceStringQuickFix(null, "signatureOrSystem", "signature") };
    }
  }

  public static class AndroidLintSimpleDateFormatInspection extends AndroidLintInspectionBase {
    public AndroidLintSimpleDateFormatInspection() {
      super(AndroidBundle.message("android.lint.inspections.simple.date.format"), DateFormatDetector.DATE_FORMAT);
    }
  }

  public static class AndroidLintSmallSpInspection extends AndroidLintInspectionBase {
    public AndroidLintSmallSpInspection() {
      super(AndroidBundle.message("android.lint.inspections.small.sp"), PxUsageDetector.SMALL_SP_ISSUE);
    }
  }

  public static class AndroidLintSpUsageInspection extends AndroidLintInspectionBase {
    public AndroidLintSpUsageInspection() {
      super(AndroidBundle.message("android.lint.inspections.sp.usage"), PxUsageDetector.DP_ISSUE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[]{new ReplaceStringQuickFix(null, "\\d+(di?p)", "sp")};
    }
  }

  public static class AndroidLintStaticFieldLeakInspection extends AndroidLintInspectionBase {
    public AndroidLintStaticFieldLeakInspection() {
      super(AndroidBundle.message("android.lint.inspections.static.field.leak"), com.android.tools.lint.checks.LeakDetector.ISSUE);
    }
  }

  // Maybe not relevant
  public static class AndroidLintStopShipInspection extends AndroidLintInspectionBase {
    public AndroidLintStopShipInspection() {
      super(AndroidBundle.message("android.lint.inspections.stop.ship"), CommentDetector.STOP_SHIP);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      // TODO: Remove comment if that's all that remains
      return new AndroidLintQuickFix[]{new ReplaceStringQuickFix("Remove STOPSHIP", "(\\s*STOPSHIP)", "")};
    }
  }

  public static class AndroidLintStringShouldBeIntInspection extends AndroidLintInspectionBase {
    public AndroidLintStringShouldBeIntInspection() {
      super(AndroidBundle.message("android.lint.inspections.string.should.be.int"), GradleDetector.STRING_INTEGER);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      String current = GradleDetector.getOldValue(GradleDetector.STRING_INTEGER, message, RAW);
      String proposed = GradleDetector.getNewValue(GradleDetector.STRING_INTEGER, message, RAW);
      if (proposed != null && current != null) {
        return new AndroidLintQuickFix[]{new ReplaceStringQuickFix("Replace with integer", current, proposed)};
      }

      return AndroidLintQuickFix.EMPTY_ARRAY;
    }
  }

  public static class AndroidLintSupportAnnotationUsageInspection extends AndroidLintInspectionBase {
    public AndroidLintSupportAnnotationUsageInspection() {
      super(AndroidBundle.message("android.lint.inspections.support.annotation.usage"), AnnotationDetector.ANNOTATION_USAGE);
    }
  }

  public static class AndroidLintSuspicious0dpInspection extends AndroidLintInspectionBase {
    public AndroidLintSuspicious0dpInspection() {
      super(AndroidBundle.message("android.lint.inspections.suspicious0dp"), InefficientWeightDetector.WRONG_0DP);
    }
  }

  public static class AndroidLintTyposInspection extends AndroidLintInspectionBase {
    public AndroidLintTyposInspection() {
      super(AndroidBundle.message("android.lint.inspections.typos"), TypoDetector.ISSUE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      TypoDetector.TypoSuggestionInfo info = TypoDetector.getSuggestions(message, RAW);
      final List<String> suggestions = info.getReplacements();
      if (!suggestions.isEmpty()) {
        List<AndroidLintQuickFix> fixes = Lists.newArrayListWithExpectedSize(suggestions.size());
        final String originalPattern = '(' + Pattern.quote(info.getOriginal()) + ')';
        for (String suggestion : suggestions) {
          fixes.add(new ReplaceStringQuickFix("Replace with \"" + suggestion + "\"", originalPattern, suggestion));
        }
        return fixes.toArray(new AndroidLintQuickFix[fixes.size()]);
      }

      return AndroidLintQuickFix.EMPTY_ARRAY;
    }
  }

  public static class AndroidLintUniqueConstantsInspection extends AndroidLintInspectionBase {
    public AndroidLintUniqueConstantsInspection() {
      super(AndroidBundle.message("android.lint.inspections.unique.constants"), AnnotationDetector.UNIQUE);
    }
  }

  public static class AndroidLintUniquePermissionInspection extends AndroidLintInspectionBase {
    public AndroidLintUniquePermissionInspection() {
      super(AndroidBundle.message("android.lint.inspections.unique.permission"), ManifestDetector.UNIQUE_PERMISSION);
    }
  }
  public static class AndroidLintUnlocalizedSmsInspection extends AndroidLintInspectionBase {
    public AndroidLintUnlocalizedSmsInspection() {
      super(AndroidBundle.message("android.lint.inspections.unlocalized.sms"), NonInternationalizedSmsDetector.ISSUE);
    }
  }
  public static class AndroidLintWorldReadableFilesInspection extends AndroidLintInspectionBase {
    public AndroidLintWorldReadableFilesInspection() {
      super(AndroidBundle.message("android.lint.inspections.world.readable.files"), SecurityDetector.WORLD_READABLE);
    }
  }
  public static class AndroidLintWrongCallInspection extends AndroidLintInspectionBase {
    public AndroidLintWrongCallInspection() {
      super(AndroidBundle.message("android.lint.inspections.wrong.call"), WrongCallDetector.ISSUE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      String current = WrongCallDetector.getOldValue(message, RAW);
      String proposed = WrongCallDetector.getNewValue(message, RAW);
      if (proposed != null && current != null) {
        return new AndroidLintQuickFix[]{new ReplaceStringQuickFix("Replace call with " + proposed + "()", current, proposed)};
      }

      return AndroidLintQuickFix.EMPTY_ARRAY;
    }
  }

  public static class AndroidLintWrongCaseInspection extends AndroidLintInspectionBase {
    public AndroidLintWrongCaseInspection() {
      super(AndroidBundle.message("android.lint.inspections.wrong.case"), WrongCaseDetector.WRONG_CASE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      final String current = WrongCaseDetector.getOldValue(message, RAW);
      final String proposed = WrongCaseDetector.getNewValue(message, RAW);
      if (proposed != null && current != null) {
        return new AndroidLintQuickFix[]{new ReplaceStringQuickFix(null, current, proposed) {
          @Override
          protected void editAfter(@SuppressWarnings("UnusedParameters") @NotNull Document document) {
            String text = document.getText();
            int index = text.indexOf("</" + current + ">");
            if (index != -1) {
              document.replaceString(index + 2, index + 2 + current.length(), proposed);
            }
          }
        }};
      }

      return AndroidLintQuickFix.EMPTY_ARRAY;
    }
  }

  public static class AndroidLintWrongFolderInspection extends AndroidLintInspectionBase {
    public AndroidLintWrongFolderInspection() {
      super(AndroidBundle.message("android.lint.inspections.wrong.folder"), WrongLocationDetector.ISSUE);
    }
  }

  public static class AndroidLintMissingLeanbackLauncherInspection extends AndroidLintInspectionBase {
    public AndroidLintMissingLeanbackLauncherInspection() {
      super(AndroidBundle.message("android.lint.inspections.missing.leanback.launcher"), AndroidTvDetector.MISSING_LEANBACK_LAUNCHER);
    }
  }

  public static class AndroidLintMissingLeanbackSupportInspection extends AndroidLintInspectionBase {
    public AndroidLintMissingLeanbackSupportInspection() {
      super(AndroidBundle.message("android.lint.inspections.missing.leanback.support"), AndroidTvDetector.MISSING_LEANBACK_SUPPORT);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      return new AndroidLintQuickFix[]{new DefaultLintQuickFix("Add uses-feature tag") {
        @Override
        public void apply(@NotNull PsiElement startElement,
                          @NotNull PsiElement endElement,
                          @NotNull AndroidQuickfixContexts.Context context) {
          XmlTag parent = PsiTreeUtil.getParentOfType(startElement, XmlTag.class, false);
          if (parent != null) {
            XmlTag usesFeatureTag = parent.createChildTag(NODE_USES_FEATURE, null, null, false);
            usesFeatureTag = parent.addSubTag(usesFeatureTag, true);
            usesFeatureTag.setAttribute(ATTR_NAME, ANDROID_URI, AndroidTvDetector.SOFTWARE_FEATURE_LEANBACK);
            usesFeatureTag.setAttribute(ATTRIBUTE_REQUIRED, ANDROID_URI, String.valueOf(false));
          }
        }
      }};
    }
  }

  public static class AndroidLintMissingTvBannerInspection extends AndroidLintInspectionBase {
    public AndroidLintMissingTvBannerInspection() {
      super(AndroidBundle.message("android.lint.inspections.missing.tv.banner"), AndroidTvDetector.MISSING_BANNER);
    }
    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      return new AndroidLintQuickFix[] { new SetAttributeQuickFix("Set banner attribute", "banner", null) };
    }
  }

  public static class AndroidLintPendingBindingsInspection extends AndroidLintInspectionBase {
    public AndroidLintPendingBindingsInspection() {
      super(AndroidBundle.message("android.lint.inspections.pending.bindings"), RecyclerViewDetector.DATA_BINDER);
    }
  }

  public static class AndroidLintPermissionImpliesUnsupportedHardwareInspection extends AndroidLintInspectionBase {
    public AndroidLintPermissionImpliesUnsupportedHardwareInspection() {
      super(AndroidBundle.message("android.lint.inspections.permission.implies.unsupported.hardware"), AndroidTvDetector.PERMISSION_IMPLIES_UNSUPPORTED_HARDWARE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull String message) {
      final String hardwareFeatureName = AndroidTvDetector.getHardwareFeature(message, RAW);
      if (hardwareFeatureName != null) {
        return new AndroidLintQuickFix[] {
          new DefaultLintQuickFix("Add uses-feature tag") {
            @Override
            public void apply(@NotNull PsiElement startElement, @NotNull PsiElement endElement,
                              @NotNull AndroidQuickfixContexts.Context context) {
              XmlTag usesPermissionTag = PsiTreeUtil.getParentOfType(startElement, XmlTag.class, false);
              if (startElement.textMatches(NODE_USES_PERMISSION) && usesPermissionTag != null) {
                XmlTag parent = (XmlTag)usesPermissionTag.getParent();
                if (parent != null) {
                  XmlTag usesFeatureTag = parent.createChildTag(NODE_USES_FEATURE, null, null, false);
                  usesFeatureTag = parent.addSubTag(usesFeatureTag, true);
                  usesFeatureTag.setAttribute(ATTR_NAME, ANDROID_URI, hardwareFeatureName);
                  usesFeatureTag.setAttribute(ATTRIBUTE_REQUIRED, ANDROID_URI, String.valueOf(false));
                }
              }
            }
          }
        };
      } else {
        return AndroidLintQuickFix.EMPTY_ARRAY;
      }
    }
  }

  public static class AndroidLintUnsafeDynamicallyLoadedCodeInspection extends AndroidLintInspectionBase {
    public AndroidLintUnsafeDynamicallyLoadedCodeInspection() {
      super(AndroidBundle.message("android.lint.inspections.unsafe.dynamically.loaded.code"), UnsafeNativeCodeDetector.LOAD);
    }
  }

  public static class AndroidLintUnsafeNativeCodeLocationInspection extends AndroidLintInspectionBase {
    public AndroidLintUnsafeNativeCodeLocationInspection() {
      super(AndroidBundle.message("android.lint.inspections.unsafe.native.code.location"), UnsafeNativeCodeDetector.UNSAFE_NATIVE_CODE_LOCATION);
    }
  }

  public static class AndroidLintUnsafeProtectedBroadcastReceiverInspection extends AndroidLintInspectionBase {
    public AndroidLintUnsafeProtectedBroadcastReceiverInspection() {
      super(AndroidBundle.message("android.lint.inspections.unsafe.protected.broadcast.receiver"), UnsafeBroadcastReceiverDetector.ACTION_STRING);
    }
  }

  public static class AndroidLintUnsupportedTvHardwareInspection extends AndroidLintInspectionBase {
    public AndroidLintUnsupportedTvHardwareInspection() {
      super(AndroidBundle.message("android.lint.inspections.unsupported.tv.hardware"), AndroidTvDetector.UNSUPPORTED_TV_HARDWARE);
    }

    @NotNull
    @Override
    public AndroidLintQuickFix[] getQuickFixes(@NotNull PsiElement startElement, @NotNull PsiElement endElement, @NotNull String message) {
      if (startElement.textMatches(PREFIX_ANDROID + ATTRIBUTE_REQUIRED)) {
        // android:required attribute
        XmlAttribute attr = PsiTreeUtil.getParentOfType(startElement, XmlAttribute.class);
        assert attr != null;
        return new AndroidLintQuickFix[] {
          new ReplaceStringQuickFix("Replace with required=\"false\"",
                                    null,
                                    PREFIX_ANDROID + ATTRIBUTE_REQUIRED + "=\"" + false + "\""),
        };
      } else if (startElement.textMatches(NODE_USES_FEATURE)) {
        return new AndroidLintQuickFix[]{new SetAttributeQuickFix("Set required=\"false\"", ATTRIBUTE_REQUIRED, VALUE_FALSE)};
      }
      return AndroidLintQuickFix.EMPTY_ARRAY;
    }
  }
}
