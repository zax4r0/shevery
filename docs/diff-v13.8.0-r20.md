# Shevery v13.8.0-r20 Code Diff

This page documents the specific code changes introduced in Shevery v13.8.0-r20 compared to the previous version.

---

## 1. Android Manifest updates (`manager/src/main/AndroidManifest.xml`)

* Uncommented `LEANBACK_LAUNCHER` activity category to enable Google TV integration natively without modifying the original phone UI layout.
* Registered the local `SheveryControlReceiver` to process intents from notification action buttons.

```diff
@@ -84,7 +84,7 @@
 
                 <!-- 2023.08.03: Google play enforces that apps with LEANBACK_LAUNCHER Activity must be uploaded with App Bundle format, -->
                 <!-- and possibly additional review steps. To avoid problems, remove it temporarily. -->
-                <!--<category android:name="android.intent.category.LEANBACK_LAUNCHER" />-->
+                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
             </intent-filter>
         </activity>
         <activity
@@ -176,6 +176,16 @@
             </intent-filter>
         </receiver>
 
+        <receiver
+            android:name=".receiver.SheveryControlReceiver"
+            android:enabled="true"
+            android:exported="false">
+            <intent-filter>
+                <action android:name="moe.shizuku.manager.action.START_SERVER" />
+                <action android:name="moe.shizuku.manager.action.STOP_SERVER" />
+            </intent-filter>
+        </receiver>
+
         <receiver
             android:name=".receiver.ShizukuReceiver"
             android:directBootAware="true"
```

---

## 2. Application Setup (`manager/src/main/java/moe/shizuku/manager/ShizukuApplication.kt`)

* Hooked up `SheveryNotificationManager` to start showing the control notification when the app initializes.

```diff
@@ -40,6 +40,7 @@ class ShizukuApplication : Application() {
         application = this
         init(this)
         moe.shizuku.manager.service.WatchdogManager.init(this)
+        moe.shizuku.manager.service.SheveryNotificationManager.setup(this)
     }
 
 }
```

---

## 3. Persistent Notification Control (`manager/src/main/java/moe/shizuku/manager/service/SheveryNotificationManager.kt`)

* Created a new service component that displays a sticky/persistent notification.
* Includes "Start" or "Stop" action buttons that trigger `SheveryControlReceiver` based on Shizuku's current running state.

```kotlin
package moe.shizuku.manager.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import moe.shizuku.manager.MainActivity
import moe.shizuku.manager.R
import moe.shizuku.manager.receiver.SheveryControlReceiver
import rikka.shizuku.Shizuku

object SheveryNotificationManager {
    private const val CHANNEL_ID = "shevery_control"
    private const val NOTIFICATION_ID = 1002
    private var initialized = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        updateNotification(moe.shizuku.manager.application)
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        updateNotification(moe.shizuku.manager.application)
    }

    fun setup(context: Context) {
        if (initialized) return
        initialized = true

        createChannel(context)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        updateNotification(context)
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val name = context.getString(R.string.notification_control_channel_name)
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun updateNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val isRunning = Shizuku.pingBinder()

        val title = if (isRunning) {
            context.getString(R.string.notification_control_title_running)
        } else {
            context.getString(R.string.notification_control_title_stopped)
        }

        val text = if (isRunning) {
            context.getString(R.string.notification_control_text_running)
        } else {
            context.getString(R.string.notification_control_text_stopped)
        }

        val mainIntent = Intent(context, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            context, 0, mainIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(if (isRunning) R.drawable.ic_server_ok_24dp else R.drawable.ic_server_error_24dp)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .setShowWhen(false)

        if (isRunning) {
            val stopIntent = Intent(context, SheveryControlReceiver::class.java).apply {
                action = SheveryControlReceiver.ACTION_STOP_SERVER
            }
            val stopPendingIntent = PendingIntent.getBroadcast(
                context, 1, stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(
                R.drawable.ic_outline_info_24,
                context.getString(R.string.notification_control_action_stop),
                stopPendingIntent
            )
        } else {
            val startIntent = Intent(context, SheveryControlReceiver::class.java).apply {
                action = SheveryControlReceiver.ACTION_START_SERVER
            }
            val startPendingIntent = PendingIntent.getBroadcast(
                context, 2, startIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(
                R.drawable.ic_server_restart,
                context.getString(R.string.notification_control_action_start),
                startPendingIntent
            )
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }
}
```

---

## 4. Notification Action Broadcast Receiver (`manager/src/main/java/moe/shizuku/manager/receiver/SheveryControlReceiver.kt`)

* Handles incoming broadcast actions to attempt starting the Shizuku server using `WatchdogManager` or stopping it.

```kotlin
package moe.shizuku.manager.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import moe.shizuku.manager.service.WatchdogManager

class SheveryControlReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_START_SERVER = "moe.shizuku.manager.action.START_SERVER"
        const val ACTION_STOP_SERVER = "moe.shizuku.manager.action.STOP_SERVER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_START_SERVER -> {
                WatchdogManager.attemptRestart(context.applicationContext)
            }
            ACTION_STOP_SERVER -> {
                WatchdogManager.stopServer()
            }
        }
    }
}
```

---

## 5. IPermissionManager Reflection Correction (`server/src/main/java/rikka/shizuku/server/util/Android17Compat.java`)

* Corrected the parameter order of `checkPermission` signature lookup to pass `packageName` first and `permissionName` second.

```diff
@@ -148,8 +148,8 @@ public class Android17Compat {
                     }
                 }
                 if (sCheckPermissionMethod != null) {
-                    // Pass permissionName first, packageName second to match IPermissionManager signature (permName, pkgName, userId)
-                    return (int) invokeMethod(pm, sCheckPermissionMethod, permissionName, packageName, userId);
+                    // Pass packageName first, permissionName second to match IPermissionManager signature (pkgName, permName, deviceId, userId)
+                    return (int) invokeMethod(pm, sCheckPermissionMethod, packageName, permissionName, userId);
                 }
             } catch (Throwable ex) {
                 Log.e(TAG, "Android 17 fallback for checkPermission(String, String, int) failed", ex);
```
