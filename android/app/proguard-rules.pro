# shared_preferences_android 2.5+ looks up Kotlin suspend methods through JNI.
# Keeping the bridge alone does not keep its parameter type names: R8 can rename
# kotlin.coroutines.Continuation and change getAll's JVM descriptor, causing a
# NoSuchMethodError even on a fresh installation. Preserve the descriptor types
# together with the plugin's generated bridge; keep release shrinking enabled.
-keep,includedescriptorclasses class io.flutter.plugins.sharedpreferences.** { *; }

# package:jni's Kotlin support also looks these up by name (lib/src/kotlin.dart),
# including coroutine suspension and failure/result handling outside the plugin.
-keep class kotlin.coroutines.** { *; }
-keep class kotlin.Result { *; }
-keep class kotlin.Result$Failure { *; }
-keep class kotlin.Unit { *; }
