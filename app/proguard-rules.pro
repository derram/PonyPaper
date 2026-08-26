# Manifest components are kept by AAPT; these broad keeps avoid surprises if
# wallpaper/settings code is only reached via framework callbacks.
-keep class uk.cpjsmith.ponypaper.PonyWallpaper { *; }
-keep class uk.cpjsmith.ponypaper.Settings { *; }

# Preference headers load fragments by class name (app:fragment=...). AAPT does
# not emit keep rules for those attributes, so R8 would strip them otherwise.
-keep class uk.cpjsmith.ponypaper.**PreferencesFragment { <init>(); }
