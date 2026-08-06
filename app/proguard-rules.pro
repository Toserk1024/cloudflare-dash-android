# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ===== R8 开启后的关键 keep 规则 =====
# kotlinx.serialization 官方规则（保留序列化器，防止 R8 后运行时崩溃）
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class io.github.toserk1024.cfdash.**$$serializer { *; }
-keepclassmembers class io.github.toserk1024.cfdash.** { *** Companion; }
-keepclasseswithmembers class io.github.toserk1024.cfdash.** { kotlinx.serialization.KSerializer serializer(...); }

# 保留行号，便于 R8 后崩溃排障
-keepattributes SourceFile,LineNumberTable

# ===== Tink(security-crypto) 依赖的编译期注解类，运行时不需要 =====
# R8 分析 com.google.crypto.tink 时找不到 errorprone/jsr305/animal-sniffer 注解类，需忽略
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**