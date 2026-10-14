# Shizuku: ShizukuShell calls the private Shizuku.newProcess through reflection,
# so the API classes must keep their member names.
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# Settings are stored as JSON; keep the serializable model intact.
-keep class com.overdrive.quest.core.model.** { *; }
-keepclassmembers class com.overdrive.quest.core.session.SessionSummary { *; }
