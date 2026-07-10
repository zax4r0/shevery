package moe.shizuku.manager.service

import android.content.Intent
import android.service.quicksettings.TileService
import moe.shizuku.manager.MainActivity

class LauncherTileService : TileService() {
    override fun onClick() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            startActivityAndCollapse(intent)
        } catch (e: Exception) {
            // Handle exceptions
        }
    }
}
