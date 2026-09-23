# Keep Room entities/DAOs (annotation processed, but be defensive under R8).
-keep class com.bookwormbliss.app.data.model.** { *; }
-keep class com.bookwormbliss.app.data.db.** { *; }

# Kotlin coroutines / metadata.
-keepattributes *Annotation*, InnerClasses, Signature
-dontwarn kotlinx.coroutines.**
