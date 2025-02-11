package org.jetbrains.android.formatter;

public class ContextSpecificSettingsProviders {
  public static final Provider<AndroidXmlCodeStyleSettings.LayoutSettings> LAYOUT =
    new Provider<>() {
      @Override
      public AndroidXmlCodeStyleSettings.LayoutSettings getSettings(AndroidXmlCodeStyleSettings baseSettings) {
        return baseSettings.LAYOUT_SETTINGS;
      }
    };

  public static final Provider<AndroidXmlCodeStyleSettings.ManifestSettings> MANIFEST =
    new Provider<>() {
      @Override
      public AndroidXmlCodeStyleSettings.ManifestSettings getSettings(AndroidXmlCodeStyleSettings baseSettings) {
        return baseSettings.MANIFEST_SETTINGS;
      }
    };

  public static final Provider<AndroidXmlCodeStyleSettings.ValueResourceFileSettings> VALUE_RESOURCE_FILE =
    new Provider<>() {
      @Override
      public AndroidXmlCodeStyleSettings.ValueResourceFileSettings getSettings(AndroidXmlCodeStyleSettings baseSettings) {
        return baseSettings.VALUE_RESOURCE_FILE_SETTINGS;
      }
    };

  public static final Provider<AndroidXmlCodeStyleSettings.OtherSettings> OTHER =
    new Provider<>() {
      @Override
      public AndroidXmlCodeStyleSettings.OtherSettings getSettings(AndroidXmlCodeStyleSettings baseSettings) {
        return baseSettings.OTHER_SETTINGS;
      }
    };

  abstract static class Provider<T extends AndroidXmlCodeStyleSettings.MySettings> {
    abstract T getSettings(AndroidXmlCodeStyleSettings baseSettings);
  }
}
