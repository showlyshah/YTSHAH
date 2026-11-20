package com.viewtube.app.player

import android.content.Intent
import android.os.Bundle
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

/** Keeps audio playing in the background and shows the lock-screen / notification controls. */
@UnstableApi
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    private val stopCommand = SessionCommand(ACTION_STOP, Bundle.EMPTY)

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
        session = MediaSession.Builder(this, PlayerHolder.player)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                        .buildUpon().add(stopCommand).build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands)
                        .setCustomLayout(ImmutableList.of(stopButton))
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle,
                ): ListenableFuture<SessionResult> {
                    if (customCommand.customAction == ACTION_STOP) {
                        PlayerHolder.stop()
                        stopSelf()
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .setCustomLayout(ImmutableList.of(stopButton))
            .build()
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
        session?.release()
        session = null
        super.onDestroy()
    }

    private companion object {
        const val ACTION_STOP = "viewtube.STOP"
    }
}
