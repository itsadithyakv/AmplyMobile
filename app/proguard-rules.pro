# Keep Room schema/runtime metadata available to generated code.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# Media3 sessions are reached by Android framework and external controllers.
-keep class com.amply.mobile.playback.AmplyPlaybackService { *; }
-keep class com.amply.mobile.widget.** { *; }
-keep class com.amply.mobile.worker.** { *; }
