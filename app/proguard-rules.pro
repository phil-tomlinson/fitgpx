# FitGPX release shrinking rules.
# fit-core and the app use no reflection; these rules only cover third-party libraries.

# osmdroid loads tile providers and overlays reflectively in a few places.
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Keep line numbers so crash reports from users are useful; hide original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
