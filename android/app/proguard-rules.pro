# kotlinx.serialization keeps its generated serializers via the plugin; keep model classes' names for safety.
-keep,includedescriptorclasses class com.onezcodes.ochords.model.**$$serializer { *; }
-keepclassmembers class com.onezcodes.ochords.model.** { *** Companion; }
-keepclasseswithmembers class com.onezcodes.ochords.model.** { kotlinx.serialization.KSerializer serializer(...); }
