package com.viewtube.app.player

import android.app.NotificationChannel
import android.app.PendingIntent
import android.content.ComponentName
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.viewtube.app.R
import com.viewtube.app.data.RuntimeDiagnostics

/** Keeps audio playing in the background and shows the lock-screen / notification controls. */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    private val stopCommand = SessionCommand(ACTION_STOP, Bundle.EMPTY)
    private val previousCommand = SessionCommand(ACTION_PREVIOUS, Bundle.EMPTY)
    private val nextCommand = SessionCommand(ACTION_NEXT, Bundle.EMPTY)

    private fun activityPendingIntent(): PendingIntent {
        val launch = Intent().setComponent(ComponentName(this, com.viewtube.app.MainActivity::class.java))
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this, 1002, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @Suppress("DEPRECATION")
    private val previousButton: CommandButton by lazy {
        CommandButton.Builder().setDisplayName("Previous")
            .setIconResId(android.R.drawable.ic_media_previous)
            .setSessionCommand(previousCommand).build()
    }
    @Suppress("DEPRECATION")
    private val nextButton: CommandButton by lazy {
        CommandButton.Builder().setDisplayName("Next")
            .setIconResId(android.R.drawable.ic_media_next)
            .setSessionCommand(nextCommand).build()
    }
    @Suppress("DEPRECATION")
    private fun playPauseButton(): CommandButton =
        CommandButton.Builder().setDisplayName(if (PlayerHolder.player.playWhenReady) "Pause" else "Play")
            .setIconResId(if (PlayerHolder.player.playWhenReady) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            .setPlayerCommand(Player.COMMAND_PLAY_PAUSE).build()

    private fun updateNotificationLayout() {
        session?.let { mediaSession ->
            mediaSession.setCustomLayout(ImmutableList.of(previousButton, playPauseButton(), nextButton, stopButton))
        }
    }

    private val notificationPlayerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = updateNotificationLayout()
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = updateNotificationLayout()
    }

    @Suppress("DEPRECATION")
    private val stopButton: CommandButton by lazy {
        CommandButton.Builder()
            .setDisplayName("Stop")
            .setIconResId(R.drawable.ic_stop)
            .setSessionCommand(stopCommand)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        RuntimeDiagnostics.record("PLAYBACK_SERVICE", "onCreate", "started")
        // Promote as early as possible. The same id is reused by Media3's media
        // notification so this placeholder is replaced rather than duplicated.
        promoteToForeground()
        session = MediaSession.Builder(this, PlayerHolder.player)
            .setSessionActivity(activityPendingIntent())
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                        .buildUpon().add(stopCommand).add(previousCommand).add(nextCommand).build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands)
                        .setAvailablePlayerCommands(
                            MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                                .build()
                        )
                        .setCustomLayout(ImmutableList.of(previousButton, playPauseButton(), nextButton, stopButton))
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        ACTION_PREVIOUS -> PlayerHolder.playPrevious()
                        ACTION_NEXT -> PlayerHolder.playNext { }
                        ACTION_STOP -> {
                            PlayerHolder.stop()
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .setCustomLayout(ImmutableList.of(previousButton, playPauseButton(), nextButton, stopButton))
            .build()
        PlayerHolder.player.addListener(notificationPlayerListener)
    }

    /**
     * Android starts its foreground-service deadline when startForegroundService() is
     * requested. Promote from both lifecycle entry points: onCreate covers initial
     * creation, while onStartCommand covers each explicit start/restart and guards
     * against MediaSessionService startup ordering on newer Android releases.
     */
    private fun promoteToForeground() {
        ensurePlaybackNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Starting playback")
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(activityPendingIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            else 0
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        RuntimeDiagnostics.record("PLAYBACK_SERVICE", "onStartCommand", "received", "startId=$startId, intentAction=${intent?.action ?: "none"}")
        promoteToForeground()
        return super.onStartCommand(intent, flags, startId)
    }

    private fun ensurePlaybackNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Playback",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply { description = "Playback status and controls" }
                )
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away ends playback unless audio is still playing in the background.
        if (!PlayerHolder.player.playWhenReady) {
            PlayerHolder.stop()
            stopSelf()
        }
    }

    override fun onDestroy() {
        RuntimeDiagnostics.record("PLAYBACK_SERVICE", "onDestroy", "started")
        stopForeground(STOP_FOREGROUND_REMOVE)
        PlayerHolder.player.removeListener(notificationPlayerListener)
        session?.release()
        session = null
        super.onDestroy()
    }

    private companion object {
        const val ACTION_STOP = "viewtube.STOP"
        const val ACTION_PREVIOUS = "viewtube.PREVIOUS"
        const val ACTION_NEXT = "viewtube.NEXT"
        const val CHANNEL_ID = "viewtube_playback"
        // Media3's default media notification ID; reuse it so its media controls replace
        // this startup notification instead of leaving a second persistent notification.
        const val NOTIFICATION_ID = 1001
    }
}
