# Shevery v13.8.0-r20 Git Blame & Authorship

This document displays a line-by-line git blame and context for key parts of code introduced in the v13.8.0-r20 update.

---

## 1. Notification Control Setup (`ShizukuApplication.kt`)

```text
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 43)         moe.shizuku.manager.service.SheveryNotificationManager.setup(this)
```

### Context
This line was added to initialize the sticky notification as soon as the main application launches. This ensures the start/stop control notification is always visible.

---

## 2. Notification Manager Logic (`SheveryNotificationManager.kt`)

Selected sections of `SheveryNotificationManager.kt` created in this release:

```kotlin
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 15) object SheveryNotificationManager {
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 16)     private const val CHANNEL_ID = "shevery_control"
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 17)     private const val NOTIFICATION_ID = 1002
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 18)     private var initialized = false
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 19) 
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 20)     private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 21)         updateNotification(moe.shizuku.manager.application)
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 22)     }
...
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 28)     fun setup(context: Context) {
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 29)         if (initialized) return
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 30)         initialized = true
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 31) 
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 32)         createChannel(context)
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 33)         Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 34)         Shizuku.addBinderDeadListener(binderDeadListener)
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 35)         updateNotification(context)
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 36)     }
...
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 49)     fun updateNotification(context: Context) {
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 50)         val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 51)         val isRunning = Shizuku.pingBinder()
...
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 80)         if (isRunning) {
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 81)             val stopIntent = Intent(context, SheveryControlReceiver::class.java).apply {
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 82)                 action = SheveryControlReceiver.ACTION_STOP_SERVER
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 83)             }
...
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 93)         } else {
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 94)             val startIntent = Intent(context, SheveryControlReceiver::class.java).apply {
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 95)                 action = SheveryControlReceiver.ACTION_START_SERVER
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 96)             }
```

### Context
Designed to dynamically generate start/stop notification actions based on `Shizuku.pingBinder()` checks. It hooks listener binders so notification contents automatically update state.

---

## 3. Control Action Receiver (`SheveryControlReceiver.kt`)

```kotlin
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500  8) class SheveryControlReceiver : BroadcastReceiver() {
...
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 14)     override fun onReceive(context: Context, intent: Intent) {
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 15)         when (intent.action) {
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 16)             ACTION_START_SERVER -> {
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 17)                 WatchdogManager.attemptRestart(context.applicationContext)
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 18)             }
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 19)             ACTION_STOP_SERVER -> {
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 20)                 WatchdogManager.stopServer()
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 21)             }
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 22)         }
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 23)     }
```

### Context
Maps notification action clicks directly to `WatchdogManager` actions to stop/start the server instance locally.

---

## 4. IPermissionManager Fallback Fix (`Android17Compat.java`)

```java
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 151)                     // Pass packageName first, permissionName second to match IPermissionManager signature (pkgName, permName, deviceId, userId)
ea2a281c (GitHub User 2026-06-11 13:07:00 +0500 152)                     return (int) invokeMethod(pm, sCheckPermissionMethod, packageName, permissionName, userId);
```

### Context
Corrects the previous bug where arguments for reflection-based permission checks were reversed. The native `IPermissionManager.checkPermission` takes `(packageName, permissionName, ...)` not `(permissionName, packageName, ...)`.
