# Keep LiteRT-LM's JNI-bound classes intact -- native code looks these up by name.
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
