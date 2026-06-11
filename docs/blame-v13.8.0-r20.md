# Shevery v13.8.0-r20 Git Blame & Authorship

This document displays a line-by-line git blame and context for key parts of code introduced in the v13.8.0-r20 update.

---

## 1. Notification Control Setup (`ShizukuApplication.kt`)

```text
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 43)         moe.shizuku.manager.service.SheveryNotificationManager.setup(this)
```

### Context
This line was added to initialize the sticky notification as soon as the main application launches. This ensures the start/stop control notification is always visible.

---

## 2. Notification Manager Logic (`SheveryNotificationManager.kt`)

Selected sections of `SheveryNotificationManager.kt` created by commit `93b5ed7c`:

```kotlin
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 15) object SheveryNotificationManager {
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 16)     private const val CHANNEL_ID = "shevery_control"
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 17)     private const val NOTIFICATION_ID = 1002
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 18)     private var initialized = false
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 19) 
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 20)     private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 21)         updateNotification(moe.shizuku.manager.application)
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 22)     }
...
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 28)     fun setup(context: Context) {
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 29)         if (initialized) return
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 30)         initialized = true
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 31) 
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 32)         createChannel(context)
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 33)         Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 34)         Shizuku.addBinderDeadListener(binderDeadListener)
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 35)         updateNotification(context)
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 36)     }
...
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 49)     fun updateNotification(context: Context) {
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 50)         val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 51)         val isRunning = Shizuku.pingBinder()
...
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 80)         if (isRunning) {
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 81)             val stopIntent = Intent(context, SheveryControlReceiver::class.java).apply {
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 82)                 action = SheveryControlReceiver.ACTION_STOP_SERVER
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 83)             }
...
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 93)         } else {
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 94)             val startIntent = Intent(context, SheveryControlReceiver::class.java).apply {
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 95)                 action = SheveryControlReceiver.ACTION_START_SERVER
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 96)             }
```

### Context
Designed to dynamically generate start/stop notification actions based on `Shizuku.pingBinder()` checks. It hooks listener binders so notification contents automatically update state.

---

## 3. Control Action Receiver (`SheveryControlReceiver.kt`)

```kotlin
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500  8) class SheveryControlReceiver : BroadcastReceiver() {
...
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 14)     override fun onReceive(context: Context, intent: Intent) {
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 15)         when (intent.action) {
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 16)             ACTION_START_SERVER -> {
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 17)                 WatchdogManager.attemptRestart(context.applicationContext)
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 18)             }
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 19)             ACTION_STOP_SERVER -> {
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 20)                 WatchdogManager.stopServer()
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 21)             }
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 22)         }
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 23)     }
```

### Context
Maps notification action clicks directly to `WatchdogManager` actions to stop/start the server instance locally.

---

## 4. IPermissionManager Fallback Fix (`Android17Compat.java`)

```java
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 151)                     // Pass packageName first, permissionName second to match IPermissionManager signature (pkgName, permName, deviceId, userId)
93b5ed7c (GitHub User 2026-06-11 12:43:12 +0500 152)                     return (int) invokeMethod(pm, sCheckPermissionMethod, packageName, permissionName, userId);
```

### Context
Corrects the previous bug where arguments for reflection-based permission checks were reversed. The native `IPermissionManager.checkPermission` takes `(packageName, permissionName, ...)` not `(permissionName, packageName, ...)`.
