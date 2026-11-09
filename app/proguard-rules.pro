# R8 full mode keep rules. Everything else is covered by the consumer rules shipped with
# Hilt, Room, Compose, Glance, kotlinx.serialization and androidx.

# Backups embed kotlinx.serialization's default polymorphic discriminator, which is the
# fully-qualified class name. Renaming these classes would make backups written by one
# build unreadable by the next.
-keep class dev.tomng.alarme.core.model.** { *; }

# Deep links, alarm actions and widget clicks resolve classes from the manifest; R8 keeps
# those automatically, but the receivers also expose static actions used by system
# broadcasts (BOOT_COMPLETED, TIME_SET, MY_PACKAGE_REPLACED).
-keep class dev.tomng.alarme.core.alarmengine.receiver.** { *; }

# Release stack traces stay readable without a mapping file on the device.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
