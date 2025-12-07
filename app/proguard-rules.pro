-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keep class com.bilidownloader.app.data.model.** { *; }

-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}